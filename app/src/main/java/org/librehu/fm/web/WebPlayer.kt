package org.librehu.fm.web

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Internet radio player (Media3 ExoPlayer, main thread): resolves the station's address (playlist files, HLS), plays
 * it, reads the song title the stream sends (ICY "StreamTitle", SHOUTcast / Icecast) and reconnects by itself after a
 * network loss (tunnel, no mobile data), every 2 s at first, then up to every 30 s, as long as the user wants it to play.
 * Audio focus: handled by ExoPlayer (pause on a call or navigation prompt, lower volume on a "duck").
 */
@OptIn(UnstableApi::class)
class WebPlayer(
    private val context: Context,
    private val userAgent: String,
    private val onStatus: (Status) -> Unit,
) {
    data class Status(
        val station: WebStation? = null,
        /** The user asked to play (a reconnection may be running). */
        val wanted: Boolean = false,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        /** "Artist - Title" sent by the stream, "" when none. */
        val title: String = "",
        /** Audio really played: MIME type ("audio/mpeg", "audio/mp4a-latm"…), kbit/s (0 = unknown), Hz. */
        val mime: String = "",
        val bitrate: Int = 0,
        val sampleRate: Int = 0,
        val error: String = "",
        val retrying: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var status = Status()
    private var job: Job? = null
    private var attempt = 0
    private var headerBitrate = 0

    val current: Status get() = status

    /** Plays [station] from the live position (a paused live stream is played again from now). */
    fun play(station: WebStation) {
        job?.cancel()
        attempt = 0
        update { Status(station = station, wanted = true, buffering = true) }
        start(station)
    }

    /** Stops and disconnects (no data used while stopped). */
    fun stop() {
        job?.cancel()
        job = null
        player?.stop()
        player?.clearMediaItems()
        update { it.copy(wanted = false, playing = false, buffering = false, retrying = false, error = "") }
    }

    /** Network back while waiting to reconnect: connect now instead of at the end of the wait. */
    fun retryNow() {
        val station = status.station ?: return
        if (!status.wanted || !status.retrying) return
        job?.cancel()
        Log.i(TAG, "network back: reconnecting to ${station.name} now")
        start(station)
    }

    fun release() {
        stop()
        player?.release()
        player = null
        scope.cancel()
    }

    private fun start(station: WebStation) {
        job =
            scope.launch {
                val resolved =
                    try {
                        withContext(Dispatchers.IO) { resolve(station.url, 0) }
                    } catch (e: Exception) {
                        Log.w(TAG, "resolve ${station.url}: $e")
                        // Not reachable now (no network?): the player gets the address and reports the error itself.
                        Resolved(station.url, hls = station.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true))
                    }
                Log.i(TAG, "play ${station.name}: ${resolved.url}${if (resolved.hls) " (HLS)" else ""}")
                headerBitrate = 0
                val p = player ?: create().also { player = it }
                val item =
                    MediaItem
                        .Builder()
                        .setUri(resolved.url)
                        .apply { if (resolved.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                        .build()
                p.setMediaItem(item)
                p.prepare()
                p.playWhenReady = true
            }
    }

    private fun create(): ExoPlayer {
        val http =
            DefaultHttpDataSource
                .Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
        // A larger buffer than the default for a car: short coverage holes pass without a cut.
        val load =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
                .build()
        return ExoPlayer
            .Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            ).setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(listener) }
    }

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) attempt = 0
                update { it.copy(playing = isPlaying, retrying = if (isPlaying) false else it.retrying, error = if (isPlaying) "" else it.error) }
                if (isPlaying) readFormat()
            }

            override fun onPlaybackStateChanged(state: Int) {
                update { it.copy(buffering = state == Player.STATE_BUFFERING && it.wanted) }
                // A live stream should never end: the server closed it, connect again.
                if (state == Player.STATE_ENDED && status.wanted) retry("stream ended")
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                // Audio focus lost for good (another player): the radio stops and frees the network.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) stop()
            }

            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    when (val e = metadata.get(i)) {
                        is IcyInfo -> {
                            val t = e.title?.trim().orEmpty()
                            update { it.copy(title = t) }
                        }

                        is IcyHeaders -> {
                            if (e.bitrate > 0) headerBitrate = e.bitrate / 1000
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "playback error ${error.errorCodeName}: ${error.message}")
                if (!status.wanted) return
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player?.seekToDefaultPosition()
                    player?.prepare()
                    return
                }
                retry(error.errorCodeName)
            }
        }

    /** Reconnection after [delayFor] (2, 4, 8, 15, then 30 s), while the station is still wanted. */
    private fun retry(why: String) {
        val station = status.station ?: return
        val wait = delayFor(attempt++)
        update { it.copy(playing = false, buffering = false, retrying = true, error = why) }
        Log.i(TAG, "reconnecting to ${station.name} in ${wait / 1000} s ($why)")
        job?.cancel()
        job =
            scope.launch {
                delay(wait)
                if (status.wanted) start(station)
            }
    }

    private fun readFormat() {
        val f = player?.audioFormat ?: return
        val kbps = listOf(f.averageBitrate, f.peakBitrate, f.bitrate).firstOrNull { it > 0 }?.div(1000) ?: headerBitrate
        update { it.copy(mime = f.sampleMimeType.orEmpty(), bitrate = kbps, sampleRate = f.sampleRate.coerceAtLeast(0)) }
    }

    private fun update(change: (Status) -> Status) {
        val next = change(status)
        if (next == status) return
        status = next
        onStatus(next)
    }

    private data class Resolved(
        val url: String,
        val hls: Boolean,
    )

    /**
     * Address to give the player: a playlist (by extension, by Content-Type or by content) is opened and its first
     * stream taken (nested playlists followed [MAX_DEPTH] times); an HLS `.m3u8` is played as HLS; anything else
     * (audio) is closed at once and played as it is. If this check fails (server that answers only to a player,
     * no network), the address is given to the player as it is.
     */
    private fun resolve(
        url: String,
        depth: Int,
    ): Resolved {
        if (depth > MAX_DEPTH) return Resolved(url, false)
        var target = url
        var conn: HttpURLConnection? = null
        try {
            // Redirects by hand: HttpURLConnection does not follow http -> https.
            for (hop in 0..MAX_REDIRECTS) {
                conn = (URL(target).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Icy-MetaData", "0")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    target = URL(URL(target), loc).toString()
                    conn.disconnect()
                    continue
                }
                break
            }
            val c = conn ?: return Resolved(url, false)
            val byType = Playlists.kindOfContentType(c.contentType)
            val byName = Playlists.kindOfUrl(target)
            val textual = c.contentType?.startsWith("text/", ignoreCase = true) == true
            if (byType == Playlists.Kind.NONE && byName == Playlists.Kind.NONE && !textual) return Resolved(target, false)
            val text = readText(c)
            return when (Playlists.kindOfText(text)) {
                Playlists.Kind.HLS -> Resolved(target, true)
                Playlists.Kind.PLS, Playlists.Kind.M3U -> {
                    val first = Playlists.parse(text).firstOrNull() ?: return Resolved(target, false)
                    resolve(URL(URL(target), first.url).toString(), depth + 1)
                }
                else -> Resolved(target, false)
            }
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TAG = "LibreHU-Web"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 60_000
        private const val START_BUFFER_MS = 2_500
        private const val REBUFFER_MS = 5_000
        private const val MAX_DEPTH = 3
        private const val MAX_REDIRECTS = 5
        private const val MAX_PLAYLIST_BYTES = 256 * 1024

        fun delayFor(attempt: Int): Long = longArrayOf(2_000, 4_000, 8_000, 15_000)[attempt.coerceAtMost(3)].takeIf { attempt < 4 } ?: 30_000

        /** Playlist text: at most [MAX_PLAYLIST_BYTES] read; UTF-8, else Latin-1 (old M3U files). */
        fun decode(bytes: ByteArray): String {
            val utf8 = String(bytes, Charsets.UTF_8)
            return if (utf8.contains('�')) String(bytes, Charset.forName("ISO-8859-1")) else utf8
        }

        private fun readText(c: HttpURLConnection): String {
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (out.size() < MAX_PLAYLIST_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            return decode(out.toByteArray())
        }
    }
}
