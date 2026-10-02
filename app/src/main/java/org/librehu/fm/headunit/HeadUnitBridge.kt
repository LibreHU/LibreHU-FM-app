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

    /**
     * True when the head unit arbitrates the audio sources itself and takes the Android audio focus on the radio's
     * behalf (Jancar ivi-services does when the radio opens). The app must then not request the focus, or it
     * immediately loses it to the head unit and stops.
     */
    val managesAudioFocus: Boolean get() = false

    companion object {
        fun create(context: Context): HeadUnitBridge =
            if (isInstalled(context, "org.librehu.service")) LibreHuBridge(context) else NoHeadUnit

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
