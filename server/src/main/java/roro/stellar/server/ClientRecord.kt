package roro.stellar.server

import android.os.Bundle
import com.stellar.server.IStellarApplication
import roro.stellar.StellarApiConstants
import roro.stellar.server.util.Logger

open class ClientRecord(
    val uid: Int,
    val pid: Int,
    val client: IStellarApplication,
    val packageName: String,
    val apiVersion: Int
) {

    val lastDenyTimeMap: MutableMap<String, Long> = mutableMapOf()

    val allowedMap: MutableMap<String, Boolean> = mutableMapOf()

    val onetimeMap: MutableMap<String, Boolean> = mutableMapOf()

    fun dispatchRequestPermissionResult(requestCode: Int, allowed: Boolean, onetime: Boolean, permission: String = "stellar") {
        val reply = Bundle()
        reply.putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        reply.putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        if (!allowed) lastDenyTimeMap[permission] = System.currentTimeMillis()
        try {
            client.dispatchRequestPermissionResult(requestCode, reply)
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

    companion object {
        protected val LOGGER: Logger = Logger("ClientRecord")
    }
}