package roro.stellar.server.shizuku

import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder
import com.stellar.server.IUserServiceCallback
import moe.shizuku.server.IShizukuServiceConnection
import roro.stellar.server.userservice.UserServiceConstants
import roro.stellar.server.userservice.UserServiceManager
import roro.stellar.server.util.Logger
import java.util.concurrent.ConcurrentHashMap

class ShizukuUserServiceAdapter(
    private val userServiceManager: UserServiceManager
) {
    companion object {
        private val LOGGER = Logger("ShizukuUserServiceAdapter")
    }

    private val records = ConcurrentHashMap<String, ShizukuUserServiceRecord>()

    private val tokenToKey = ConcurrentHashMap<String, String>()

    fun addUserService(
        conn: IShizukuServiceConnection,
        options: Bundle,
        callingUid: Int,
        callingPid: Int
    ): Int {
        val componentName = options.getParcelable<ComponentName>(ShizukuApiConstants.UserServiceArgs.COMPONENT)
            ?: throw IllegalArgumentException("component is null")

        val packageName = componentName.packageName
        val className = componentName.className
        val tag = options.getString(ShizukuApiConstants.UserServiceArgs.TAG)
        val versionCode = options.getInt(ShizukuApiConstants.UserServiceArgs.VERSION_CODE, 1)
        val daemon = options.getBoolean(ShizukuApiConstants.UserServiceArgs.DAEMON, true)
        val debug = options.getBoolean(ShizukuApiConstants.UserServiceArgs.DEBUGGABLE, false)
        val use32Bit = options.getBoolean(ShizukuApiConstants.UserServiceArgs.USE_32_BIT, false)
        val noCreate = options.getBoolean(ShizukuApiConstants.UserServiceArgs.NO_CREATE, false)
        val processNameSuffix = options.getString(ShizukuApiConstants.UserServiceArgs.PROCESS_NAME) ?: "p"

        val key = "$packageName:${tag ?: className}"
        LOGGER.i("addUserService: key=$key, daemon=$daemon, noCreate=$noCreate")

        val existingRecord = records[key]

        if (noCreate) {
            val binder = existingRecord?.serviceBinder
            if (binder == null || !binder.pingBinder()) {
                return -1
            }
            existingRecord.callbacks.register(conn)
            try {
                conn.connected(binder)
            } catch (e: Exception) {
                LOGGER.w(e, "通知已存在服务失败")
            }
            return 0
        }

        if (existingRecord != null) {
            existingRecord.callbacks.register(conn)
            val binder = existingRecord.serviceBinder
            if (binder != null && binder.pingBinder()) {
                try {
                    conn.connected(binder)
                } catch (e: Exception) {
                    LOGGER.w(e, "通知已存在服务失败")
                }
                return 0
            }
        }

        val stellarArgs = Bundle().apply {
            putString(UserServiceConstants.ARG_PACKAGE_NAME, packageName)
            putString(UserServiceConstants.ARG_CLASS_NAME, className)
            putString(UserServiceConstants.ARG_PROCESS_NAME_SUFFIX, processNameSuffix)
            putBoolean(UserServiceConstants.ARG_DEBUG, debug)
            putBoolean(UserServiceConstants.ARG_USE_32_BIT, use32Bit)
            putLong(UserServiceConstants.ARG_VERSION_CODE, versionCode.toLong())
            putInt(
                UserServiceConstants.ARG_SERVICE_MODE,
                if (daemon) UserServiceConstants.MODE_DAEMON else UserServiceConstants.MODE_ONE_TIME
            )
            putString(UserServiceConstants.ARG_VERIFICATION_TOKEN, "")
        }

        val stellarCallback = createStellarCallback(key, conn, daemon)

        val stellarToken = userServiceManager.startUserService(
            callingUid, callingPid, stellarArgs, stellarCallback
        )

        if (stellarToken == null) {
            LOGGER.w("Stellar startUserService 返回 null")
            return -1
        }

        val record = records.getOrPut(key) {
            ShizukuUserServiceRecord(key, stellarToken, daemon)
        }
        record.callbacks.register(conn)
        tokenToKey[stellarToken] = key

        LOGGER.i("addUserService 成功: key=$key, stellarToken=$stellarToken")
        return 0
    }

    fun removeUserService(
        conn: IShizukuServiceConnection,
        options: Bundle
    ): Int {
        val componentName = options.getParcelable<ComponentName>(ShizukuApiConstants.UserServiceArgs.COMPONENT)
            ?: throw IllegalArgumentException("component is null")

        val packageName = componentName.packageName
        val className = componentName.className
        val tag = options.getString(ShizukuApiConstants.UserServiceArgs.TAG)
        val remove = options.getBoolean(ShizukuApiConstants.UserServiceArgs.REMOVE, true)

        val key = "$packageName:${tag ?: className}"

        LOGGER.i("removeUserService: key=$key, remove=$remove")

        val record = records[key] ?: return 1

        if (remove) {
            records.remove(key)
            tokenToKey.remove(record.stellarToken)
            userServiceManager.stopUserService(record.stellarToken)
            record.onServiceDisconnected()
        } else {
            record.callbacks.unregister(conn)
        }

        return 0
    }

    fun onStellarServiceAttached(stellarToken: String, binder: IBinder) {
        val key = tokenToKey[stellarToken] ?: run {
            LOGGER.d("onStellarServiceAttached: 未找到 token=$stellarToken 对应的记录 (可能是 Stellar 原生服务)")
            return
        }

        val record = records[key] ?: run {
            LOGGER.w("onStellarServiceAttached: 未找到 key=$key 对应的记录")
            return
        }

        LOGGER.i("onStellarServiceAttached: key=$key")
        record.onServiceConnected(binder)
    }

    fun onStellarServiceDisconnected(stellarToken: String) {
        val key = tokenToKey[stellarToken] ?: return
        val record = records[key] ?: return

        LOGGER.i("onStellarServiceDisconnected: key=$key")
        record.onServiceDisconnected()

        if (!record.daemon) {
            records.remove(key)
            tokenToKey.remove(stellarToken)
        }
    }

    private fun createStellarCallback(
        key: String,
        conn: IShizukuServiceConnection,
        daemon: Boolean
    ): IUserServiceCallback.Stub {
        return object : IUserServiceCallback.Stub() {
            override fun onServiceConnected(service: IBinder, verificationToken: String?) {
                LOGGER.i("Stellar onServiceConnected: key=$key")
                val record = records[key]
                record?.onServiceConnected(service)
            }

            override fun onServiceDisconnected() {
                LOGGER.i("Stellar onServiceDisconnected: key=$key")
                val record = records[key]
                record?.onServiceDisconnected()
            }

            override fun onServiceStartFailed(errorCode: Int, message: String?) {
                LOGGER.w("Stellar onServiceStartFailed: key=$key, code=$errorCode, msg=$message")
                try {
                    conn.died()
                } catch (e: Exception) {
                    LOGGER.w(e, "通知服务启动失败")
                }
            }
        }
    }
}
