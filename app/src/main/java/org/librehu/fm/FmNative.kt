package org.librehu.fm

/**
 * MediaTek FM driver (`/dev/fm`) through the AOSP FM JNI (`src/main/cpp/fmr`). Frequencies are in MHz.
 * Not thread safe: call from [FmTuner]'s thread only (except [stopScan]).
 */
object FmNative {
    /** Null when the library or the driver configuration could not be loaded. */
    val loadError: Throwable? =
        try {
            System.loadLibrary("librehufm")
            null
        } catch (e: Throwable) {
            e
        }

    @JvmStatic external fun openDev(): Boolean

    @JvmStatic external fun closeDev(): Boolean

    @JvmStatic external fun powerUp(frequency: Float): Boolean

    @JvmStatic external fun powerDown(type: Int): Boolean

    @JvmStatic external fun tune(frequency: Float): Boolean

    /** Next station from [frequency] (up or down); returns its frequency in MHz. */
    @JvmStatic external fun seek(
        frequency: Float,
        up: Boolean,
    ): Float

    /** Full band scan; stations in 100 kHz units (875 = 87.5 MHz), or null. */
    @JvmStatic external fun autoScan(): ShortArray?

    @JvmStatic external fun stopScan(): Boolean

    @JvmStatic external fun setRds(on: Boolean): Int

    /** Waits for RDS data; returns the RDS_EVENT_* flags that changed. */
    @JvmStatic external fun readRds(): Short

    @JvmStatic external fun getPs(): ByteArray?

    @JvmStatic external fun getLrText(): ByteArray?

    @JvmStatic external fun activeAf(): Short

    @JvmStatic external fun setMute(mute: Boolean): Int

    @JvmStatic external fun isRdsSupport(): Int

    @JvmStatic external fun switchAntenna(antenna: Int): Int

    /** [PI, PTY, TP, TA] of the last RDS data read by [readRds]. */
    @JvmStatic external fun getRdsInfo(): IntArray?

    /**
     * Band plan for the next power-up, seek and scan: [band] FM_BAND_UE (1) or FM_BAND_JAPAN (2), [space] 1 = 100 kHz or
     * 2 = 200 kHz, limits in 100 kHz units, 75 µs de-emphasis (applied by the AC8257 driver at power-up).
     */
    @JvmStatic external fun setRegion(
        band: Int,
        space: Int,
        low: Int,
        high: Int,
        deemphasis75: Boolean,
    )

    /**
     * RT+ tags of the last RDS data read by [readRds] (AC8257 driver): [toggle, running, content type, start, length
     * marker of the first tag, then of the second], or null.
     */
    @JvmStatic external fun getRtPlus(): IntArray?

    /**
     * Raw RDS groups of the driver's last RDS interrupt (up to 12), as `[A, B, C, D, crc]` per group (crc: 0x08 block A
     * good, 0x04 B, 0x02 C, 0x01 D), or null when there is nothing new: for what the driver does not decode ([Ert]).
     */
    @JvmStatic external fun readRdsGroups(): ShortArray?

    const val RDS_EVENT_FLAGS = 0x0001
    const val RDS_EVENT_PI_CODE = 0x0002
    const val RDS_EVENT_PTY_CODE = 0x0004
    const val RDS_EVENT_PROGRAMNAME = 0x0008
    const val RDS_EVENT_LAST_RADIOTEXT = 0x0040
    const val RDS_EVENT_AF = 0x0080
    const val RDS_EVENT_RTP_TAG = 0x4000
}
