package roro.stellar.server

import android.os.Bundle
import com.stellar.server.IStellarApplication
import roro.stellar.StellarApiConstants
import roro.stellar.server.util.Logger

/**
 * 客户端记录类
 * Client Record Class
 *
 *
 * 功能说明 Features：
 *
 *  * 存储已连接客户端的信息 - Stores information of connected clients
 *  * 管理客户端的权限状态 - Manages client permission status
 *  * 处理权限请求结果的分发 - Handles permission request result dispatch
 *
 *
 *
 * 包含信息 Information：
 *
 *  * UID和PID - 客户端进程标识
 *  * 包名和API版本 - 客户端应用信息
 *  * 权限状态 - 是否授权及一次性授权标志
 *
 */
open class ClientRecord
/**
 * 构造客户端记录
 * Construct client record
 *
 * @param uid 客户端UID
 * @param pid 客户端PID
 * @param client 客户端回调接口
 * @param packageName 客户端包名
 * @param apiVersion 客户端API版本
 */(
    /** 客户端UID Client UID  */
    val uid: Int,
    /** 客户端PID Client PID  */
    val pid: Int,
    /** 客户端回调接口 Client callback interface  */
    val client: IStellarApplication,
    /** 客户端包名 Client package name  */
    val packageName: String?,
    /** 客户端API版本 Client API version  */
    val apiVersion: Int
) {
    /** 是否已授权 Whether authorized  */
    var allowed: Boolean = false

    /** 是否为一次性授权 Whether one-time authorization  */
    var onetime: Boolean = false

    var lastDenyTime: Long = 0

    /**
     * 分发权限请求结果到客户端
     * Dispatch permission request result to client
     *
     * @param requestCode 请求码
     * @param allowed 是否允许
     */
    fun dispatchRequestPermissionResult(requestCode: Int, allowed: Boolean, onetime: Boolean) {
        val reply = Bundle()
        reply.putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        reply.putBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        if (!allowed) lastDenyTime = System.currentTimeMillis()
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