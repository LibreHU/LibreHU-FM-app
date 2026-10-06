package org.librehu.fm

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import java.lang.reflect.Array as JArray

/**
 * Plays the FM chip's audio the way Jancar's radio app does on the UJC201:
 * - first choice, a hardware audio patch FM tuner → speaker (hidden `AudioManager.createAudioPatch`, needs
 *   MODIFY_AUDIO_ROUTING): the audio never goes through the app, and a silent media AudioTrack keeps the music
 *   stream active, like Jancar's `PlayTrackUtils`;
 * - fallback, the "render" path of AOSP FMRadio: the `RADIO_TUNER` capture source (1998) copied to a media
 *   AudioTrack (needs CAPTURE_AUDIO_OUTPUT and RECORD_AUDIO).
 *
 * [muteChip] mutes the chip itself (focus loss) when the audio does not go through the app.
 */
class FmAudio(
    private val muteChip: (Boolean) -> Unit,
) {
    enum class Path { NONE, PATCH, RENDER }

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var patch: Any? = null

    @Volatile
    var path: Path = Path.NONE
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var volume: Float = 1f
        set(value) {
            field = value
            if (path == Path.PATCH) muteChip(value == 0f) else track?.setVolume(value)
        }

    @Volatile
    private var track: AudioTrack? = null

    /** Hardware patch, before the chip powers up like Jancar's `FmService.powerUp`. */
    fun preparePatch(mode: AudioMode) {
        if (patch == null && mode != AudioMode.RENDER) patch = HwPatch.create()
    }

    fun start(mode: AudioMode) {
        if (running) return
        lastError = ""
        preparePatch(mode)
        if (patch == null && mode == AudioMode.PATCH) {
            // Forced hardware path (settings): no silent fallback, the user tests this path.
            fail("Audio patch FM tuner -> speaker refused (MODIFY_AUDIO_ROUTING / hidden API?)")
            return
        }
        running = true
        path = if (patch != null) Path.PATCH else Path.RENDER
        Log.i(TAG, "audio path: $path")
        thread = Thread(if (path == Path.PATCH) ::keepAlive else ::render, "fm-audio").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
        patch?.let { HwPatch.release(it) }
        patch = null
        path = Path.NONE
    }

    /** Silent media track while the hardware patch plays: the music stream stays active (volume keys, policy). */
    private fun keepAlive() {
        val size = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, ENCODING).coerceAtLeast(4096)
        val t =
            try {
                buildTrack(size)
            } catch (e: Exception) {
                Log.w(TAG, "silent track: ${e.message}")
                return
            }
        track = t
        val silence = ByteArray(SILENCE_BYTES)
        try {
            t.play()
            while (running) {
                t.write(silence, 0, silence.size)
                Thread.sleep(SILENCE_SLEEP_MS)
            }
        } catch (_: Exception) {
        } finally {
            try {
                t.stop()
            } catch (_: IllegalStateException) {
            }
            t.release()
            track = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun render() {
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS_IN, ENCODING).coerceAtLeast(4096)
        val record =
            try {
                AudioRecord(RADIO_TUNER, SAMPLE_RATE, CHANNELS_IN, ENCODING, bufSize)
            } catch (e: Exception) {
                fail("AudioRecord: ${e.message}")
                return
            }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            fail("FM capture refused (CAPTURE_AUDIO_OUTPUT / RECORD_AUDIO missing?)")
            return
        }
        val t = buildTrack(bufSize)
        track = t
        t.setVolume(volume)
        val buf = ByteArray(bufSize)
        try {
            record.startRecording()
            t.play()
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    t.write(buf, 0, n)
                } else if (n < 0) {
                    Thread.sleep(20)
                }
            }
        } catch (e: Exception) {
            fail("FM audio: ${e.message}")
        } finally {
            try {
                record.stop()
            } catch (_: IllegalStateException) {
            }
            record.release()
            try {
                t.stop()
            } catch (_: IllegalStateException) {
            }
            t.release()
            track = null
        }
    }

    private fun buildTrack(size: Int): AudioTrack =
        AudioTrack
            .Builder()
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            ).setAudioFormat(
                AudioFormat
                    .Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(ENCODING)
                    .build(),
            ).setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

    private fun fail(message: String) {
        lastError = message
        running = false
        Log.e(TAG, message)
    }

    /**
     * Hidden audio patch API of Android 9 (`AudioManager.listAudioPorts / createAudioPatch / releaseAudioPatch`), by
     * reflection. Same ports as Jancar's `createAudioPatchBySpeaker`: AUDIO_DEVICE_IN_FM_TUNER → speaker.
     */
    private object HwPatch {
        private const val DEVICE_IN_FM_TUNER = 0x80002000.toInt()
        private const val DEVICE_OUT_SPEAKER = 0x2

        fun create(): Any? =
            try {
                val portCls = Class.forName("android.media.AudioPort")
                val devPortCls = Class.forName("android.media.AudioDevicePort")
                val cfgCls = Class.forName("android.media.AudioPortConfig")
                val patchCls = Class.forName("android.media.AudioPatch")
                val ports = ArrayList<Any>()
                AudioManager::class.java.getMethod("listAudioPorts", ArrayList::class.java).invoke(null, ports)
                val type = devPortCls.getMethod("type")
                val active = portCls.getMethod("activeConfig")
                val devices = ports.filter { devPortCls.isInstance(it) }
                val source = devices.firstOrNull { type.invoke(it) == DEVICE_IN_FM_TUNER }
                val sink = devices.firstOrNull { type.invoke(it) == DEVICE_OUT_SPEAKER }
                if (source == null || sink == null) {
                    Log.i(TAG, "audio patch: no FM tuner or speaker port")
                    null
                } else {
                    val out = JArray.newInstance(patchCls, 1)
                    val sources = JArray.newInstance(cfgCls, 1).also { JArray.set(it, 0, active.invoke(source)) }
                    val sinks = JArray.newInstance(cfgCls, 1).also { JArray.set(it, 0, active.invoke(sink)) }
                    val res =
                        AudioManager::class.java
                            .getMethod("createAudioPatch", out.javaClass, sources.javaClass, sinks.javaClass)
                            .invoke(null, out, sources, sinks) as Int
                    val p = JArray.get(out, 0)
                    Log.i(TAG, "audio patch FM tuner -> speaker: $res")
                    if (res == 0 && p != null) p else null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "audio patch: ${e.cause ?: e}")
                null
            }

        fun release(patch: Any) {
            try {
                AudioManager::class.java
                    .getMethod("releaseAudioPatch", Class.forName("android.media.AudioPatch"))
                    .invoke(null, patch)
            } catch (e: Throwable) {
                Log.w(TAG, "release audio patch: ${e.cause ?: e}")
            }
        }
    }

    private companion object {
        const val TAG = "LibreHU-FM"
        const val RADIO_TUNER = 1998 // MediaRecorder.AudioSource.RADIO_TUNER (@SystemApi)
        const val SAMPLE_RATE = 44100
        const val CHANNELS_IN = AudioFormat.CHANNEL_IN_STEREO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val SILENCE_BYTES = 100
        const val SILENCE_SLEEP_MS = 50L
    }
}
