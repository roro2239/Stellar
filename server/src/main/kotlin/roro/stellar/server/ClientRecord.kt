package roro.stellar.server

import android.os.Bundle
import com.stellar.server.IStellarApplication
import moe.shizuku.server.IShizukuApplication
import roro.stellar.StellarApiConstants
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.util.Logger

open class ClientRecord(
    val uid: Int,
    val pid: Int,
    val client: IStellarApplication?,
    val packageName: String,
    val apiVersion: Int
) {
    // Shizuku 应用引用
    var shizukuApplication: IShizukuApplication? = null

    val lastDenyTimeMap: MutableMap<String, Long> = mutableMapOf()

    val allowedMap: MutableMap<String, Boolean> = mutableMapOf()

    val onetimeMap: MutableMap<String, Boolean> = mutableMapOf()

    fun dispatchRequestPermissionResult(requestCode: Int, allowed: Boolean, onetime: Boolean, permission: String = "stellar") {
        val reply = Bundle()
        reply.putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        reply.putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        if (!allowed) lastDenyTimeMap[permission] = System.currentTimeMillis()
        try {
            client?.dispatchRequestPermissionResult(requestCode, reply)
        } catch (e: Throwable) {
            LOGGER.w(
                e,
                "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)",
                uid,
                pid,
                packageName
            )
        }
    }

    /**
     * 分发 Shizuku 权限结果
     */
    fun dispatchShizukuPermissionResult(requestCode: Int, allowed: Boolean) {
        val app = shizukuApplication ?: return
        if (!allowed) lastDenyTimeMap[ShizukuApiConstants.PERMISSION_NAME] = System.currentTimeMillis()
        try {
            val data = Bundle()
            data.putBoolean(ShizukuApiConstants.EXTRA_ALLOWED, allowed)
            app.dispatchRequestPermissionResult(requestCode, data)
            LOGGER.i("已通知 Shizuku 客户端权限结果: uid=$uid, pid=$pid, allowed=$allowed")
        } catch (e: Throwable) {
            LOGGER.w(e, "dispatchShizukuPermissionResult failed for client (uid=%d, pid=%d)", uid, pid)
        }
    }

    companion object {
        protected val LOGGER: Logger = Logger("ClientRecord")
    }
}