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
    fun dispatchShizukuPermissionResult(requestCode: Int, allowed: Boolean, serviceUid: Int, serviceVersion: Int, serviceSeContext: String?) {
        val app = shizukuApplication ?: return
        if (!allowed) lastDenyTimeMap[ShizukuApiConstants.PERMISSION_NAME] = System.currentTimeMillis()
        try {
            val data = Bundle()
            data.putBoolean(ShizukuApiConstants.EXTRA_ALLOWED, allowed)
            app.dispatchRequestPermissionResult(requestCode, data)
            LOGGER.i("已通知 Shizuku 客户端权限结果: uid=$uid, pid=$pid, allowed=$allowed")

            // 重新调用 bindApplication 更新客户端缓存的权限状态
            if (allowed) {
                // 兼容旧版客户端 (API <= v12)
                val replyServerVersion = if (apiVersion == -1) 12 else ShizukuApiConstants.SERVER_VERSION
                val reply = Bundle().apply {
                    putInt(ShizukuApiConstants.BindApplication.SERVER_UID, serviceUid)
                    putInt(ShizukuApiConstants.BindApplication.SERVER_VERSION, replyServerVersion)
                    putInt(ShizukuApiConstants.BindApplication.SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION)
                    putString(ShizukuApiConstants.BindApplication.SERVER_SECONTEXT, serviceSeContext)
                    putBoolean(ShizukuApiConstants.BindApplication.PERMISSION_GRANTED, true)
                    putBoolean(ShizukuApiConstants.BindApplication.SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false)
                }
                app.bindApplication(reply)
                LOGGER.i("已重新绑定 Shizuku 客户端: uid=$uid, pid=$pid, granted=true, version=$replyServerVersion")
            }
        } catch (e: Throwable) {
            LOGGER.w(e, "dispatchShizukuPermissionResult failed for client (uid=%d, pid=%d)", uid, pid)
        }
    }

    companion object {
        protected val LOGGER: Logger = Logger("ClientRecord")
    }
}