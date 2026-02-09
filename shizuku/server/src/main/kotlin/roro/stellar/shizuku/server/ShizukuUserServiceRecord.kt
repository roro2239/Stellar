package roro.stellar.shizuku.server

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteCallbackList
import android.util.Log
import moe.shizuku.server.IShizukuServiceConnection
import java.util.UUID

/**
 * Shizuku 用户服务记录
 * 管理单个用户服务的生命周期和连接
 */
abstract class ShizukuUserServiceRecord(
    val versionCode: Int,
    daemon: Boolean
) {
    companion object {
        private const val TAG = "ShizukuUserServiceRecord"
    }

    val token: String = "${UUID.randomUUID()}-${System.currentTimeMillis()}"
    var service: IBinder? = null
        private set
    var daemon: Boolean = daemon
        private set
    var starting: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var startTimeoutCallback: Runnable? = null

    private val deathRecipient = IBinder.DeathRecipient {
        Log.v(TAG, "Binder for service record $token is dead")
        removeSelf()
    }

    val callbacks = object : RemoteCallbackList<IShizukuServiceConnection>() {
        override fun onCallbackDied(callback: IShizukuServiceConnection?) {
            if (this@ShizukuUserServiceRecord.daemon || registeredCallbackCount != 0) {
                return
            }
            Log.v(TAG, "Remove service record $token since it does not run as a daemon and all connections are gone")
            removeSelf()
        }
    }

    abstract fun removeSelf()

    fun setStartingTimeout(timeoutMillis: Long) {
        if (starting) {
            Log.w(TAG, "Service record $token is already starting")
            return
        }

        Log.v(TAG, "Set starting timeout for service record $token: $timeoutMillis")
        starting = true

        startTimeoutCallback = Runnable {
            if (starting) {
                Log.w(TAG, "Service record $token is not started in $timeoutMillis ms")
                removeSelf()
            }
        }
        mainHandler.postDelayed(startTimeoutCallback!!, timeoutMillis)
    }

    fun setDaemon(daemon: Boolean) {
        this.daemon = daemon
    }

    fun setBinder(binder: IBinder) {
        Log.v(TAG, "Binder received for service record $token")

        startTimeoutCallback?.let { mainHandler.removeCallbacks(it) }
        starting = false

        service = binder

        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (tr: Throwable) {
            Log.w(TAG, "linkToDeath $token", tr)
        }

        broadcastBinderReceived()
    }

    fun broadcastBinderReceived() {
        Log.v(TAG, "Broadcast binder received for service record $token")

        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).connected(service)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to call connected $token", e)
            }
        }
        callbacks.finishBroadcast()
    }

    fun broadcastBinderDied() {
        Log.v(TAG, "Broadcast binder died for service record $token")

        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i).died()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to call died $token", e)
            }
        }
        callbacks.finishBroadcast()
    }

    fun destroy() {
        service?.unlinkToDeath(deathRecipient, 0)

        service?.let { binder ->
            if (binder.pingBinder()) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    binder.interfaceDescriptor?.let { descriptor ->
                        data.writeInterfaceToken(descriptor)
                    }
                    binder.transact(
                        ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy,
                        data, reply, Binder.FLAG_ONEWAY
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to call destroy $token", e)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }

        callbacks.kill()
    }
}
