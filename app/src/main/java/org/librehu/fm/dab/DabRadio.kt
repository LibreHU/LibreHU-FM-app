package org.librehu.fm.dab

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Process
import android.util.Log
import io.github.jvspier.dabplay.audio.AudioPlayer
import io.github.jvspier.dabplay.data.Snapshot
import io.github.jvspier.dabplay.data.StationStore
import io.github.jvspier.dabplay.engine.DabEngine
import io.github.jvspier.dabplay.engine.Dongle
import io.github.jvspier.dabplay.engine.DongleKind
import io.github.jvspier.dabplay.engine.DriverRegistry
import io.github.jvspier.dabplay.engine.EndpointInfo
import io.github.jvspier.dabplay.engine.EngineLog
import io.github.jvspier.dabplay.engine.Focus
import io.github.jvspier.dabplay.engine.FocusChange
import io.github.jvspier.dabplay.engine.OpenDongle
import io.github.jvspier.dabplay.engine.UnsupportedDongle
import io.github.jvspier.dabplay.tuner.Transport
import io.github.jvspier.dabplay.usb.AndroidUsbTransport
import java.io.File

/** A DAB service as the screens show it; [key] = ECC.SId ("E3.83C7"), stable from one ensemble to another. */
data class DabStation(
    val key: String,
    val name: String,
    val ensemble: String,
    val channel: String,
    val bitrate: Int,
    /** DAB+ (HE-AAC); false: DAB (MPEG Layer II). */
    val dabPlus: Boolean,
)

/** What the DAB part is doing, for the screens (the engine's own texts are English, for its log). */
enum class DabStatus { NO_DONGLE, UNSUPPORTED, NOT_RESPONDING, READY, SCANNING, TUNING, PLAYING, WEAK, NO_SIGNAL, UNPLAYABLE }

/** Progress of a scan of the band: channel being looked at ("5A"), its rank, and the services found so far. */
data class DabScan(
    val channel: String,
    val index: Int,
    val total: Int,
    val services: Int,
)

/**
 * DAB+ USB dongles through the DABplay+ engine (dab/, MIT): one engine and one station list for the whole process,
 * like the original app, so that a dongle dropping out while the car cranks resumes the station whatever the screens
 * do. The service turns the engine's state into the radio state ([org.librehu.fm.RadioState]).
 */
class DabRadio private constructor(
    private val context: Context,
) {
    private val usb: UsbManager = context.getSystemService(UsbManager::class.java)

    val store = StationStore(File(context.filesDir, "dab_stations.json"), onError = { Log.e(TAG, "saving the DAB stations failed", it) })

    /** The user wants DAB to play: the last station plays again as soon as a dongle is ready (car start, re-plug). */
    @Volatile
    var wanted = false

    /** Traffic announcements of the stations (the radio's TA setting). */
    @Volatile
    var announcements = false

    private val log =
        object : EngineLog {
            override fun i(
                tag: String,
                msg: String,
            ) {
                Log.i(TAG, "$tag: $msg")
            }

            override fun w(
                tag: String,
                msg: String,
                t: Throwable?,
            ) {
                Log.w(TAG, "$tag: $msg", t)
            }

            override fun e(
                tag: String,
                msg: String,
                t: Throwable?,
            ) {
                Log.e(TAG, "$tag: $msg", t)
            }

            override fun logcatOnly(
                tag: String,
                msg: String,
            ) {
                Log.d(TAG, "$tag: $msg")
            }
        }

    val engine =
        DabEngine(
            store,
            MediaFocus(context.getSystemService(AudioManager::class.java)),
            newAudio = { AudioPlayer { Log.i(TAG, it) } },
            events = log,
            resumeOnStart = { wanted },
            followStation = { true },
            trafficAnnouncements = { announcements },
            usbThreadSetup = { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) },
        )

    init {
        // In the process, not in a screen or the service: an unplug must stop the engine whatever runs.
        val filter =
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_PERMISSION)
                addAction(ACTION_READY)
            }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    @Suppress("DEPRECATION")
                    val dev: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    when (intent.action) {
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> engine.detached(dev?.deviceName)
                        ACTION_PERMISSION -> if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connect(ask = false)
                        else -> connect()
                    }
                }
            }
        if (Build.VERSION.SDK_INT >= 33) {
            // System broadcasts (USB) reach a not-exported receiver; ours come from this app.
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /** The supported dongles plugged in now. */
    private fun dongles(): List<UsbDevice> = usb.deviceList.values.filter { kindOf(it) != null }

    val plugged: Boolean get() = dongles().isNotEmpty()

    /**
     * Connects the dongle plugged in. Android asks the driver once for the USB access ([ask]) unless the dongle opens
     * LibreHU FM by itself (the "use by default" tick of its prompt).
     */
    fun connect(ask: Boolean = true) {
        val dev = dongles().firstOrNull() ?: return
        if (usb.hasPermission(dev)) {
            engine.attach(AndroidDongle(usb, dev))
        } else if (ask) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            usb.requestPermission(dev, PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags))
        }
    }

    /** The stations found by the scans, in the store's order (by ensemble, then name), for the screens. */
    fun stations(s: Snapshot): List<DabStation> = s.stations.map { st -> station(st) }

    companion object {
        private const val TAG = "LibreHU-DAB"
        const val ACTION_PERMISSION = "org.librehu.fm.DAB_USB_PERMISSION"

        /** Sent by [DabUsbActivity] once Android has given the access to a dongle just plugged in. */
        const val ACTION_READY = "org.librehu.fm.DAB_USB_READY"

        @Volatile
        private var instance: DabRadio? = null

        fun get(context: Context): DabRadio =
            instance ?: synchronized(this) { instance ?: DabRadio(context.applicationContext).also { instance = it } }

        fun kindOf(dev: UsbDevice): DongleKind? = DriverRegistry.kindOf(dev.vendorId, dev.productId)

        fun station(st: io.github.jvspier.dabplay.data.Station): DabStation =
            DabStation(st.key.toString(), st.label.trim(), st.ensemble.label.trim(), st.ensemble.channel, st.service.bitrateKbps, st.service.ascty == 63)
    }
}

