package roro.stellar.shizuku.server

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import com.stellar.server.IStellarService
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IShizukuServiceConnection

class ShizukuServiceIntercept(
    private val callback: ShizukuServiceCallback,
    managerApkPathProvider: () -> String,
    packageInfoProvider: (packageName: String, flags: Long, userId: Int) -> PackageInfo?
) : IShizukuService.Stub() {

    private val stellarService: IStellarService
        get() = callback.getStellarService()

    private val userServiceManager = ShizukuUserServiceManager(managerApkPathProvider, packageInfoProvider)

    private val clientApiVersions = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    private data class ShizukuClientRecord(
        val uid: Int,
        val pid: Int,
        val packageName: String,
        val application: IShizukuApplication
    )

    private val shizukuClients = java.util.concurrent.ConcurrentHashMap<Int, ShizukuClientRecord>()

    private val managerAppId: Int
        get() = callback.getManagerAppId()

    private inline fun <T> withClearedIdentity(block: () -> T): T {
        val id = Binder.clearCallingIdentity()
        try {
            return block()
        } finally {
            Binder.restoreCallingIdentity(id)
        }
    }

    private fun getAppId(uid: Int): Int = uid % 100000
    private fun getUserId(uid: Int): Int = uid / 100000

    private fun checkCallerManagerPermission(callingUid: Int): Boolean {
        return getAppId(callingUid) == managerAppId
    }


    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return withClearedIdentity { stellarService.version }
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return withClearedIdentity { stellarService.uid }
    }

    override fun checkPermission(permission: String?): Int {
        enforceCallingPermission("checkPermission")
        return withClearedIdentity { stellarService.checkPermission(permission) }
    }

    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")
        return withClearedIdentity {
            stellarService.seLinuxContext ?: throw IllegalStateException("无法获取 SELinux 上下文")
        }
    }


    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")
        return withClearedIdentity { stellarService.getSystemProperty(name, defaultValue) }
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")
        withClearedIdentity { stellarService.setSystemProperty(name, value) }
    }


    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        enforceCallingPermission("newProcess")
        Log.d(TAG, "newProcess: uid=${getCallingUid()}, cmd=${cmd?.contentToString()}")
        val stellarProcess = withClearedIdentity { stellarService.newProcess(cmd, env, dir) }
        return StellarRemoteProcessAdapter(stellarProcess)
    }


    override fun checkSelfPermission(): Boolean {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        val serviceUid = withClearedIdentity { stellarService.uid }
        if (callingUid == serviceUid || callingPid == callback.getServicePid()) {
            return true
        }

        if (checkCallerManagerPermission(callingUid)) {
            return true
        }

        return callback.checkShizukuPermission(callingUid) == FLAG_GRANTED
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = getCallingUid()
        return callback.checkShizukuPermission(callingUid) == FLAG_DENIED
    }

    override fun requestPermission(requestCode: Int) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        val serviceUid = withClearedIdentity { stellarService.uid }
        if (callingUid == serviceUid || callingPid == callback.getServicePid()) {
            return
        }

        Log.i(TAG, "requestPermission: uid=$callingUid, pid=$callingPid, requestCode=$requestCode")

        val currentFlag = callback.checkShizukuPermission(callingUid)
        Log.i(TAG, "requestPermission: currentFlag=$currentFlag")

        when (currentFlag) {
            FLAG_GRANTED -> {
                Log.i(TAG, "requestPermission: 权限已授予")
                callback.dispatchPermissionResult(callingUid, requestCode, true)
            }
            FLAG_DENIED -> {
                Log.i(TAG, "requestPermission: 权限已拒绝")
                callback.dispatchPermissionResult(callingUid, requestCode, false)
            }
            else -> {
                val packages = callback.getPackagesForUid(callingUid)
                val packageName = packages.firstOrNull()
                if (packageName == null) {
                    Log.w(TAG, "requestPermission: 无法获取 uid $callingUid 的包名")
                    callback.dispatchPermissionResult(callingUid, requestCode, false)
                    return
                }

                Log.i(TAG, "requestPermission: 显示权限确认对话框, packageName=$packageName")
                val userId = getUserId(callingUid)
                callback.showPermissionConfirmation(requestCode, callingUid, callingPid, userId, packageName)
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

        val callingPid = getCallingPid()
        val callingUid = getCallingUid()

        val apiVersion = args.getInt(ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION, 13)
        clientApiVersions[callingUid] = apiVersion

        val packages = callback.getPackagesForUid(callingUid)
        if (!packages.contains(requestPackageName)) {
            Log.w(TAG, "请求包 $requestPackageName 不属于 uid $callingUid")
            throw SecurityException("请求包 $requestPackageName 不属于 uid $callingUid")
        }

        shizukuClients[callingUid] = ShizukuClientRecord(callingUid, callingPid, requestPackageName, application)

        val allowed = callback.checkShizukuPermission(callingUid) == FLAG_GRANTED

        Log.i(TAG, "Shizuku attachApplication: $requestPackageName $callingUid $callingPid, allowed=$allowed")

        val reply = Bundle()
        withClearedIdentity {
            reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_UID, stellarService.uid)
            reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_VERSION, stellarService.version)
            reply.putString(ShizukuApiConstants.BIND_APPLICATION_SERVER_SECONTEXT, stellarService.seLinuxContext)
        }
        reply.putInt(ShizukuApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION)
        reply.putBoolean(ShizukuApiConstants.BIND_APPLICATION_PERMISSION_GRANTED, allowed)
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
        val callingUid = getCallingUid()
        val apiVersion = clientApiVersions[callingUid] ?: 13
        return userServiceManager.addUserService(conn, args, apiVersion)
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
        withClearedIdentity { stellarService.exit() }
    }

    override fun dispatchPackageChanged(intent: Intent?) {
    }

    override fun isHidden(uid: Int): Boolean {
        return false
    }

    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle?
    ) { r
        withClearedIdentity {
            stellarService.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        }
    }


    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        if (getAppId(getCallingUid()) != managerAppId) {
            return 0
        }
        val flag = callback.checkShizukuPermission(uid)
        return when (flag) {
            FLAG_GRANTED -> ShizukuApiConstants.FLAG_ALLOWED
            FLAG_DENIED -> ShizukuApiConstants.FLAG_DENIED
            else -> 0
        } and mask
    }

    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        if (getAppId(getCallingUid()) != managerAppId) {
            return
        }
        val newFlag = when {
            (value and ShizukuApiConstants.FLAG_ALLOWED) != 0 -> FLAG_GRANTED
            (value and ShizukuApiConstants.FLAG_DENIED) != 0 -> FLAG_DENIED
            else -> FLAG_ASK
        }
        callback.updateShizukuPermission(uid, newFlag)
    }


    private fun enforceManagerPermission(func: String) {
        val callingUid = getCallingUid()
        if (!checkCallerManagerPermission(callingUid)) {
            throw SecurityException("Permission Denial: $func requires manager permission")
        }
    }

    private fun enforceCallingPermission(func: String) {
        val callingUid = getCallingUid()

        val serviceUid = withClearedIdentity { stellarService.uid }
        if (callingUid == serviceUid) return
        if (checkCallerManagerPermission(callingUid)) return

        if (callback.checkShizukuPermission(callingUid) != FLAG_GRANTED) {
            throw SecurityException("Permission Denial: $func from pid=${getCallingPid()} requires permission")
        }
    }


    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        Log.d(TAG, "onTransact: code=$code, callingUid=${getCallingUid()}, callingPid=${getCallingPid()}")

        if (code == ShizukuApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        } else if (code == 18) {
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

    fun notifyPermissionResult(uid: Int, requestCode: Int, allowed: Boolean) {
        Log.i(TAG, "notifyPermissionResult: uid=$uid, requestCode=$requestCode, allowed=$allowed")

        val client = shizukuClients[uid]
        if (client != null) {
            try {
                client.application.dispatchRequestPermissionResult(requestCode, Bundle().apply {
                    putBoolean(ShizukuApiConstants.REQUEST_PERMISSION_RESULT_ALLOWED, allowed)
                })
                Log.i(TAG, "notifyPermissionResult: 已通知客户端 ${client.packageName}")
            } catch (e: Throwable) {
                Log.w(TAG, "notifyPermissionResult: 通知失败", e)
            }
        } else {
            Log.w(TAG, "notifyPermissionResult: 未找到 uid $uid 的客户端")
        }
    }

    companion object {
        private const val TAG = "ShizukuIntercept"

        const val SHIZUKU_PERMISSION = "shizuku"

        const val FLAG_ASK = 0
        const val FLAG_GRANTED = 1
        const val FLAG_DENIED = 2
    }
}
