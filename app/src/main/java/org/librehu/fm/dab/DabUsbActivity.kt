package org.librehu.fm.dab

import android.app.Activity
import android.os.Bundle

/**
 * Where Android sends "a DAB dongle was plugged in" (res/xml/dab_dongles.xml). Invisible: it connects the dongle to
 * the engine and closes, nothing comes to the front. Ticking "use by default" in Android's prompt gives LibreHU FM the
 * dongle for good: no question at the next car start.
 */
class DabUsbActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DabRadio.get(this).connect(ask = false)
        finish()
    }
}
