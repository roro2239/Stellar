package roro.stellar.server.shizuku

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import moe.shizuku.server.IShizukuApplication
import roro.stellar.server.util.Logger

/**
 * Shizuku 客户端记录
 * 用于管理使用 Shizuku API 的客户端
 */
class ShizukuClientRecord(
    val uid: Int,
    val pid: Int,
    val client: IShizukuApplication,
    val packageName: String,
    val apiVersion: Int
) {
    var allowed: Boolean = false
    var onetime: Boolean = false
    var lastDenyTime: Long = 0

    fun dispatchRequestPermissionResult(
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean
    ) {
        this.allowed = allowed
        this.onetime = onetime

        if (!allowed) {
            lastDenyTime = System.currentTimeMillis()
        }

        val data = Bundle()
        data.putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)

        try {
            client.dispatchRequestPermissionResult(requestCode, data)
        } catch (e: RemoteException) {
            LOGGER.w(e, "dispatchRequestPermissionResult 失败")
        }
    }

    companion object {
        private val LOGGER = Logger("ShizukuClientRecord")
    }
}
