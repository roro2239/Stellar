package roro.stellar.server

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import com.stellar.server.IRemoteProcess
import com.stellar.server.IStellarApplication
import com.stellar.server.IStellarService
import com.stellar.server.IUserServiceCallback
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.BinderSender.register
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.binder.BinderDistributor
import roro.stellar.server.bootstrap.ServerBootstrap
import roro.stellar.server.command.FollowCommandExecutor
import roro.stellar.server.communication.CallerContext
import roro.stellar.server.communication.PermissionEnforcer
import roro.stellar.server.communication.StellarCommunicationBridge
import roro.stellar.server.ext.FollowStellarStartupExt
import roro.stellar.server.grant.ManagerGrantHelper
import roro.stellar.server.ktx.mainHandler
import roro.stellar.server.monitor.PackageMonitor
import roro.stellar.server.query.ApplicationQueryHelper
import roro.stellar.server.service.StellarServiceCore
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.shizuku.ShizukuCallbackFactory
import roro.stellar.server.shizuku.ShizukuServiceIntercept
import roro.stellar.server.userservice.UserServiceManager
import roro.stellar.server.util.Logger
import kotlin.system.exitProcess

class StellarService : IStellarService.Stub() {

    private val clientManager: ClientManager
    private val configManager: ConfigManager
    private val userServiceManager: UserServiceManager
    private val managerAppId: Int

    private val serviceCore: StellarServiceCore
    internal val permissionEnforcer: PermissionEnforcer
    private val bridge: StellarCommunicationBridge

    internal val shizukuServiceIntercept: ShizukuServiceIntercept

    init {
        try {
            LOGGER.i("正在启动 Stellar 服务...")

            LOGGER.i("等待系统服务...")
            ServerBootstrap.waitSystemService("package")
            ServerBootstrap.waitSystemService(Context.ACTIVITY_SERVICE)
            ServerBootstrap.waitSystemService(Context.USER_SERVICE)
            ServerBootstrap.waitSystemService(Context.APP_OPS_SERVICE)

            LOGGER.i("获取管理器应用信息...")
            val ai = ServerBootstrap.managerApplicationInfo
            if (ai == null) {
                LOGGER.e("无法获取管理器应用信息")
                exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
            }

            managerAppId = ai.uid
            LOGGER.i("管理器应用 UID: $managerAppId")

            LOGGER.i("初始化配置管理器...")
            configManager = ConfigManager()
            clientManager = ClientManager(configManager)
            userServiceManager = UserServiceManager()

            LOGGER.i("初始化服务核心...")
            serviceCore = StellarServiceCore(clientManager, configManager, userServiceManager)
            permissionEnforcer = PermissionEnforcer(clientManager, configManager, managerAppId)
            bridge = StellarCommunicationBridge(serviceCore, permissionEnforcer)

            LOGGER.i("初始化 Shizuku 兼容层...")
            shizukuServiceIntercept = ShizukuServiceIntercept(
                ShizukuCallbackFactory.create(
                    clientManager, configManager, userServiceManager,
                    managerAppId, serviceCore,
                    shizukuNotifier = { uid, pid, requestCode, allowed ->
                        shizukuServiceIntercept.notifyPermissionResult(uid, pid, requestCode, allowed)
                    }
                )
            )

            LOGGER.i("启动文件监听...")
            ApkChangedObservers.start(ai.sourceDir) {
                LOGGER.w("检测到管理器应用文件变化，检查应用状态...")
                if (ServerBootstrap.managerApplicationInfo == null) {
                    LOGGER.w("用户 0 中的管理器应用已卸载，正在退出...")
                    exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                } else {
                    LOGGER.i("管理器应用仍然存在，继续运行")
                }
            }

            LOGGER.i("注册包移除监听...")
            PackageMonitor.registerPackageRemovedReceiver(ai)

            LOGGER.i("注册 Binder...")
            register(this)

            LOGGER.i("发送 Binder 到客户端...")
            mainHandler.post {
                try {
                    BinderDistributor.sendBinderToAllClients(this)
                    BinderDistributor.sendBinderToManager(this)
                    ManagerGrantHelper.grantWriteSecureSettings(managerAppId)
                    ManagerGrantHelper.grantAccessibilityService()
                    FollowStellarStartupExt.schedule(configManager)
                    FollowCommandExecutor.execute()
                    LOGGER.i("Stellar 服务启动完成")
                } catch (e: Throwable) {
                    LOGGER.e(e, "发送 Binder 失败")
                }
            }
        } catch (e: Throwable) {
            LOGGER.e(e, "Stellar 服务初始化失败")
            exitProcess(1)
        }
    }

