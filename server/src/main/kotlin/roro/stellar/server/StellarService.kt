package roro.stellar.server

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.ddm.DdmHandleAppName
import android.os.Binder
import android.os.Bundle
import android.os.FileObserver
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.ServiceManager
import com.stellar.server.IRemoteProcess
import com.stellar.server.IStellarApplication
import com.stellar.server.IStellarService
import com.stellar.server.IUserServiceCallback
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.UserManagerApis
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.StellarApiConstants
import roro.stellar.server.ApkChangedObservers.start
import roro.stellar.server.BinderSender.register
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.communication.CallerContext
import roro.stellar.server.communication.PermissionEnforcer
import roro.stellar.server.communication.StellarCommunicationBridge
import roro.stellar.server.ext.FollowStellarStartupExt
import roro.stellar.server.ktx.mainHandler
import roro.stellar.server.service.StellarServiceCore
import roro.stellar.server.userservice.UserServiceManager
import roro.stellar.server.util.Logger
import roro.stellar.server.util.UserHandleCompat.getAppId
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.shizuku.ShizukuServiceCallback
import roro.stellar.server.shizuku.ShizukuServiceIntercept
import java.io.File
import kotlin.system.exitProcess

/**
 * Stellar 服务 - 通讯层入口
 * 职责：
 * 1. 接收 AIDL 调用
 * 2. 委托给通讯桥接层处理
 * 3. 管理服务生命周期
 */
class StellarService : IStellarService.Stub() {

    private val clientManager: ClientManager
    private val configManager: ConfigManager
    private val userServiceManager: UserServiceManager
    private val managerAppId: Int

    private val serviceCore: StellarServiceCore
    internal val permissionEnforcer: PermissionEnforcer
    private val bridge: StellarCommunicationBridge

    // Shizuku 兼容层 - 复用 Stellar 客户端管理和权限系统
    private val shizukuServiceIntercept: ShizukuServiceIntercept

    init {
        try {
            LOGGER.i("正在启动 Stellar 服务...")

            LOGGER.i("等待系统服务...")
            waitSystemService("package")
            waitSystemService(Context.ACTIVITY_SERVICE)
            waitSystemService(Context.USER_SERVICE)
            waitSystemService(Context.APP_OPS_SERVICE)

            LOGGER.i("获取管理器应用信息...")
            val ai = managerApplicationInfo
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
            shizukuServiceIntercept = ShizukuServiceIntercept(createShizukuCallback())

            LOGGER.i("启动文件监听...")
            start(ai.sourceDir) {
                LOGGER.w("检测到管理器应用文件变化，检查应用状态...")
                if (managerApplicationInfo == null) {
                    LOGGER.w("用户 0 中的管理器应用已卸载，正在退出...")
                    exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                } else {
                    LOGGER.i("管理器应用仍然存在，继续运行")
                }
            }

            LOGGER.i("注册包移除监听...")
            registerPackageRemovedReceiver(ai)

            LOGGER.i("注册 Binder...")
            register(this)

            LOGGER.i("发送 Binder 到客户端...")
            mainHandler.post {
                try {
                    sendBinderToClient()
                    sendBinderToManager()
                    FollowStellarStartupExt.schedule(configManager)
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

    // ========== AIDL 接口实现 - 服务信息 ==========

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

    // ========== AIDL 接口实现 - 权限管理 ==========

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

        // 检查是否是 Shizuku 权限请求
        val permission = data.getString(StellarApiConstants.REQUEST_PERMISSION_REPLY_PERMISSION, "stellar")
        if (permission == ShizukuApiConstants.PERMISSION_NAME) {
            val allowed = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED, false)
            val onetime = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME, false)

            LOGGER.i("Shizuku 权限结果: uid=$requestUid, pid=$requestPid, code=$requestCode, allowed=$allowed, onetime=$onetime")

            val record = clientManager.findClient(requestUid, requestPid)
            if (onetime) {
                // 一次性权限：设置到 ClientRecord
                record?.onetimeMap?.set(ShizukuApiConstants.PERMISSION_NAME, allowed)
            } else {
                // 持久权限：更新到 ConfigManager
                val newFlag = if (allowed) ConfigManager.FLAG_GRANTED else ConfigManager.FLAG_DENIED
                configManager.updatePermission(requestUid, ShizukuApiConstants.PERMISSION_NAME, newFlag)
                // 清除一次性权限
                clientManager.findClients(requestUid).forEach { it.onetimeMap.remove(ShizukuApiConstants.PERMISSION_NAME) }
            }

            // 记录拒绝时间
            if (!allowed) {
                record?.lastDenyTimeMap?.set(ShizukuApiConstants.PERMISSION_NAME, System.currentTimeMillis())
            }

            // 通知客户端应用
            shizukuServiceIntercept.notifyPermissionResult(requestUid, requestPid, requestCode, allowed)
            return
        }

        val caller = CallerContext.fromBinder()
        bridge.handleDispatchPermissionConfirmationResult(caller, requestUid, requestPid, requestCode, data)
    }

    override fun getFlagForUid(uid: Int, permission: String): Int {
        // Shizuku 权限也统一从 ConfigManager 获取
        if (permission == ShizukuApiConstants.PERMISSION_NAME) {
            val stellarFlag = configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)
            // 转换为 Shizuku 标志格式返回给管理器
            return ShizukuApiConstants.stellarToShizukuFlag(stellarFlag)
        }

        val caller = CallerContext.fromBinder()
        return bridge.handleGetFlagForUid(caller, uid, permission)
    }

