package org.librehu.fm.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Jancar ivi-services integration: like the stock radio app, open / close the radio through
 * `com.jancar.services.radio.IRadio`. ivi-services then makes the radio the active media source and, in the default
 * antenna mode ("auto"), powers the antenna (GPIO 110 + MCU 0x43). Raw binder calls: no Jancar classes needed.
 * Reference: LibreHU-service docs/ivi-services/api.md (IRadio 1 = open(callback, package), 2 = close()).
 */
class IviRadioBridge(
    private val context: Context,
) : HeadUnitBridge {
    override val name = "Jancar ivi-services"

    @Volatile
    private var radio: IBinder? = null
    private var wantOpen = false
    private var bound = false

    /** ivi-services keeps a callback per client; ours ignores its events (RDS etc. come from our own chip access). */
    private val callback =
        object : Binder() {
            override fun onTransact(
                code: Int,
                data: Parcel,
                reply: Parcel?,
                flags: Int,
            ): Boolean {
                if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags)
                reply?.writeNoException()
                return true
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                radio = service
                if (wantOpen) open()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                radio = null
            }
        }

    init {
        bound =
            try {
                context.bindService(
                    Intent(ACTION).setPackage(PACKAGE),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "ivi-services radio: ${e.message}")
                false
            }
    }

    override fun onRadioOn() {
        wantOpen = true
        open()
    }

    override fun onRadioOff() {
        wantOpen = false
        call(TX_CLOSE) {}
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

    private fun open() =
        call(TX_OPEN) {
            it.writeStrongBinder(callback)
            it.writeString(context.packageName)
        }

    private fun call(
        code: Int,
        args: (Parcel) -> Unit,
    ) {
        val b = radio ?: return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            args(data)
            b.transact(code, data, reply, 0)
            reply.readException()
        } catch (e: Exception) {
            Log.w(TAG, "IRadio $code: ${e.message}")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private companion object {
        const val TAG = "LibreHU-FM"
        const val PACKAGE = "com.jancar.services"
        const val ACTION = "com.jancar.services.action.radio"
        const val DESCRIPTOR = "com.jancar.services.radio.IRadio"
        const val TX_OPEN = 1
        const val TX_CLOSE = 2
    }
}
