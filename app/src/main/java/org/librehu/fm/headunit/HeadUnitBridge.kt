package org.librehu.fm.headunit

import android.content.Context

/**
 * What the radio needs from the head unit around the FM chip: antenna power and, with Jancar ivi-services, telling
 * the head unit that the radio is the active source. Each branch of this repository provides its implementation:
 * `main` none, `ivi` Jancar ivi-services, `librehu-service` LibreHU-service.
 */
interface HeadUnitBridge {
    /** Radio powered up: switch the antenna on, claim the source. */
    fun onRadioOn() {}

    /** Radio powered down. */
    fun onRadioOff() {}

    fun release() {}

    /** Shown in the settings / about line. */
    val name: String

    companion object {
        fun create(context: Context): HeadUnitBridge =
            if (isInstalled(context, "com.jancar.services")) IviRadioBridge(context) else NoHeadUnit

        private fun isInstalled(
            context: Context,
            pkg: String,
        ): Boolean =
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
    }
}

/** Plain Android device: the antenna is not switched (passive antenna or always powered). */
object NoHeadUnit : HeadUnitBridge {
    override val name = "Android"
}