    override fun updateFlagForUid(uid: Int, permission: String, flag: Int) {
        // Shizuku 权限也统一更新到 ConfigManager
        if (permission == ShizukuApiConstants.PERMISSION_NAME) {
            // 从 Shizuku 标志转换为 Stellar 标志
            val stellarFlag = ShizukuApiConstants.shizukuToStellarFlag(flag)
            configManager.updatePermission(uid, ShizukuApiConstants.PERMISSION_NAME, stellarFlag)
            // 清除一次性权限
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

    // ========== AIDL 接口实现 - 进程管理 ==========

    override fun newProcess(cmd: Array<String?>?, env: Array<String?>?, dir: String?): IRemoteProcess {
        val caller = CallerContext.fromBinder()
        return bridge.handleNewProcess(caller, cmd ?: emptyArray(), env, dir)
    }

    // ========== AIDL 接口实现 - 系统属性 ==========

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetSystemProperty(caller, name ?: "", defaultValue ?: "")
    }

    override fun setSystemProperty(name: String?, value: String?) {
        val caller = CallerContext.fromBinder()
        bridge.handleSetSystemProperty(caller, name ?: "", value ?: "")
    }

    // ========== AIDL 接口实现 - 用户服务 ==========

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

    // ========== AIDL 接口实现 - 日志管理 ==========

    override fun getLogs(): List<String> {
        val caller = CallerContext.fromBinder()
        return bridge.handleGetLogs(caller)
    }

    override fun clearLogs() {
        val caller = CallerContext.fromBinder()
        bridge.handleClearLogs(caller)
    }

    // ========== AIDL 接口实现 - 服务控制 ==========

    override fun exit() {
        val caller = CallerContext.fromBinder()
        permissionEnforcer.enforceManager(caller, "exit")
        LOGGER.i("exit")
        exitProcess(0)
    }

    // ========== 客户端连接管理 ==========

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

    // ========== 自定义事务处理 ==========

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == ServerConstants.BINDER_TRANSACTION_getApplications) {
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR)
            val userId = data.readInt()
            val result = getApplications(userId)
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

    // ========== 应用列表管理 ==========

