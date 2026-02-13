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
    var shizukuApplication: IShizukuApplication? = null

    val lastDenyTimeMap: MutableMap<String, Long> = mutableMapOf()

    val allowedMap: MutableMap<String, Boolean> = mutableMapOf()

    val onetimeMap: MutableMap<String, Boolean> = mutableMapOf()

    fun dispatchRequestPermissionResult(requestCode: Int, allowed: Boolean, onetime: Boolean, permission: String = "stellar") {
        if (!allowed) lastDenyTimeMap[permission] = System.currentTimeMillis()

        val reply = Bundle().apply {
            putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
            putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        }

        try {
            client?.dispatchRequestPermissionResult(requestCode, reply)
        } catch (e: Throwable) {
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName)
        }
    }

    fun dispatchShizukuPermissionResult(requestCode: Int, allowed: Boolean, serviceUid: Int, serviceVersion: Int, serviceSeContext: String?) {
        val app = shizukuApplication ?: return
        if (!allowed) lastDenyTimeMap[ShizukuApiConstants.PERMISSION_NAME] = System.currentTimeMillis()

        try {
            app.dispatchRequestPermissionResult(requestCode, Bundle().apply {
                putBoolean(ShizukuApiConstants.EXTRA_ALLOWED, allowed)
            })
            LOGGER.i("已通知 Shizuku 客户端权限结果: uid=$uid, pid=$pid, allowed=$allowed")

            if (allowed) {
                val replyServerVersion = if (apiVersion == -1) 12 else ShizukuApiConstants.SERVER_VERSION
                app.bindApplication(Bundle().apply {
                    putInt(ShizukuApiConstants.BindApplication.SERVER_UID, serviceUid)
                    putInt(ShizukuApiConstants.BindApplication.SERVER_VERSION, replyServerVersion)
                    putInt(ShizukuApiConstants.BindApplication.SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION)
                    putString(ShizukuApiConstants.BindApplication.SERVER_SECONTEXT, serviceSeContext)
                    putBoolean(ShizukuApiConstants.BindApplication.PERMISSION_GRANTED, true)
                    putBoolean(ShizukuApiConstants.BindApplication.SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false)
                })
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