/**
 * A USB dongle for the engine: opening = USB access and claiming interface 0's bulk endpoints. From DABplay+'s
 * AndroidDongle (MIT): 16c0:05dc is a shared V-USB id, so what is behind it decides, not the maker's name.
 */
private class AndroidDongle(
    private val usb: UsbManager,
    private val dev: UsbDevice,
) : Dongle {
    override val name: String = dev.deviceName
    override val kind: DongleKind? = DabRadio.kindOf(dev)
    override val details: String
        get() = "%04x:%04x '%s' / '%s'".format(dev.vendorId, dev.productId, dev.manufacturerName, dev.productName)

    override fun open(): OpenDongle {
        if (dev.interfaceCount == 0) throw UnsupportedDongle("$details: no USB interface")
        val endpoints = dev.getInterface(0).let { i -> (0 until i.endpointCount).map { i.getEndpoint(it) } }
        kind?.let { k ->
            val seen = endpoints.map { EndpointInfo(it.address, it.type == UsbConstants.USB_ENDPOINT_XFER_BULK) }
            if (!DriverRegistry.fits(k, seen)) throw UnsupportedDongle("$details: interface 0 is not a $k dongle's")
        }
        val conn = usb.openDevice(dev) ?: error("cannot open the USB device")
        try {
            val intf = dev.getInterface(0)
            check(conn.claimInterface(intf, true)) { "cannot claim interface 0" }
            val eps =
                (0 until intf.endpointCount)
                    .map { intf.getEndpoint(it) }
                    .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                    .associateBy { it.address }
            val transport = AndroidUsbTransport(conn, eps)
            return object : OpenDongle {
                override val transport: Transport = transport

                override fun close() = conn.close()
            }
        } catch (e: Throwable) {
            conn.close()
            throw e
        }
    }
}

/**
 * Media audio focus for the DAB sound (from DABplay+'s AudioFocus, MIT): kept while it plays, a call silences it, a
 * navigation prompt lowers it, another player takes it for good (the FM or the internet stream of this app too).
 */
private class MediaFocus(
    private val am: AudioManager,
) : Focus {
    private var request: AudioFocusRequest? = null

    @Volatile
    private var onChange: (FocusChange) -> Unit = {}

    @Synchronized
    override fun request(onChange: (FocusChange) -> Unit): Boolean {
        this.onChange = onChange
        if (request != null) return true
        val r =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener {
                    when (it) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            abandon()
                            this.onChange(FocusChange.LOST)
                        }

                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> this.onChange(FocusChange.SILENCE)

                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> this.onChange(FocusChange.DUCK)

                        AudioManager.AUDIOFOCUS_GAIN -> this.onChange(FocusChange.RESTORE)
                    }
                }.build()
        if (am.requestAudioFocus(r) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        request = r
        return true
    }

    @Synchronized
    override fun abandon() {
        request?.let { am.abandonAudioFocusRequest(it) }
        request = null
    }
}