    private fun getApplications(userId: Int): ParcelableListSlice<PackageInfo?> {
        val list = ArrayList<PackageInfo?>()
        val users = ArrayList<Int?>()
        if (userId == -1) {
            users.addAll(UserManagerApis.getUserIdsNoThrow())
        } else {
            users.add(userId)
        }

        for (user in users) {
            for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                (PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS).toLong(),
                user!!
            )) {
                // 排除 Stellar 管理器
                if (MANAGER_APPLICATION_ID == pi.packageName) continue
                // 排除 Shizuku 管理器
                if (pi.requestedPermissions?.contains(SHIZUKU_MANAGER_PERMISSION) == true) continue
                val applicationInfo = pi.applicationInfo ?: continue

                val uid = applicationInfo.uid
                var flag = -1

                configManager.find(uid)?.let {
                    if (!it.packages.contains(pi.packageName)) continue
                    it.permissions["stellar"]?.let { configFlag ->
                        flag = configFlag
                    }
                }

                if (flag != -1) {
                    list.add(pi)
                } else if (applicationInfo.metaData != null) {
                    // 检查 Stellar 权限声明
                    val stellarPermission = applicationInfo.metaData.getString(
                        StellarApiConstants.PERMISSION_KEY,
                        ""
                    )
                    if (stellarPermission.split(",").contains("stellar")) {
                        list.add(pi)
                    } else if (applicationInfo.metaData.getBoolean("moe.shizuku.client.V3_SUPPORT", false)) {
                        // 检查 Shizuku 支持
                        list.add(pi)
                    }
                }
            }
        }
        return ParcelableListSlice<PackageInfo?>(list)
    }

    // ========== 文件监听 ==========

    @Suppress("DEPRECATION")
    private fun registerPackageRemovedReceiver(ai: ApplicationInfo) {
        val externalDataDir = File("/storage/emulated/0/Android/data/$MANAGER_APPLICATION_ID")

        if (externalDataDir.exists()) {
            val dirObserver = object : FileObserver(
                externalDataDir.absolutePath,
                FileObserver.ALL_EVENTS
            ) {
                override fun onEvent(event: Int, path: String?) {
                    LOGGER.w("外部存储目录事件: event=${eventToString(event)}, path=$path, exists=${externalDataDir.exists()}")
                    
                    if (event and FileObserver.DELETE_SELF != 0 || event and FileObserver.MOVED_FROM != 0) {
                        if (!externalDataDir.exists()) {
                            LOGGER.w("管理器应用外部存储目录已被删除，正在退出...")
                            exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
                        }
                    }
                }
            }
            dirObserver.startWatching()
        } else {
            LOGGER.w("外部存储目录不存在，将在下次启动时创建")
        }
    }
    
    private fun eventToString(event: Int): String {
        val events = mutableListOf<String>()
        if (event and FileObserver.ACCESS != 0) events.add("ACCESS")
        if (event and FileObserver.MODIFY != 0) events.add("MODIFY")
        if (event and FileObserver.ATTRIB != 0) events.add("ATTRIB")
        if (event and FileObserver.CLOSE_WRITE != 0) events.add("CLOSE_WRITE")
        if (event and FileObserver.CLOSE_NOWRITE != 0) events.add("CLOSE_NOWRITE")
        if (event and FileObserver.OPEN != 0) events.add("OPEN")
        if (event and FileObserver.MOVED_FROM != 0) events.add("MOVED_FROM")
        if (event and FileObserver.MOVED_TO != 0) events.add("MOVED_TO")
        if (event and FileObserver.CREATE != 0) events.add("CREATE")
        if (event and FileObserver.DELETE != 0) events.add("DELETE")
        if (event and FileObserver.DELETE_SELF != 0) events.add("DELETE_SELF")
        if (event and FileObserver.MOVE_SELF != 0) events.add("MOVE_SELF")
        return if (events.isEmpty()) "UNKNOWN($event)" else events.joinToString("|")
    }

    // ========== Shizuku 兼容层 ==========

    private fun createShizukuCallback(): ShizukuServiceCallback {
        return object : ShizukuServiceCallback {
            override val stellarService: IStellarService get() = this@StellarService
            override val clientManager: ClientManager get() = this@StellarService.clientManager
            override val configManager: ConfigManager get() = this@StellarService.configManager
            override val userServiceManager: UserServiceManager get() = this@StellarService.userServiceManager
            override val managerAppId: Int get() = this@StellarService.managerAppId
            override val servicePid: Int get() = android.os.Process.myPid()

            override fun getPackagesForUid(uid: Int): List<String> {
                return PackageManagerApis.getPackagesForUidNoThrow(uid).toList()
            }

            override fun requestPermission(uid: Int, pid: Int, requestCode: Int) {
                val userId = uid / 100000
                val packages = PackageManagerApis.getPackagesForUidNoThrow(uid)
                val packageName = packages.firstOrNull() ?: return

                LOGGER.i("Shizuku 权限请求: uid=$uid, pid=$pid, pkg=$packageName, code=$requestCode")

                val ai = PackageManagerApis.getApplicationInfoNoThrow(packageName, 0, userId)
                if (ai == null) {
                    LOGGER.w("无法获取应用信息: $packageName")
                    return
                }

                // 检查当前权限状态
                val currentFlag = configManager.getPermissionFlag(uid, ShizukuApiConstants.PERMISSION_NAME)

                // 如果已经被永久拒绝，直接返回拒绝结果
                if (currentFlag == ConfigManager.FLAG_DENIED) {
                    LOGGER.i("Shizuku 权限已被永久拒绝: uid=$uid")
                    shizukuServiceIntercept.notifyPermissionResult(uid, pid, requestCode, false)
                    return
                }

                // 如果已经被永久授权，直接返回授权结果
                if (currentFlag == ConfigManager.FLAG_GRANTED) {
                    LOGGER.i("Shizuku 权限已被永久授权: uid=$uid")
                    shizukuServiceIntercept.notifyPermissionResult(uid, pid, requestCode, true)
                    return
                }

                // 确保配置存在
                if (configManager.find(uid) == null) {
                    configManager.createConfigWithAllPermissions(uid, packageName)
                }

                // 检查是否在短时间内被拒绝过（10秒内），决定是否显示"不再询问"选项
                val lastDenyTime = clientManager.findClient(uid, pid)?.lastDenyTimeMap?.get(ShizukuApiConstants.PERMISSION_NAME) ?: 0
                val denyOnce = (System.currentTimeMillis() - lastDenyTime) > 10000

                val intent = Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
                    .setPackage(ServerConstants.MANAGER_APPLICATION_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    .putExtra("uid", uid)
                    .putExtra("pid", pid)
                    .putExtra("requestCode", requestCode)
                    .putExtra("applicationInfo", ai)
                    .putExtra("denyOnce", denyOnce)
                    .putExtra("permission", ShizukuApiConstants.PERMISSION_NAME)

                rikka.hidden.compat.ActivityManagerApis.startActivityNoThrow(intent, null, userId)
            }
        }
    }

    // ========== Binder 发送 ==========

    fun sendBinderToClient() {
        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClient(this, userId)
        }
    }

    fun sendBinderToManager() {
        sendBinderToManger(this)
    }

    // ========== Companion Object ==========

    companion object {
        private val LOGGER: Logger = Logger("StellarService")
        // Shizuku Manager 特征权限 (signature 级别，只有 Manager 会请求)
        private const val SHIZUKU_MANAGER_PERMISSION = "moe.shizuku.manager.permission.MANAGER"

        @JvmStatic
        fun main(args: Array<String>) {
            DdmHandleAppName.setAppName("stellar_server", 0)

            Looper.prepareMainLooper()
            StellarService()
            Looper.loop()
        }

        private fun waitSystemService(name: String?) {
            while (ServiceManager.getService(name) == null) {
                try {
                    LOGGER.i("服务 $name 尚未启动，等待 1 秒。")
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    LOGGER.w(e.message, e)
                }
            }
        }

        val managerApplicationInfo: ApplicationInfo?
            get() = PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)

        private fun sendBinderToClient(binder: Binder?, userId: Int) {
            try {
                for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                    PackageManager.GET_META_DATA.toLong(),
                    userId
                )) {
                    if (pi == null || pi.applicationInfo == null || pi.applicationInfo!!.metaData == null) continue

                    if (pi.applicationInfo!!.metaData.getString(StellarApiConstants.PERMISSION_KEY, "").split(",")
                            .contains("stellar")
                    ) {
                        sendBinderToUserApp(binder, pi.packageName, userId)
                    }
                }
            } catch (tr: Throwable) {
                LOGGER.e("调用 getInstalledPackages 时发生异常", tr = tr)
            }
        }

        private fun sendBinderToManger(binder: Binder?) {
            for (userId in UserManagerApis.getUserIdsNoThrow()) {
                sendBinderToManger(binder, userId)
            }
        }

        fun sendBinderToManger(binder: Binder?, userId: Int) {
            sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId)
        }

        fun sendBinderToUserApp(
            binder: Binder?,
            packageName: String?,
            userId: Int,
            retry: Boolean = true
        ) {
            sendBinderInternal(
                packageName = packageName,
                userId = userId,
                providerSuffix = ".stellar",
                extraKey = "roro.stellar.manager.intent.extra.BINDER",
                binderContainer = com.stellar.api.BinderContainer(binder),
                logPrefix = "",
                retry = retry,
                onRetry = { sendBinderToUserApp(binder, packageName, userId, false) }
            )
        }

        fun sendShizukuBinderToUserApp(
            stellarService: StellarService?,
            packageName: String?,
            userId: Int
        ) {
            if (stellarService == null || packageName == null) return

            sendBinderInternal(
                packageName = packageName,
                userId = userId,
                providerSuffix = ".shizuku",
                extraKey = ShizukuApiConstants.EXTRA_BINDER,
                binderContainer = moe.shizuku.api.BinderContainer(stellarService.shizukuServiceIntercept.asBinder()),
                logPrefix = "Shizuku ",
                retry = false,
                onRetry = null
            )
        }

        private fun sendBinderInternal(
            packageName: String?,
            userId: Int,
            providerSuffix: String,
            extraKey: String,
            binderContainer: android.os.Parcelable,
            logPrefix: String,
            retry: Boolean,
            onRetry: (() -> Unit)?
        ) {
            if (packageName == null) return

            try {
                rikka.hidden.compat.DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(
                    packageName, 30_000L, userId, 316, "shell"
                )
                LOGGER.v("将 %d:%s 添加到省电临时白名单 30 秒", userId, packageName)
            } catch (tr: Throwable) {
                LOGGER.e(tr, "添加 %d:%s 到省电临时白名单失败", userId, packageName)
            }

            val name = "$packageName$providerSuffix"
            var provider: android.content.IContentProvider? = null
            val token: IBinder? = null

            try {
                provider = rikka.hidden.compat.ActivityManagerApis.getContentProviderExternal(name, userId, token, name)
                if (provider == null) {
                    LOGGER.e("${logPrefix}provider 为 null %s %d", name, userId)
                    return
                }
                if (!provider.asBinder().pingBinder()) {
                    LOGGER.e("${logPrefix}provider 已失效 %s %d", name, userId)
                    if (retry && onRetry != null) {
                        rikka.hidden.compat.ActivityManagerApis.forceStopPackageNoThrow(packageName, userId)
                        LOGGER.e("终止用户 %d 中的 %s 并重试", userId, packageName)
                        Thread.sleep(1000)
                        onRetry()
                    }
                    return
                }

                if (retry && onRetry != null) {
                    // 这是重试后的成功情况，不需要额外日志
                }

                val extra = Bundle()
                extra.putParcelable(extraKey, binderContainer)

                val reply = roro.stellar.server.api.IContentProviderUtils.callCompat(provider, null, name, "sendBinder", null, extra)
                if (reply != null) {
                    LOGGER.i("已向用户 %d 中的应用 %s 发送 ${logPrefix}binder", userId, packageName)
                } else {
                    LOGGER.w("向用户 %d 中的应用 %s 发送 ${logPrefix}binder 失败", userId, packageName)
                }
            } catch (tr: Throwable) {
                LOGGER.e(tr, "向用户 %d 中的应用 %s 发送 ${logPrefix}binder 失败", userId, packageName)
            } finally {
                if (provider != null) {
                    try {
                        rikka.hidden.compat.ActivityManagerApis.removeContentProviderExternal(name, token)
                    } catch (tr: Throwable) {
                        LOGGER.w(tr, "移除 ContentProvider 失败")
                    }
                }
            }
        }
    }
}
