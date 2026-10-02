package org.librehu.fm.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import org.librehu.service.ILibreHuService

/**
 * LibreHU-service integration (https://github.com/LibreHU/LibreHU-service): asks for the radio antenna power while
 * the radio plays (API 2 `setRadioAntenna`). The service keeps it off while the ignition is off.
 */
class LibreHuBridge(
    private val context: Context,
) : HeadUnitBridge {
    override val name = "LibreHU-service"

    @Volatile
    private var service: ILibreHuService? = null
    private var antenna = false
    private var bound = false

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                service = binder?.let { ILibreHuService.Stub.asInterface(it) }
                apply()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

    init {
        bound =
            try {
                context.bindService(Intent(ACTION_BIND).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.w(TAG, "LibreHU-service: ${e.message}")
                false
            }
    }

    override fun onRadioOn() {
        antenna = true
        apply()
    }

    override fun onRadioOff() {
        antenna = false
        apply()
    }

    override fun release() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
        }
        bound = false
    }

    private fun apply() {
        val s = service ?: return
        try {
            if (s.apiVersion >= 2) s.setRadioAntenna(antenna) else Log.w(TAG, "LibreHU-service API < 2: no antenna control")
        } catch (e: RemoteException) {
            Log.w(TAG, "setRadioAntenna: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "LibreHU-FM"
        const val PACKAGE = "org.librehu.service"
        const val ACTION_BIND = "org.librehu.service.BIND"
    }
}