    override fun getVersion(): Int {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetVersion(caller)
    }

    override fun getUid(): Int {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetUid(caller)
    }

    override fun getSELinuxContext(): String? {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetSELinuxContext(caller)
    }

    override fun getVersionName(): String? {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetVersionName(caller)
    }

    override fun getVersionCode(): Int {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetVersionCode(caller)
    }

    override fun checkSelfPermission(permission: String?): Boolean {
        if (permission == null) return false
        val caller = CallerContext.fromBinder()
        return bridge.handleCheckSelfPermission(caller, permission)
    }

    override fun requestPermission(permission: String, requestCode: Int) {
        val caller = CallerContext.fromBinder()
        bridge.handleRequestPermission(caller, permission, requestCode)
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val caller = CallerContext.fromBinder()
        return bridge.handleShouldShowRequestPermissionRationale(caller)
    }

    override fun getSupportedPermissions(): Array<String> {
        return bridge.handleGetSupportedPermissions()
    }

    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle?
    ) {
        if (data == null) return

        val permission = data.getString(StellarApiConstants.REQUEST_PERMISSION_REPLY_PERMISSION, "stellar")
        if (permission == ShizukuApiConstants.PERMISSION_NAME) {
            val allowed = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, false)
            val onetime = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, false)

            LOGGER.i("Shizuku 权限结果: uid=$requestUid, pid=$requestPid, code=$requestCode, allowed=$allowed, onetime=$onetime")

            val record = clientManager.findClient(requestUid, requestPid)
            if (onetime) {
                record?.onetimeMap?.set(ShizukuApiConstants.PERMISSION_NAME, allowed)
            } else {
                val newFlag = if (allowed) ConfigManager.FLAG_GRANTED else ConfigManager.FLAG_DENIED
                configManager.updatePermission(requestUid, ShizukuApiConstants.PERMISSION_NAME, newFlag)
                clientManager.findClients(requestUid).forEach { it.onetimeMap.remove(ShizukuApiConstants.PERMISSION_NAME) }
            }

            if (!allowed) {
                record?.lastDenyTimeMap?.set(ShizukuApiConstants.PERMISSION_NAME, System.currentTimeMillis())
            }

            shizukuServiceIntercept.notifyPermissionResult(requestUid, requestPid, requestCode, allowed)
            return
        }

        val caller = CallerContext.fromBinder()
        bridge.handleDispatchPermissionConfirmationResult(caller, requestUid, requestPid, requestCode, data)
    }

    override fun getFlagForUid(uid: Int, permission: String): Int {
        if (permission == ShizukuApiConstants.PERMISSION_NAME) {
            val stellarFlag = configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)
            return ShizukuApiConstants.stellarToShizukuFlag(stellarFlag)
        }

        val caller = CallerContext.fromBinder()
        return bridge.handleGetFlagForUid(caller, uid, permission)
    }

    override fun updateFlagForUid(uid: Int, permission: String, flag: Int) {
        if (permission == ShizukuApiConstants.PERMISSION_NAME) {
            val stellarFlag = ShizukuApiConstants.shizukuToStellarFlag(flag)
            configManager.updatePermission(uid, ShizukuApiConstants.PERMISSION_NAME, stellarFlag)
            clientManager.findClients(uid).forEach { it.onetimeMap.remove(ShizukuApiConstants.PERMISSION_NAME) }
            return
        }

        val caller = CallerContext.fromBinder()
        bridge.handleUpdateFlagForUid(caller, uid, permission, flag)
    }

    override fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        val caller = CallerContext.fromBinder()
        bridge.handleGrantRuntimePermission(caller, packageName, permissionName, userId)
    }

    override fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        val caller = CallerContext.fromBinder()
        bridge.handleRevokeRuntimePermission(caller, packageName, permissionName, userId)
    }

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        val caller = CallerContext.fromBinder()
        return bridge.handleNewProcess(caller, cmd ?: emptyArray(), env, dir)
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetSystemProperty(caller, name ?: "", defaultValue ?: "")
    }

    override fun setSystemProperty(name: String?, value: String?) {
        val caller = CallerContext.fromBinder()
        bridge.handleSetSystemProperty(caller, name ?: "", value ?: "")
    }

    override fun startUserService(args: Bundle?, callback: IUserServiceCallback?): String? {
        val caller = CallerContext.fromBinder()
        return bridge.handleStartUserService(caller, args, callback)
    }

    override fun stopUserService(token: String?) {
        val caller = CallerContext.fromBinder()
        bridge.handleStopUserService(caller, token)
    }

    override fun attachUserService(binder: IBinder?, options: Bundle?) {
        bridge.handleAttachUserService(binder, options)
    }

    override fun getUserServiceCount(): Int {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetUserServiceCount(caller)
    }

    override fun getLogs(): List<String> {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetLogs(caller)
    }

    override fun clearLogs() {
        val caller = CallerContext.fromBinder()
        bridge.handleClearLogs(caller)
    }

    override fun isShizukuCompatEnabled(): Boolean {
        val caller = CallerContext.fromBinder()
        permissionEnforcer.enforceManager(caller, "isShizukuCompatEnabled")
        return configManager.isShizukuCompatEnabled()
    }

    override fun setShizukuCompatEnabled(enabled: Boolean) {
        val caller = CallerContext.fromBinder()
        permissionEnforcer.enforceManager(caller, "setShizukuCompatEnabled")
        configManager.setShizukuCompatEnabled(enabled)
        LOGGER.i("Shizuku 兼容层已%s", if (enabled) "启用" else "禁用")
    }

    override fun exit() {
        val caller = CallerContext.fromBinder()
        permissionEnforcer.enforceManager(caller, "exit")
        LOGGER.i("exit")
        exitProcess(0)
    }

    override fun attachApplication(application: IStellarApplication?, args: Bundle?) {
        if (application == null || args == null) {
            return
        }

        val requestPackageName =
            args.getString(StellarApiConstants.ATTACH_APPLICATION_PACKAGE_NAME) ?: return
        val apiVersion = args.getInt(StellarApiConstants.ATTACH_APPLICATION_API_VERSION, -1)

        val callingPid = getCallingPid()
        val callingUid = getCallingUid()
        val isManager: Boolean = MANAGER_APPLICATION_ID == requestPackageName
        var clientRecord: ClientRecord? = null

        val packages = PackageManagerApis.getPackagesForUidNoThrow(callingUid)
        if (!packages.contains(requestPackageName)) {
            LOGGER.w("请求包 $requestPackageName 不属于 uid $callingUid")
            throw SecurityException("请求包 $requestPackageName 不属于 uid $callingUid")
        }

        val existingClient = clientManager.findClient(callingUid, callingPid)
        if (existingClient == null) {
            LOGGER.i("创建新客户端记录: uid=%d, pid=%d, package=%s", callingUid, callingPid, requestPackageName)
            synchronized(this) {
                clientRecord = clientManager.addClient(
                    callingUid,
                    callingPid,
                    application,
                    requestPackageName,
                    apiVersion
                )
            }
            if (clientRecord == null) {
                LOGGER.w("添加客户端失败")
                return
            }
        } else {
            LOGGER.i("使用已存在的客户端记录: uid=%d, pid=%d, package=%s", callingUid, callingPid, requestPackageName)
            clientRecord = existingClient
        }

        if (isManager) {
            try {
                application.asBinder().linkToDeath({
                    LOGGER.i("管理器进程已死亡，正在授予无障碍服务权限...")
                    ManagerGrantHelper.grantAccessibilityService()
                }, 0)
            } catch (e: Throwable) {
                LOGGER.w(e, "监控管理器进程死亡失败")
            }
        }

        LOGGER.i("attachApplication 完成: %s %d %d", requestPackageName, callingUid, callingPid)

        val reply = Bundle()
        reply.putInt(StellarApiConstants.BIND_APPLICATION_SERVER_UID, serviceCore.serviceInfo.getUid())
        reply.putInt(
            StellarApiConstants.BIND_APPLICATION_SERVER_VERSION,
            StellarApiConstants.SERVER_VERSION
        )
        reply.putString(
            StellarApiConstants.BIND_APPLICATION_SERVER_SECONTEXT,
            serviceCore.serviceInfo.getSELinuxContext()
        )
        reply.putInt(
            StellarApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION,
            StellarApiConstants.SERVER_PATCH_VERSION
        )
        if (!isManager) {
            val permissionGranted = clientRecord?.allowedMap["stellar"] ?: false
            LOGGER.i("权限状态检查: uid=%d, pid=%d, package=%s, granted=%s, allowedMap=%s",
                callingUid, callingPid, requestPackageName, permissionGranted,
                clientRecord?.allowedMap?.toString() ?: "null")
            reply.putBoolean(
                StellarApiConstants.BIND_APPLICATION_PERMISSION_GRANTED,
                permissionGranted
            )
            reply.putBoolean(
                StellarApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE,
                false
            )
        }
        try {
            application.bindApplication(reply)
        } catch (e: Throwable) {
            LOGGER.w(e, "attachApplication")
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == ServerConstants.BINDER_TRANSACTION_getApplications) {
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR)
            val userId = data.readInt()
            val result = ApplicationQueryHelper.getApplications(userId, configManager)
            reply?.let {
                it.writeNoException()
                result.writeToParcel(it, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }
            return true
        } else if (code == StellarApiConstants.BINDER_TRANSACTION_transact) {
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        val caller = CallerContext.fromBinder()
        permissionEnforcer.enforcePermission(caller, "transactRemote")

        val targetBinder = data.readStrongBinder()
        val targetCode = data.readInt()
        val targetFlags: Int

        val clientRecord = clientManager.findClient(caller.uid, caller.pid)

        targetFlags = if (clientRecord != null && clientRecord.apiVersion >= 13) {
            data.readInt()
        } else {
            flags
        }

        LOGGER.d(
            "transact: uid=%d, descriptor=%s, code=%d",
            caller.uid,
            targetBinder.interfaceDescriptor,
            targetCode
        )

        val newData = Parcel.obtain()
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail())
        } catch (tr: Throwable) {
            LOGGER.w(tr, "appendFrom")
            return
        }
        try {
            val id = clearCallingIdentity()
            targetBinder.transact(targetCode, newData, reply, targetFlags)
            restoreCallingIdentity(id)
        } finally {
            newData.recycle()
        }
    }

    override fun checkPermission(permission: String?): Int {
        val caller = CallerContext.fromBinder()
        permissionEnforcer.enforcePermission(caller, "checkPermission")
        return rikka.hidden.compat.PermissionManagerApis.checkPermission(permission, serviceCore.serviceInfo.getUid())
    }

    companion object {
        private val LOGGER = Logger("StellarService")

        @JvmStatic
        fun main(args: Array<String>) {
            ServerBootstrap.main(args)
        }
    }
}
