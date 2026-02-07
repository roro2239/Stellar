package roro.stellar.shizuku.server

import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IShizukuApplication

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
            Log.w(TAG, "dispatchRequestPermissionResult 失败", e)
        }
    }

    companion object {
        private const val TAG = "ShizukuClientRecord"
    }
}
