package roro.stellar.server.shizuku

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SELinux
import android.os.SystemProperties
import android.system.Os
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.server.ServerConstants
import roro.stellar.server.StellarService
import roro.stellar.server.util.Logger
import roro.stellar.server.util.OsUtils
import roro.stellar.server.util.UserHandleCompat
import java.io.File
import java.io.IOException

/**
 * Shizuku 服务拦截器
 * 实现 IShizukuService 接口，将 Shizuku API 调用转发到 Stellar 服务
 */
class ShizukuServiceIntercept(
    private val stellarService: StellarService
) : IShizukuService.Stub() {

    private val shizukuConfigManager = ShizukuConfigManager()
    private val clientManager = ShizukuClientManager(shizukuConfigManager)
    private val userServiceManager = ShizukuUserServiceManager()

    private val managerAppId: Int = UserHandleCompat.getAppId(
        PackageManagerApis.getApplicationInfoNoThrow(
            ServerConstants.MANAGER_APPLICATION_ID, 0, 0
        )?.uid ?: -1
    )

    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return ShizukuApiConstants.SERVER_VERSION
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return Os.getuid()
    }

    override fun checkPermission(permission: String?): Int {
        enforceCallingPermission("checkPermission")
        return if (Os.getuid() == 0) {
            PackageManager.PERMISSION_GRANTED
        } else {
            PackageManager.PERMISSION_DENIED
        }
    }

    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")
        return try {
            SELinux.getContext()
        } catch (e: Throwable) {
            throw IllegalStateException(e.message)
        }
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")
        return try {
            SystemProperties.get(name, defaultValue)
        } catch (e: Throwable) {
            throw IllegalStateException(e.message)
        }
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")
        try {
            SystemProperties.set(name, value)
        } catch (e: Throwable) {
            throw IllegalStateException(e.message)
        }
    }

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        enforceCallingPermission("newProcess")
        LOGGER.d("newProcess: uid=%d, cmd=%s", getCallingUid(), cmd?.contentToString())

        val process: Process = try {
            Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
        } catch (e: IOException) {
            throw IllegalStateException(e.message)
        }

        val clientRecord = clientManager.findClient(getCallingUid(), getCallingPid())
        val token = clientRecord?.client?.asBinder()

        return ShizukuRemoteProcessHolder(process, token)
    }

    override fun checkSelfPermission(): Boolean {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingUid == OsUtils.uid || callingPid == OsUtils.pid) {
            return true
        }

        if (checkCallerManagerPermission(callingUid)) {
            return true
        }

        return shizukuConfigManager.getFlag(callingUid) == ShizukuConfigManager.FLAG_GRANTED
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = getCallingUid()
        return shizukuConfigManager.getFlag(callingUid) == ShizukuConfigManager.FLAG_DENIED
    }

    override fun requestPermission(requestCode: Int) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()
        val userId = UserHandleCompat.getUserId(callingUid)

        if (callingUid == OsUtils.uid || callingPid == OsUtils.pid) {
            return
        }

        val clientRecord = clientManager.requireClient(callingUid, callingPid)

        when (shizukuConfigManager.getFlag(callingUid)) {
            ShizukuConfigManager.FLAG_GRANTED -> {
                clientRecord.dispatchRequestPermissionResult(requestCode, true, false)
                return
            }
            ShizukuConfigManager.FLAG_DENIED -> {
                clientRecord.dispatchRequestPermissionResult(requestCode, false, false)
                return
            }
            else -> {
                stellarService.showPermissionConfirmation(
                    requestCode,
                    createStellarClientRecord(clientRecord),
                    callingUid,
                    callingPid,
                    userId,
                    "stellar"
                )
            }
        }
    }

    override fun attachApplication(application: IShizukuApplication?, args: Bundle?) {
        LOGGER.d("attachApplication: application=%s, args=%s", application, args)
        if (application == null || args == null) {
            LOGGER.w("attachApplication: application 或 args 为 null")
            return
        }

        // 设置 ClassLoader 以确保 Bundle 能正确解析
        args.classLoader = this.javaClass.classLoader

        val requestPackageName = args.getString(
            ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME
        )
        LOGGER.d("attachApplication: requestPackageName=%s", requestPackageName)
        if (requestPackageName == null) {
            LOGGER.w("attachApplication: requestPackageName 为 null, args keys=%s", args.keySet())
            return
        }
        val apiVersion = args.getInt(ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION, -1)

        val callingPid = getCallingPid()
        val callingUid = getCallingUid()

        val packages = PackageManagerApis.getPackagesForUidNoThrow(callingUid)
        if (!packages.contains(requestPackageName)) {
            LOGGER.w("请求包 %s 不属于 uid %d", requestPackageName, callingUid)
            throw SecurityException("请求包 $requestPackageName 不属于 uid $callingUid")
        }

        var clientRecord = clientManager.findClient(callingUid, callingPid)
        if (clientRecord == null) {
            LOGGER.i("创建 Shizuku 客户端: uid=%d, pid=%d, package=%s",
                callingUid, callingPid, requestPackageName)
            clientRecord = clientManager.addClient(
                callingUid, callingPid, application, requestPackageName, apiVersion
            )
        }

        if (clientRecord == null) {
            LOGGER.w("添加 Shizuku 客户端失败")
            return
        }

        LOGGER.i("Shizuku attachApplication: %s %d %d", requestPackageName, callingUid, callingPid)

        val reply = Bundle()
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_UID, OsUtils.uid)
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_VERSION, ShizukuApiConstants.SERVER_VERSION)
        reply.putString(ShizukuApiConstants.BIND_APPLICATION_SERVER_SECONTEXT, OsUtils.sELinuxContext)
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION)
        reply.putBoolean(ShizukuApiConstants.BIND_APPLICATION_PERMISSION_GRANTED, clientRecord.allowed)
        reply.putBoolean(ShizukuApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false)

        try {
            application.bindApplication(reply)
        } catch (e: Throwable) {
            LOGGER.w(e, "Shizuku bindApplication 失败")
        }
    }

    override fun addUserService(conn: IShizukuServiceConnection?, args: Bundle?): Int {
        enforceCallingPermission("addUserService")
        if (conn == null || args == null) return 1

        return userServiceManager.addUserService(
            getCallingUid(), getCallingPid(), conn, args
        )
    }

    override fun removeUserService(conn: IShizukuServiceConnection?, args: Bundle?): Int {
        enforceCallingPermission("removeUserService")
        if (conn == null || args == null) return 1

        return userServiceManager.removeUserService(conn, args)
    }

    override fun attachUserService(binder: IBinder?, options: Bundle?) {
        if (binder == null || options == null) return
        userServiceManager.attachUserService(binder, options)
    }

    override fun exit() {
        enforceManagerPermission("exit")
        LOGGER.i("Shizuku exit called")
    }

    override fun dispatchPackageChanged(intent: Intent?) {
        // 包变化通知，暂不处理
    }

    override fun isHidden(uid: Int): Boolean {
        return false
    }

    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle?
    ) {
        if (UserHandleCompat.getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("dispatchPermissionConfirmationResult 不是从管理器调用的")
            return
        }
        if (data == null) return

        val allowed = data.getBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED)
        val onetime = data.getBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME)

        val records = clientManager.findClients(requestUid)
        for (record in records) {
            record.allowed = allowed
            if (record.pid == requestPid) {
                record.dispatchRequestPermissionResult(requestCode, allowed, onetime)
            }
        }

        // 获取包名用于更新配置
        val packageName = records.firstOrNull()?.packageName ?: return

        // 更新 Shizuku 配置
        shizukuConfigManager.updateFlag(
            requestUid, packageName,
            if (onetime) ShizukuConfigManager.FLAG_ASK
            else if (allowed) ShizukuConfigManager.FLAG_GRANTED
            else ShizukuConfigManager.FLAG_DENIED
        )
    }

    /**
     * 供 StellarService 调用的权限确认结果处理方法
     * @return 是否找到并处理了 Shizuku 客户端
     */
    fun handlePermissionResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean
    ): Boolean {
        val records = clientManager.findClients(requestUid)
        if (records.isEmpty()) {
            return false
        }

        for (record in records) {
            record.allowed = allowed
            if (record.pid == requestPid) {
                record.dispatchRequestPermissionResult(requestCode, allowed, onetime)
            }
        }

        // 获取包名用于更新配置
        val packageName = records.firstOrNull()?.packageName ?: return false

        // 更新 Shizuku 配置
        shizukuConfigManager.updateFlag(
            requestUid, packageName,
            if (onetime) ShizukuConfigManager.FLAG_ASK
            else if (allowed) ShizukuConfigManager.FLAG_GRANTED
            else ShizukuConfigManager.FLAG_DENIED
        )

        LOGGER.i("handlePermissionResult: uid=%d, allowed=%s, package=%s",
            requestUid, allowed, packageName)
        return true
    }

    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        if (UserHandleCompat.getAppId(getCallingUid()) != managerAppId) {
            return 0
        }
        val flag = shizukuConfigManager.getFlag(uid)
        return when (flag) {
            ShizukuConfigManager.FLAG_GRANTED -> ShizukuApiConstants.FLAG_ALLOWED
            ShizukuConfigManager.FLAG_DENIED -> ShizukuApiConstants.FLAG_DENIED
            else -> 0
        } and mask
    }

    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        if (UserHandleCompat.getAppId(getCallingUid()) != managerAppId) {
            return
        }
        val newFlag = when {
            (value and ShizukuApiConstants.FLAG_ALLOWED) != 0 -> ShizukuConfigManager.FLAG_GRANTED
            (value and ShizukuApiConstants.FLAG_DENIED) != 0 -> ShizukuConfigManager.FLAG_DENIED
            else -> ShizukuConfigManager.FLAG_ASK
        }

        // 从客户端记录或现有配置获取包名
        val records = clientManager.findClients(uid)
        val packageName = records.firstOrNull()?.packageName
            ?: shizukuConfigManager.find(uid)?.packageName
            ?: return

        shizukuConfigManager.updateFlag(uid, packageName, newFlag)

        for (record in records) {
            record.allowed = newFlag == ShizukuConfigManager.FLAG_GRANTED
        }
    }

    // ============ 辅助方法 ============

    /**
     * 获取 Shizuku 应用的权限标志
     * 供 StellarService 调用
     */
    fun getShizukuFlagForUid(uid: Int): Int {
        return shizukuConfigManager.getFlag(uid)
    }

    /**
     * 更新 Shizuku 应用的权限标志
     * 供 StellarService 调用
     */
    fun updateShizukuFlagForUid(uid: Int, packageName: String, flag: Int) {
        shizukuConfigManager.updateFlag(uid, packageName, flag)

        // 同步更新客户端记录
        val records = clientManager.findClients(uid)
        for (record in records) {
            record.allowed = flag == ShizukuConfigManager.FLAG_GRANTED
        }
    }

    private fun checkCallerManagerPermission(callingUid: Int): Boolean {
        return UserHandleCompat.getAppId(callingUid) == managerAppId
    }

    private fun enforceManagerPermission(func: String) {
        val callingUid = getCallingUid()
        if (!checkCallerManagerPermission(callingUid)) {
            throw SecurityException("Permission Denial: $func requires manager permission")
        }
    }

    private fun enforceCallingPermission(func: String) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingUid == OsUtils.uid) return
        if (checkCallerManagerPermission(callingUid)) return

        val clientRecord = clientManager.findClient(callingUid, callingPid)
        if (clientRecord == null) {
            throw SecurityException("Permission Denial: $func - not an attached client")
        }
        if (!clientRecord.allowed) {
            throw SecurityException("Permission Denial: $func requires permission")
        }
    }

    private fun createStellarClientRecord(
        shizukuRecord: ShizukuClientRecord
    ): roro.stellar.server.ClientRecord {
        val stellarApp = object : com.stellar.server.IStellarApplication.Stub() {
            override fun bindApplication(data: Bundle?) {}
            override fun dispatchRequestPermissionResult(requestCode: Int, data: Bundle?) {
                val allowed = data?.getBoolean(
                    roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, false
                ) ?: false
                val onetime = data?.getBoolean(
                    roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, false
                ) ?: false
                shizukuRecord.dispatchRequestPermissionResult(requestCode, allowed, onetime)
            }
        }
        return roro.stellar.server.ClientRecord(
            shizukuRecord.uid,
            shizukuRecord.pid,
            stellarApp,
            shizukuRecord.packageName,
            shizukuRecord.apiVersion
        )
    }

    // ============ Binder 事务处理 ============

    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        LOGGER.d("onTransact: code=%d, callingUid=%d, callingPid=%d", code, getCallingUid(), getCallingPid())

        if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        } else if (code == 18) {
            // V13 版本的 attachApplication
            try {
                data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
                val binder = data.readStrongBinder()
                LOGGER.d("attachApplication V13: binder=%s", binder)
                val hasArgs = data.readInt() != 0
                LOGGER.d("attachApplication V13: hasArgs=%s", hasArgs)
                val args = if (hasArgs) {
                    Bundle.CREATOR.createFromParcel(data)
                } else {
                    Bundle()
                }
                LOGGER.d("attachApplication V13: args=%s", args)
                attachApplication(IShizukuApplication.Stub.asInterface(binder), args)
                reply?.writeNoException()
            } catch (e: Exception) {
                LOGGER.e(e, "attachApplication V13 失败")
                reply?.writeException(e)
            }
            return true
        } else if (code == 14) {
            // 旧版本 (v12 及以下) 的 attachApplication 兼容处理
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            val binder = data.readStrongBinder()
            val packageName = data.readString()
            val args = Bundle()
            args.putString(ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME, packageName)
            args.putInt(ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION, -1)
            attachApplication(IShizukuApplication.Stub.asInterface(binder), args)
            reply?.writeNoException()
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        enforceCallingPermission("transactRemote")

        val targetBinder = data.readStrongBinder()
        val targetCode = data.readInt()
        val targetFlags = data.readInt()

        LOGGER.d("transactRemote: uid=%d, code=%d", getCallingUid(), targetCode)

        val newData = Parcel.obtain()
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail())
            val id = Binder.clearCallingIdentity()
            targetBinder.transact(targetCode, newData, reply, targetFlags)
            Binder.restoreCallingIdentity(id)
        } finally {
            newData.recycle()
        }
    }

    companion object {
        private val LOGGER = Logger("ShizukuIntercept")
    }
}
