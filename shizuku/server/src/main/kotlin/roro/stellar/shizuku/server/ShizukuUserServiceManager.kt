package roro.stellar.shizuku.server

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IShizukuServiceConnection
import java.util.Collections
import java.util.UUID

/**
 * Shizuku 用户服务管理器
 * 管理通过 Shizuku API 启动的用户服务
 */
class ShizukuUserServiceManager {

    private val services = Collections.synchronizedMap(HashMap<String, ServiceRecord>())

    data class ServiceRecord(
        val token: String,
        val uid: Int,
        val pid: Int,
        val connection: IShizukuServiceConnection,
        val args: Bundle,
        var binder: IBinder? = null
    )

    fun addUserService(
        callingUid: Int,
        callingPid: Int,
        conn: IShizukuServiceConnection,
        args: Bundle
    ): Int {
        val token = UUID.randomUUID().toString()

        val record = ServiceRecord(
            token = token,
            uid = callingUid,
            pid = callingPid,
            connection = conn,
            args = args
        )

        services[token] = record
        Log.i(TAG, "添加 Shizuku 用户服务: token=$token, uid=$callingUid")

        return 0
    }

    fun removeUserService(
        conn: IShizukuServiceConnection,
        args: Bundle
    ): Int {
        val iterator = services.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.connection.asBinder() == conn.asBinder()) {
                iterator.remove()
                Log.i(TAG, "移除 Shizuku 用户服务: token=${entry.key}")
                return 0
            }
        }
        return 1
    }

    fun attachUserService(binder: IBinder, options: Bundle) {
        val token = options.getString(ShizukuApiConstants.USER_SERVICE_ARG_TOKEN) ?: return

        val record = services[token]
        if (record != null) {
            record.binder = binder
            try {
                record.connection.connected(binder)
                Log.i(TAG, "Shizuku 用户服务已连接: token=$token")
            } catch (e: RemoteException) {
                Log.w(TAG, "通知用户服务连接失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "ShizukuUserService"
    }
}