package org.librehu.fm

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log

/**
 * Plays the FM chip's audio: the MediaTek HAL exposes the tuner as the `RADIO_TUNER` capture source (1998), copied
 * to a media AudioTrack — the "render" path of AOSP FMRadio and of Jancar's radio app. Needs CAPTURE_AUDIO_OUTPUT
 * (privileged install) and RECORD_AUDIO.
 */
class FmAudio {
    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var volume: Float = 1f
        set(value) {
            field = value
            track?.setVolume(value)
        }

    @Volatile
    private var track: AudioTrack? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "fm-audio").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop() {
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
        val t =
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
                ).setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        track = t
        t.setVolume(volume)
        val buf = ByteArray(bufSize)
        try {
            record.startRecording()
            t.play()
            lastError = ""
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

    private fun fail(message: String) {
        lastError = message
        running = false
        Log.e(TAG, message)
    }

    private companion object {
        const val TAG = "LibreHU-FM"
        const val RADIO_TUNER = 1998 // MediaRecorder.AudioSource.RADIO_TUNER (@SystemApi)
        const val SAMPLE_RATE = 44100
        const val CHANNELS_IN = AudioFormat.CHANNEL_IN_STEREO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
