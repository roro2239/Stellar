package roro.stellar.shizuku.server

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection
import java.io.File
import java.io.IOException

/**
 * Shizuku 服务拦截器
 * 实现 IShizukuService 接口，将 Shizuku API 调用转发到 Stellar 服务
 */
class ShizukuServiceIntercept(
    private val callback: ShizukuServiceCallback,
    private val pfdUtil: ParcelFileDescriptorUtil,
    private val packagesForUidProvider: (Int) -> List<String>
) : IShizukuService.Stub() {

    private val shizukuConfigManager = ShizukuConfigManager()
    private val clientManager = ShizukuClientManager(shizukuConfigManager)
    private val userServiceManager = ShizukuUserServiceManager()

    private val managerAppId: Int
        get() = callback.getManagerAppId()

    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return ShizukuApiConstants.SERVER_VERSION
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return callback.getServiceUid()
    }

    override fun checkPermission(permission: String?): Int {
        enforceCallingPermission("checkPermission")
        return if (callback.getServiceUid() == 0) {
            PackageManager.PERMISSION_GRANTED
        } else {
            PackageManager.PERMISSION_DENIED
        }
    }

    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")
        return callback.getSELinuxContext()
            ?: throw IllegalStateException("无法获取 SELinux 上下文")
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, name, defaultValue) as String
        } catch (e: Throwable) {
            throw IllegalStateException(e.message)
        }
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("set", String::class.java, String::class.java)
            method.invoke(null, name, value)
        } catch (e: Throwable) {
            throw IllegalStateException(e.message)
        }
    }

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        enforceCallingPermission("newProcess")
        Log.d(TAG, "newProcess: uid=${getCallingUid()}, cmd=${cmd?.contentToString()}")

        val process: Process = try {
            Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
        } catch (e: IOException) {
            throw IllegalStateException(e.message)
        }

        val clientRecord = clientManager.findClient(getCallingUid(), getCallingPid())
        val token = clientRecord?.client?.asBinder()

        return ShizukuRemoteProcessHolder(process, token, pfdUtil)
    }

    override fun checkSelfPermission(): Boolean {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingUid == callback.getServiceUid() || callingPid == callback.getServicePid()) {
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
        val userId = callingUid / 100000

        if (callingUid == callback.getServiceUid() || callingPid == callback.getServicePid()) {
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
                callback.showPermissionConfirmation(
                    requestCode, callingUid, callingPid, userId, clientRecord.packageName
                )
            }
        }
    }

    override fun attachApplication(application: IShizukuApplication?, args: Bundle?) {
        Log.d(TAG, "attachApplication: application=$application, args=$args")
        if (application == null || args == null) {
            Log.w(TAG, "attachApplication: application 或 args 为 null")
            return
        }

        args.classLoader = this.javaClass.classLoader

        val requestPackageName = args.getString(ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME)
        Log.d(TAG, "attachApplication: requestPackageName=$requestPackageName")
        if (requestPackageName == null) {
            Log.w(TAG, "attachApplication: requestPackageName 为 null")
            return
        }
        val apiVersion = args.getInt(ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION, -1)

        val callingPid = getCallingPid()
        val callingUid = getCallingUid()

        val packages = packagesForUidProvider(callingUid)
        if (!packages.contains(requestPackageName)) {
            Log.w(TAG, "请求包 $requestPackageName 不属于 uid $callingUid")
            throw SecurityException("请求包 $requestPackageName 不属于 uid $callingUid")
        }

        var clientRecord = clientManager.findClient(callingUid, callingPid)
        if (clientRecord == null) {
            Log.i(TAG, "创建 Shizuku 客户端: uid=$callingUid, pid=$callingPid, package=$requestPackageName")
            clientRecord = clientManager.addClient(callingUid, callingPid, application, requestPackageName, apiVersion)
        }

        if (clientRecord == null) {
            Log.w(TAG, "添加 Shizuku 客户端失败")
            return
        }

        Log.i(TAG, "Shizuku attachApplication: $requestPackageName $callingUid $callingPid")

        val reply = Bundle()
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_UID, callback.getServiceUid())
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_VERSION, ShizukuApiConstants.SERVER_VERSION)
        reply.putString(ShizukuApiConstants.BIND_APPLICATION_SERVER_SECONTEXT, callback.getSELinuxContext())
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION)
        reply.putBoolean(ShizukuApiConstants.BIND_APPLICATION_PERMISSION_GRANTED, clientRecord.allowed)
        reply.putBoolean(ShizukuApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false)

        try {
            application.bindApplication(reply)
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku bindApplication 失败", e)
        }
    }

    override fun addUserService(conn: IShizukuServiceConnection?, args: Bundle?): Int {
        enforceCallingPermission("addUserService")
        if (conn == null || args == null) return 1
        return userServiceManager.addUserService(getCallingUid(), getCallingPid(), conn, args)
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
        Log.i(TAG, "Shizuku exit called")
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
        if (getAppId(getCallingUid()) != managerAppId) {
            Log.w(TAG, "dispatchPermissionConfirmationResult 不是从管理器调用的")
            return
        }
        if (data == null) return

        val allowed = data.getBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED)
        val onetime = data.getBoolean(ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME)

        handlePermissionResultInternal(requestUid, requestPid, requestCode, allowed, onetime)
    }

    /**
     * 供外部调用的权限确认结果处理方法
     */
    fun handlePermissionResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean
    ): Boolean {
        return handlePermissionResultInternal(requestUid, requestPid, requestCode, allowed, onetime)
    }

    private fun handlePermissionResultInternal(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean
    ): Boolean {
        val records = clientManager.findClients(requestUid)
        if (records.isEmpty()) return false

        for (record in records) {
            record.allowed = allowed
            if (record.pid == requestPid) {
                record.dispatchRequestPermissionResult(requestCode, allowed, onetime)
            }
        }

        val packageName = records.firstOrNull()?.packageName ?: return false

        shizukuConfigManager.updateFlag(
            requestUid, packageName,
            if (onetime) ShizukuConfigManager.FLAG_ASK
            else if (allowed) ShizukuConfigManager.FLAG_GRANTED
            else ShizukuConfigManager.FLAG_DENIED
        )

        Log.i(TAG, "handlePermissionResult: uid=$requestUid, allowed=$allowed, package=$packageName")
        return true
    }

    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        if (getAppId(getCallingUid()) != managerAppId) {
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
        if (getAppId(getCallingUid()) != managerAppId) {
            return
        }
        val newFlag = when {
            (value and ShizukuApiConstants.FLAG_ALLOWED) != 0 -> ShizukuConfigManager.FLAG_GRANTED
            (value and ShizukuApiConstants.FLAG_DENIED) != 0 -> ShizukuConfigManager.FLAG_DENIED
            else -> ShizukuConfigManager.FLAG_ASK
        }

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

    fun getShizukuFlagForUid(uid: Int): Int {
        return shizukuConfigManager.getFlag(uid)
    }

    fun updateShizukuFlagForUid(uid: Int, packageName: String, flag: Int) {
        shizukuConfigManager.updateFlag(uid, packageName, flag)
        val records = clientManager.findClients(uid)
        for (record in records) {
            record.allowed = flag == ShizukuConfigManager.FLAG_GRANTED
        }
    }

    private fun getAppId(uid: Int): Int = uid % 100000

    private fun checkCallerManagerPermission(callingUid: Int): Boolean {
        return getAppId(callingUid) == managerAppId
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

        if (callingUid == callback.getServiceUid()) return
        if (checkCallerManagerPermission(callingUid)) return

        val clientRecord = clientManager.findClient(callingUid, callingPid)
        if (clientRecord == null) {
            throw SecurityException("Permission Denial: $func - not an attached client")
        }
        if (!clientRecord.allowed) {
            throw SecurityException("Permission Denial: $func requires permission")
        }
    }

    // ============ Binder 事务处理 ============

    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        Log.d(TAG, "onTransact: code=$code, callingUid=${getCallingUid()}, callingPid=${getCallingPid()}")

        if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        } else if (code == 18) {
            // V13 版本的 attachApplication
            try {
                data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
                val binder = data.readStrongBinder()
                val hasArgs = data.readInt() != 0
                val args = if (hasArgs) Bundle.CREATOR.createFromParcel(data) else Bundle()
                attachApplication(IShizukuApplication.Stub.asInterface(binder), args)
                reply?.writeNoException()
            } catch (e: Exception) {
                Log.e(TAG, "attachApplication V13 失败", e)
                reply?.writeException(e)
            }
            return true
        } else if (code == 14) {
            // 旧版本 (v12 及以下) 的 attachApplication
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

        Log.d(TAG, "transactRemote: uid=${getCallingUid()}, code=$targetCode")

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
        private const val TAG = "ShizukuIntercept"
    }
}
