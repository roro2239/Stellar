package roro.stellar.server.shizuku

import android.os.IBinder
import android.os.RemoteCallbackList
import moe.shizuku.server.IShizukuServiceConnection
import roro.stellar.server.util.Logger

class ShizukuUserServiceRecord(
    val key: String,
    val stellarToken: String,
    val daemon: Boolean
) {
    companion object {
        private val LOGGER = Logger("ShizukuUserServiceRecord")
    }

    val callbacks = RemoteCallbackList<IShizukuServiceConnection>()

    var serviceBinder: IBinder? = null
        private set

    fun onServiceConnected(binder: IBinder) {
        serviceBinder = binder
        broadcast("connected") { it.connected(binder) }
    }

    fun onServiceDisconnected() {
        serviceBinder = null
        broadcast("died") { it.died() }
    }

    private inline fun broadcast(action: String, block: (IShizukuServiceConnection) -> Unit) {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    block(callbacks.getBroadcastItem(i))
                } catch (e: Exception) {
                    LOGGER.w(e, "广播 $action 失败")
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }
}
