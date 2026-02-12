package roro.stellar.server.shizuku

import android.os.IBinder
import android.os.RemoteCallbackList
import moe.shizuku.server.IShizukuServiceConnection
import roro.stellar.server.util.Logger

/**
 * Shizuku 用户服务记录
 * 管理 IShizukuServiceConnection 回调列表
 */
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

    /**
     * 服务连接成功，广播给所有回调
     */
    fun onServiceConnected(binder: IBinder) {
        serviceBinder = binder
        broadcastConnected()
    }

    /**
     * 服务断开，广播给所有回调
     */
    fun onServiceDisconnected() {
        serviceBinder = null
        broadcastDied()
    }

    private fun broadcastConnected() {
        val binder = serviceBinder ?: return
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).connected(binder)
                } catch (e: Exception) {
                    LOGGER.w(e, "广播 connected 失败")
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun broadcastDied() {
        val count = callbacks.beginBroadcast()
        try {
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i).died()
                } catch (e: Exception) {
                    LOGGER.w(e, "广播 died 失败")
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }
}
