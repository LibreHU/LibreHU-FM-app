package org.librehu.fm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.librehu.fm.headunit.HeadUnitBridge
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Owns the FM chip: power, tuning, seek, scan, RDS, audio, audio focus and the media session (used by the
 * launcher, the steering wheel keys and the notification). All chip calls run on one thread.
 */
class FmService : Service() {
    private val chip = Executors.newSingleThreadExecutor { r -> Thread(r, "fm-chip") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio = FmAudio()
    private lateinit var store: RadioStore
    private lateinit var logos: StationLogos
    private lateinit var bridge: HeadUnitBridge
    private lateinit var session: MediaSession
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var deviceOpen = false

    @Volatile
    private var rdsThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        store = RadioStore(this)
        logos = StationLogos(this)
        bridge = HeadUnitBridge.create(this)
        audioManager = getSystemService(AudioManager::class.java)
        _state.update {
            it.copy(
                available = FmNative.loadError == null,
                error = FmNative.loadError?.message.orEmpty(),
                frequency = store.frequency,
                presets = store.presets,
                stations = store.stations,
            )
        }
        session =
            MediaSession(this, "LibreHU-FM").apply {
                setCallback(sessionCallback)
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@FmService,
                        0,
                        Intent(this@FmService, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        // Station logo: cached one at once, network lookup once the RDS name is stable (some stations scroll it).
        scope.launch {
            state
                .map { it.frequency to it.title.trim() }
                .distinctUntilChanged()
                .collectLatest { (freq, name) ->
                    val cached = withContext(Dispatchers.IO) { logos.cached(freq) }
                    _state.update { if (it.frequency == freq) it.copy(logo = cached) else it }
                    if (name.isEmpty()) return@collectLatest
                    delay(LOGO_NAME_STABLE_MS)
                    val found = withContext(Dispatchers.IO) { logos.find(freq, name) }
                    if (found != null) _state.update { if (it.frequency == freq) it.copy(logo = found) else it }
                }
        }
        scope.launch {
            state.collect { s ->
                updateSession(s)
                RadioWidget.updateAll(this@FmService, s)
                if (s.poweredOn) notify(s)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Started with startForegroundService(): always go foreground first.
        startForegroundCompat(state.value)
        when (intent?.action) {
            ACTION_PLAY -> {
                powerOn()
            }

            ACTION_PAUSE -> {
                powerOff()
            }

            ACTION_TOGGLE -> {
                if (state.value.poweredOn) powerOff() else powerOn()
            }

            ACTION_NEXT -> {
                next(true)
            }

            ACTION_PREVIOUS -> {
                next(false)
            }

            ACTION_SEEK_UP -> {
                seek(true)
            }

            ACTION_SEEK_DOWN -> {
                seek(false)
            }

            ACTION_STEP_UP -> {
                tune(state.value.frequency + Band.STEP)
            }

            ACTION_STEP_DOWN -> {
                tune(state.value.frequency - Band.STEP)
            }

            ACTION_TUNE -> {
                tune(intent.getIntExtra(EXTRA_FREQUENCY, state.value.frequency))
            }

            ACTION_SCAN -> {
                scan()
            }

            ACTION_STOP_SCAN -> {
                FmNative.stopScan()
            }

            ACTION_TOGGLE_PRESET -> {
                togglePreset()
            }

            else -> {}
        }
        chip.execute { if (!state.value.poweredOn) scope.launch { stopForeground(STOP_FOREGROUND_REMOVE) } }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        powerOff()
        chip.shutdown()
        session.release()
        bridge.release()
        scope.cancel()
        super.onDestroy()
    }

    // --- Chip ----------------------------------------------------------------------------------------------------

    private fun powerOn() =
        chip.execute {
            if (state.value.poweredOn || FmNative.loadError != null) return@execute
            if (!bridge.managesAudioFocus && !requestFocus()) {
                fail(getString(R.string.error_focus))
                return@execute
            }
            setBusy(true)
            if (!deviceOpen) deviceOpen = FmNative.openDev()
            val freq = state.value.frequency
            if (!deviceOpen || !FmNative.powerUp(Band.mhz(freq))) {
                abandonFocus()
                fail(getString(if (deviceOpen) R.string.error_power_up else R.string.error_open))
                return@execute
            }
            bridge.onRadioOn()
            FmNative.setRds(true)
            FmNative.setMute(false)
            audio.start()
            _state.update { it.copy(poweredOn = true, busy = false, error = "") }
            startRds()
            // The render thread reports a missing capture permission asynchronously.
            Thread.sleep(500)
            if (audio.lastError.isNotEmpty()) _state.update { it.copy(error = audio.lastError) }
        }

    private fun powerOff() =
        chip.execute {
            if (!state.value.poweredOn) return@execute
            stopRds()
            audio.stop()
            audioManager.setParameters("AudioFmPreStop=1")
            FmNative.setRds(false)
            FmNative.powerDown(0)
            if (deviceOpen) {
                FmNative.closeDev()
                deviceOpen = false
            }
            bridge.onRadioOff()
            abandonFocus()
            _state.update { it.copy(poweredOn = false, busy = false) }
            scope.launch { stopForeground(STOP_FOREGROUND_REMOVE) }
        }

    private fun tune(frequency: Int) =
        chip.execute {
            val f = Band.clamp(frequency)
            if (state.value.poweredOn && !FmNative.tune(Band.mhz(f))) {
                Log.w(TAG, "tune $f failed")
            }
            setFrequency(f)
        }

    private fun seek(up: Boolean) =
        chip.execute {
            if (!state.value.poweredOn) return@execute
            setBusy(true)
            val found = FmNative.seek(Band.mhz(state.value.frequency), up)
            val f = (found * 10).roundToInt()
            if (f in Band.MIN..Band.MAX) {
                FmNative.tune(Band.mhz(f))
                setFrequency(f)
            }
            setBusy(false)
        }

    private fun scan() =
        chip.execute {
            if (!state.value.poweredOn) return@execute
            _state.update { it.copy(scanning = true) }
            FmNative.setRds(false)
            val found = FmNative.autoScan()
            FmNative.setRds(true)
            val stations =
                found
                    ?.map { it.toInt() }
                    ?.filter { it in Band.MIN..Band.MAX }
                    ?.distinct()
                    ?.map { Station(it) }
                    .orEmpty()
            if (stations.isNotEmpty()) store.stations = stations
            val target = stations.firstOrNull()?.frequency ?: state.value.frequency
            FmNative.tune(Band.mhz(target))
            _state.update { it.copy(scanning = false, stations = stations.ifEmpty { it.stations }) }
            setFrequency(target)
        }

    /** Next / previous preset, or seek when there is none. */
    private fun next(forward: Boolean) {
        val s = state.value
        if (!s.poweredOn) {
            powerOn()
            return
        }
        val list =
            s.presets
                .ifEmpty { s.stations }
                .map { it.frequency }
                .sorted()
        if (list.isEmpty()) {
            seek(forward)
            return
        }
        val target =
            if (forward) {
                list.firstOrNull { it > s.frequency } ?: list.first()
            } else {
                list.lastOrNull { it < s.frequency } ?: list.last()
            }
        tune(target)
    }

    private fun togglePreset() {
        val s = state.value
        val presets =
            if (s.isPreset) {
                s.presets.filterNot { it.frequency == s.frequency }
            } else {
                (s.presets + Station(s.frequency, s.ps.trim())).sortedBy { it.frequency }
            }
        store.presets = presets
        _state.update { it.copy(presets = presets) }
    }

    private fun setFrequency(f: Int) {
        store.frequency = f
        _state.update { it.copy(frequency = f, ps = "", radioText = "") }
    }

    private fun setBusy(busy: Boolean) = _state.update { it.copy(busy = busy) }

    private fun fail(message: String) {
        Log.e(TAG, message)
        _state.update { it.copy(busy = false, error = message) }
    }

    // --- RDS -----------------------------------------------------------------------------------------------------

    private fun startRds() {
        if (FmNative.isRdsSupport() != 1) return
        rdsThread =
            Thread({
                while (rdsThread === Thread.currentThread()) {
                    val events = FmNative.readRds().toInt()
                    if (events and FmNative.RDS_EVENT_PROGRAMNAME != 0) {
                        val ps = FmNative.getPs()?.let(::rdsText).orEmpty()
                        _state.update { it.copy(ps = ps) }
                    }
                    if (events and FmNative.RDS_EVENT_LAST_RADIOTEXT != 0) {
                        val rt = FmNative.getLrText()?.let(::rdsText).orEmpty()
                        _state.update { it.copy(radioText = rt) }
                    }
                    try {
                        Thread.sleep(RDS_POLL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }, "fm-rds").apply { start() }
    }

    private fun stopRds() {
        val t = rdsThread
        rdsThread = null
        t?.interrupt()
        t?.join(500)
    }

    private fun rdsText(bytes: ByteArray): String =
        String(bytes, Charsets.ISO_8859_1).replace('\u0000', ' ').trim().replace(Regex("\\s+"), " ")

    // --- Audio focus ---------------------------------------------------------------------------------------------

    private val focusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.i(TAG, "audio focus lost: radio stopped")
                    powerOff()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    audio.volume = 0f
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    audio.volume = DUCK_VOLUME
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    audio.volume = 1f
                }
            }
        }

    private fun requestFocus(): Boolean {
        val req =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setOnAudioFocusChangeListener(focusListener)
                .build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // --- Media session, notification -----------------------------------------------------------------------------

    private val sessionCallback =
        object : MediaSession.Callback() {
            override fun onPlay() = powerOn()

            override fun onPause() = powerOff()

            override fun onStop() = powerOff()

            override fun onSkipToNext() = next(true)

            override fun onSkipToPrevious() = next(false)
        }

    private fun updateSession(s: RadioState) {
        session.setMetadata(
            MediaMetadata
                .Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title.ifBlank { "FM ${Band.format(s.frequency)}" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.radioText.ifBlank { "FM ${Band.format(s.frequency)} MHz" })
                .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, s.logo)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, s.logo)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState
                .Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                ).setState(
                    if (s.poweredOn) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                ).build(),
        )
        session.isActive = s.poweredOn
    }

    private fun buildNotification(s: RadioState): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )

        fun action(
            icon: Int,
            title: Int,
            act: String,
        ) = Notification.Action
            .Builder(
                android.graphics.drawable.Icon
                    .createWithResource(this, icon),
                getString(title),
                servicePending(this, act),
            ).build()
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle(s.title.ifBlank { "FM ${Band.format(s.frequency)}" })
            .setContentText(s.radioText.ifBlank { "${Band.format(s.frequency)} MHz" })
            .setLargeIcon(s.logo)
            .setContentIntent(session.controller.sessionActivity)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_skip_previous, R.string.previous, ACTION_PREVIOUS))
            .addAction(
                if (s.poweredOn) {
                    action(R.drawable.ic_pause, R.string.pause, ACTION_PAUSE)
                } else {
                    action(R.drawable.ic_play, R.string.play, ACTION_PLAY)
                },
            ).addAction(action(R.drawable.ic_skip_next, R.string.next, ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun startForegroundCompat(s: RadioState) {
        val n = buildNotification(s)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notify(s: RadioState) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(s))

    companion object {
        private const val TAG = "LibreHU-FM"
        private const val CHANNEL_ID = "radio"
        private const val NOTIFICATION_ID = 1
        private const val RDS_POLL_MS = 200L
        private const val DUCK_VOLUME = 0.3f
        private const val LOGO_NAME_STABLE_MS = 3000L

        const val ACTION_PLAY = "org.librehu.fm.PLAY"
        const val ACTION_PAUSE = "org.librehu.fm.PAUSE"
        const val ACTION_TOGGLE = "org.librehu.fm.TOGGLE"
        const val ACTION_NEXT = "org.librehu.fm.NEXT"
        const val ACTION_PREVIOUS = "org.librehu.fm.PREVIOUS"
        const val ACTION_SEEK_UP = "org.librehu.fm.SEEK_UP"
        const val ACTION_SEEK_DOWN = "org.librehu.fm.SEEK_DOWN"
        const val ACTION_STEP_UP = "org.librehu.fm.STEP_UP"
        const val ACTION_STEP_DOWN = "org.librehu.fm.STEP_DOWN"
        const val ACTION_TUNE = "org.librehu.fm.TUNE"
        const val ACTION_SCAN = "org.librehu.fm.SCAN"
        const val ACTION_STOP_SCAN = "org.librehu.fm.STOP_SCAN"
        const val ACTION_TOGGLE_PRESET = "org.librehu.fm.TOGGLE_PRESET"
        const val EXTRA_FREQUENCY = "frequency"

        private val _state = MutableStateFlow(RadioState())

        /** Radio state, shared with the activity and the widget (same process). */
        val state: StateFlow<RadioState> = _state.asStateFlow()

        fun send(
            context: Context,
            action: String,
            frequency: Int? = null,
        ) {
            val i = Intent(context, FmService::class.java).setAction(action)
            if (frequency != null) i.putExtra(EXTRA_FREQUENCY, frequency)
            context.startForegroundService(i)
        }

        fun servicePending(
            context: Context,
            action: String,
        ): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                action.hashCode(),
                Intent(context, FmService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
