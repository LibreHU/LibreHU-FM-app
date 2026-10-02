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

    const val RDS_EVENT_PROGRAMNAME = 0x0008
    const val RDS_EVENT_LAST_RADIOTEXT = 0x0040
    const val RDS_EVENT_AF = 0x0080
}
