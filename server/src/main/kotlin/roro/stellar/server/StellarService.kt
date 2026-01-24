package roro.stellar.server

import android.content.Context
import android.content.IContentProvider
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.ddm.DdmHandleAppName
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import android.os.SELinux
import android.os.ServiceManager
import android.os.SystemProperties
import android.system.Os
import androidx.annotation.CallSuper
import com.stellar.api.BinderContainer
import com.stellar.server.IRemoteProcess
import com.stellar.server.IStellarApplication
import com.stellar.server.IStellarService
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.DeviceIdleControllerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.PermissionManagerApis
import rikka.hidden.compat.UserManagerApis
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.StellarApiConstants
import roro.stellar.StellarApiConstants.PERMISSION_KEY
import roro.stellar.server.ApkChangedObservers.start
import roro.stellar.server.BinderSender.register
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.api.IContentProviderUtils.callCompat
import roro.stellar.server.api.RemoteProcessHolder
import roro.stellar.server.ext.FollowStellarStartupExt
import roro.stellar.server.ktx.mainHandler
import roro.stellar.server.util.Logger
import roro.stellar.server.util.OsUtils
import roro.stellar.server.util.UserHandleCompat.getAppId
import roro.stellar.server.util.UserHandleCompat.getUserId
import roro.stellar.server.userservice.UserServiceManager
import com.stellar.server.IUserServiceCallback
import android.os.FileObserver
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

class StellarService : IStellarService.Stub() {

    private val clientManager: ClientManager
    private val configManager: ConfigManager
    private val userServiceManager: UserServiceManager
    private val managerAppId: Int

    init {
        try {
            LOGGER.i("正在启动 Stellar 服务...")
            System.err.println("正在启动 Stellar 服务...")

            LOGGER.i("等待系统服务...")
            System.err.println("等待系统服务...")
            waitSystemService("package")
            waitSystemService(Context.ACTIVITY_SERVICE)
            waitSystemService(Context.USER_SERVICE)
            waitSystemService(Context.APP_OPS_SERVICE)

            LOGGER.i("获取管理器应用信息...")
            System.err.println("获取管理器应用信息...")
            val ai = managerApplicationInfo
            if (ai == null) {
                System.err.println("错误：无法获取管理器应用信息")
                exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
            }

            managerAppId = ai.uid
            LOGGER.i("管理器应用 UID: $managerAppId")
            System.err.println("管理器应用 UID: $managerAppId")

            LOGGER.i("初始化配置管理器...")
            System.err.println("初始化配置管理器...")
            configManager = ConfigManager()
            clientManager = ClientManager(configManager)
            userServiceManager = UserServiceManager()

            LOGGER.i("启动文件监听...")
            System.err.println("启动文件监听...")
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
            System.err.println("注册包移除监听...")
            registerPackageRemovedReceiver(ai)

            LOGGER.i("注册 Binder...")
            System.err.println("注册 Binder...")
            register(this)

            LOGGER.i("发送 Binder 到客户端...")
            System.err.println("发送 Binder 到客户端...")
            mainHandler.post {
                try {
                    sendBinderToClient()
                    sendBinderToManager()
                    FollowStellarStartupExt.schedule(configManager)
                    LOGGER.i("Stellar 服务启动完成")
                    System.err.println("Stellar 服务启动完成")
                } catch (e: Throwable) {
                    LOGGER.e(e, "发送 Binder 失败")
                    System.err.println("错误：发送 Binder 失败: ${e.message}")
                    e.printStackTrace()
                }
            }
        } catch (e: Throwable) {
            LOGGER.e(e, "Stellar 服务初始化失败")
            System.err.println("错误：Stellar 服务初始化失败: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    fun checkCallerManagerPermission(
        callingUid: Int
    ): Boolean {
        return getAppId(callingUid) == managerAppId
    }

    val managerVersionName: String?
        get() {
            val pi: PackageInfo = managerPackageInfo ?: return "unknown"
            return if (pi.versionName != null) pi.versionName else "unknown"
        }

    val managerVersionCode: Int
        get() {
            val pi: PackageInfo = managerPackageInfo ?: return -1
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pi.longVersionCode.toInt()
                } else {
                    pi.versionCode
                }
            } catch (_: Exception) {
                -1
            }
        }

    override fun exit() {
        enforceManagerPermission("exit")
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

        LOGGER.i("attachApplication 完成: %s %d %d", requestPackageName, callingUid, callingPid)

        val reply = Bundle()
        reply.putInt(StellarApiConstants.BIND_APPLICATION_SERVER_UID, OsUtils.uid)
        reply.putInt(
            StellarApiConstants.BIND_APPLICATION_SERVER_VERSION,
            StellarApiConstants.SERVER_VERSION
        )
        reply.putString(
            StellarApiConstants.BIND_APPLICATION_SERVER_SECONTEXT,
            OsUtils.sELinuxContext
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

    fun showPermissionConfirmation(
        requestCode: Int,
        clientRecord: ClientRecord,
        callingUid: Int,
        callingPid: Int,
        userId: Int,
        permission: String = "stellar"
    ) {
        val ai = PackageManagerApis.getApplicationInfoNoThrow(clientRecord.packageName, 0, userId)
            ?: return

        val pi = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, userId)
        val userInfo = UserManagerApis.getUserInfo(userId)
        val isWorkProfileUser =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ("android.os.usertype.profile.MANAGED" == userInfo.userType) else (userInfo.flags and UserInfo.FLAG_MANAGED_PROFILE) != 0
        if (pi == null && !isWorkProfileUser) {
            LOGGER.w("在非工作配置文件用户 %d 中未找到管理器，撤销权限", userId)
            clientRecord.dispatchRequestPermissionResult(
                requestCode,
                allowed = false,
                onetime = false
            )
            return
        }

        val intent = Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
            .setPackage(MANAGER_APPLICATION_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            .putExtra("uid", callingUid)
            .putExtra("pid", callingPid)
            .putExtra("requestCode", requestCode)
            .putExtra("applicationInfo", ai)
            .putExtra(
                "denyOnce",
                (System.currentTimeMillis() - (clientRecord.lastDenyTimeMap[permission]
                    ?: 0)) > 10000
            )
            .putExtra("permission", permission)
        ActivityManagerApis.startActivityNoThrow(intent, null, if (isWorkProfileUser) 0 else userId)
    }

    @Throws(RemoteException::class)
    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle?
    ) {
        if (getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("dispatchPermissionConfirmationResult 不是从管理器调用的")
            return
        }

        if (data == null) {
            return
        }

        val allowed = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED)
        val onetime = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME)
        val permission =
            data.getString(StellarApiConstants.REQUEST_PERMISSION_REPLY_PERMISSION, "stellar")

        LOGGER.i(
            "dispatchPermissionConfirmationResult: uid=$requestUid, pid=$requestPid, requestCode=$requestCode, allowed=$allowed, onetime=$onetime, permission=$permission"
        )

        val records: MutableList<ClientRecord> = clientManager.findClients(requestUid)
        val packages = ArrayList<String>()
        if (records.isEmpty()) {
            LOGGER.w("dispatchPermissionConfirmationResult：未找到 uid %d 的客户端", requestUid)
        } else {
            for (record in records) {
                packages.add(record.packageName)
                if (StellarApiConstants.isRuntimePermission(permission)) {
                    record.allowedMap[permission] = allowed
                }
                if (record.pid == requestPid) {
                    record.dispatchRequestPermissionResult(
                        requestCode, allowed, onetime,
                        permission
                    )
                }
            }
        }

        configManager.update(
            requestUid,
            packages
        )
        configManager.updatePermission(
            requestUid,
            permission,
            if (onetime) ConfigManager.FLAG_ASK else if (allowed) ConfigManager.FLAG_GRANTED else ConfigManager.FLAG_DENIED
        )
    }

    private fun getFlagForUidInternal(uid: Int, permission: String): Int {
        val entry = configManager.find(uid)
        return entry?.permissions[permission] ?: ConfigManager.FLAG_ASK
    }

    override fun getFlagForUid(uid: Int, permission: String): Int {
        if (getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("getFlagsForUid 只允许从管理器调用")
            return 0
        }
        return getFlagForUidInternal(uid, permission)
    }

    @Throws(RemoteException::class)
    override fun updateFlagForUid(uid: Int, permission: String, newFlag: Int) {
        if (getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid 只允许从管理器调用")
            return
        }

        val records: MutableList<ClientRecord> = clientManager.findClients(uid)
        for (record in records) {
            fun stopApp() {
                if (StellarApiConstants.isRuntimePermission(permission) && record.allowedMap[permission] == true) {
                    ActivityManagerApis.forceStopPackageNoThrow(
                        record.packageName,
                        getUserId(uid)
                    )
                    onPermissionRevoked(record.packageName)
                }
            }

            when (newFlag) {
                ConfigManager.FLAG_ASK -> {
                    stopApp()
                    record.allowedMap[permission] = false
                    record.onetimeMap[permission] = false
                }

                ConfigManager.FLAG_DENIED -> {
                    stopApp()
                    record.allowedMap[permission] = false
                    record.onetimeMap[permission] = false
                }

                ConfigManager.FLAG_GRANTED -> {
                    record.allowedMap[permission] = true
                    record.onetimeMap[permission] = false
                }
            }
        }

        configManager.updatePermission(uid, permission, newFlag)
    }

    @Throws(RemoteException::class)
    override fun grantRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            PermissionManagerApis.grantRuntimePermission(packageName, permissionName, userId)
        } catch (e: Exception) {
            throw RemoteException("授予权限失败: ${e.message}")
        }
    }

    @Throws(RemoteException::class)
    override fun revokeRuntimePermission(packageName: String, permissionName: String, userId: Int) {
        try {
            PermissionManagerApis.revokeRuntimePermission(packageName, permissionName, userId)
        } catch (e: Exception) {
            throw RemoteException("撤销权限失败: ${e.message}")
        }
    }

    private fun onPermissionRevoked(packageName: String?) {
    }

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
                PackageManager.GET_META_DATA.toLong(),
                user!!
            )) {
                if (MANAGER_APPLICATION_ID == pi.packageName) continue
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
                } else if (applicationInfo.metaData != null && applicationInfo.metaData.getString(
                        PERMISSION_KEY,
                        ""
                    ).split(",").contains("stellar")
                ) {
                    list.add(pi)
                }
            }
        }
        return ParcelableListSlice<PackageInfo?>(list)
    }

    fun enforceManagerPermission(func: String) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingPid == Os.getpid()) {
            return
        }

        if (checkCallerManagerPermission(callingUid)) {
            return
        }

        val msg = ("Permission Denial: " + func + " from pid="
                + getCallingPid()
                + " is not manager ")
        LOGGER.w(msg)
        throw SecurityException(msg)
    }

    fun enforceCallingPermission(func: String) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingUid == OsUtils.uid) {
            return
        }

        val clientRecord = clientManager.findClient(callingUid, callingPid)

        if (clientRecord == null) {
            val msg = ("Permission Denial: " + func + " from pid="
                    + getCallingPid()
                    + " is not an attached client")
            LOGGER.w(msg)
            throw SecurityException(msg)
        }

        if (clientRecord.allowedMap["stellar"] != true) {
            val msg = ("Permission Denial: " + func + " from pid="
                    + getCallingPid()
                    + " requires permission")
            LOGGER.w(msg)
            throw SecurityException(msg)
        }
    }

    fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        enforceCallingPermission("transactRemote")

        val targetBinder = data.readStrongBinder()
        val targetCode = data.readInt()
        val targetFlags: Int

        val callingUid = getCallingUid()
        val callingPid = getCallingPid()
        val clientRecord = clientManager.findClient(callingUid, callingPid)

        targetFlags = if (clientRecord != null && clientRecord.apiVersion >= 13) {
            data.readInt()
        } else {
            flags
        }

        LOGGER.d(
            "transact: uid=%d, descriptor=%s, code=%d",
            getCallingUid(),
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

    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return StellarApiConstants.SERVER_VERSION
    }

    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return Os.getuid()
    }

    override fun checkPermission(permission: String?): Int {
        enforceCallingPermission("checkPermission")
        return PermissionManagerApis.checkPermission(permission, Os.getuid())
    }

    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")

        try {
            return SELinux.getContext()
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }

    override fun getVersionName(): String? {
        enforceCallingPermission("getVersionName")
        return this.managerVersionName
    }

    override fun getVersionCode(): Int {
        enforceCallingPermission("getVersionCode")
        return this.managerVersionCode
    }

    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")

        try {
            return SystemProperties.get(name, defaultValue)
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }

    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")

        try {
            SystemProperties.set(name, value)
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }

    override fun getSupportedPermissions(): Array<out String> {
        return StellarApiConstants.PERMISSIONS
    }

    override fun checkSelfPermission(permission: String?): Boolean {
        if (!supportedPermissions.contains(permission)) return false

        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingUid == OsUtils.uid || callingPid == OsUtils.pid) {
            return true
        }

        return when (configManager.find(callingUid)?.permissions?.get(permission)) {
            ConfigManager.FLAG_GRANTED -> true
            ConfigManager.FLAG_DENIED -> false
            else -> {
                if (StellarApiConstants.isRuntimePermission(permission!!))
                    clientManager.requireClient(callingUid, callingPid).allowedMap[permission]
                        ?: false
                else false
            }
        }
    }

    override fun requestPermission(
        permission: String,
        requestCode: Int
    ) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()
        val userId = getUserId(callingUid)

        if (callingUid == OsUtils.uid || callingPid == OsUtils.pid) {
            return
        }

        val clientRecord = clientManager.requireClient(callingUid, callingPid)

        if (!supportedPermissions.contains(permission)) {
            clientRecord.dispatchRequestPermissionResult(
                requestCode,
                allowed = false,
                onetime = false,
                permission
            )
            return
        }

        when (configManager.find(callingUid)?.permissions?.get(permission) ?: return) {
            ConfigManager.FLAG_GRANTED -> {
                clientRecord.dispatchRequestPermissionResult(
                    requestCode,
                    allowed = true,
                    onetime = false,
                    permission
                )
                return
            }

            ConfigManager.FLAG_DENIED -> {
                clientRecord.dispatchRequestPermissionResult(
                    requestCode,
                    allowed = false,
                    onetime = false,
                    permission
                )
                return
            }

            else -> {
                showPermissionConfirmation(
                    requestCode,
                    clientRecord,
                    callingUid,
                    callingPid,
                    userId,
                    permission
                )
            }
        }
    }

    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        if (callingUid == OsUtils.uid || callingPid == OsUtils.pid) {
            return true
        }

        clientManager.requireClient(callingUid, callingPid)

        val entry = configManager.find(callingUid)
        return entry != null && entry.permissions["stellar"] == ConfigManager.FLAG_DENIED
    }

    override fun newProcess(
        cmd: Array<String?>?,
        env: Array<String?>?,
        dir: String?
    ): IRemoteProcess {
        enforceCallingPermission("newProcess")

        LOGGER.d(
            "newProcess: uid=%d, cmd=%s, env=%s, dir=%s",
            getCallingUid(),
            cmd.contentToString(),
            env.contentToString(),
            dir
        )

        val process: Process
        try {
            process = Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
        } catch (e: IOException) {
            throw IllegalStateException(e.message)
        }

        val clientRecord = clientManager.findClient(getCallingUid(), getCallingPid())
        val token = clientRecord?.client?.asBinder()

        return RemoteProcessHolder(process, token)
    }

    override fun startUserService(args: Bundle?, callback: IUserServiceCallback?): String? {
        enforceCallingPermission("startUserService")
        if (args == null) return null

        return userServiceManager.startUserService(
            getCallingUid(),
            getCallingPid(),
            args,
            callback
        )
    }

    override fun stopUserService(token: String?) {
        enforceCallingPermission("stopUserService")
        if (token == null) return

        userServiceManager.stopUserService(token)
    }

    override fun attachUserService(binder: IBinder?, options: Bundle?) {
        if (binder == null || options == null) return

        userServiceManager.attachUserService(binder, options)
    }

    override fun getUserServiceCount(): Int {
        enforceCallingPermission("getUserServiceCount")

        return userServiceManager.getUserServiceCount(getCallingUid())
    }

    @CallSuper
    @Throws(RemoteException::class)
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

    fun sendBinderToClient() {
        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClient(this, userId)
        }
    }

    fun sendBinderToManager() {
        sendBinderToManger(this)
    }

    @Suppress("DEPRECATION")
    private fun registerPackageRemovedReceiver(ai: ApplicationInfo) {
        val externalDataDir = File("/storage/emulated/0/Android/data/$MANAGER_APPLICATION_ID")
        LOGGER.i("外部存储目录状态: exists=${externalDataDir.exists()}, path=${externalDataDir.absolutePath}")
        
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
            LOGGER.i("已开始监听外部存储目录（所有事件）")
        } else {
            LOGGER.w("外部存储目录不存在，将在下次启动时创建")
        }
        
        mainHandler.postDelayed({
            LOGGER.i("5秒后检查: 外部存储目录 exists=${externalDataDir.exists()}, 应用信息=${managerApplicationInfo != null}")
        }, 5000)
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

    companion object {

        private val LOGGER: Logger = Logger("StellarService")

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

        val managerPackageInfo: PackageInfo?
            get() = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)

        private fun sendBinderToClient(binder: Binder?, userId: Int) {
            try {
                for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                    PackageManager.GET_META_DATA.toLong(),
                    userId
                )) {
                    if (pi == null || pi.applicationInfo == null || pi.applicationInfo!!.metaData == null) continue

                    if (pi.applicationInfo!!.metaData.getString(PERMISSION_KEY, "").split(",")
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
            try {
                DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(
                    packageName, (30 * 1000).toLong(), userId,
                    316, "shell"
                )
                LOGGER.v("将 %d:%s 添加到省电临时白名单 30 秒", userId, packageName)
            } catch (tr: Throwable) {
                LOGGER.e(tr, "添加 %d:%s 到省电临时白名单失败", userId, packageName)
            }

            val name = "$packageName.stellar"
            var provider: IContentProvider? = null

            val token: IBinder? = null

            try {
                provider = ActivityManagerApis.getContentProviderExternal(name, userId, token, name)
                if (provider == null) {
                    LOGGER.e("provider 为 null %s %d", name, userId)
                    return
                }
                if (!provider.asBinder().pingBinder()) {
                    LOGGER.e("provider 已失效 %s %d", name, userId)

                    if (retry) {
                        ActivityManagerApis.forceStopPackageNoThrow(packageName, userId)
                        LOGGER.e("终止用户 %d 中的 %s 并重试", userId, packageName)
                        Thread.sleep(1000)
                        sendBinderToUserApp(binder, packageName, userId, false)
                    }
                    return
                }

                if (!retry) {
                    LOGGER.e("重试成功")
                }

                val extra = Bundle()
                extra.putParcelable(
                    "roro.stellar.manager.intent.extra.BINDER",
                    BinderContainer(binder)
                )

                val reply = callCompat(provider, null, name, "sendBinder", null, extra)
                if (reply != null) {
                    LOGGER.i("已向用户 %d 中的用户应用 %s 发送 binder", userId, packageName)
                } else {
                    LOGGER.w("向用户 %d 中的用户应用 %s 发送 binder 失败", userId, packageName)
                }
            } catch (tr: Throwable) {
                LOGGER.e(tr, "向用户 %d 中的用户应用 %s 发送 binder 失败", userId, packageName)
            } finally {
                if (provider != null) {
                    try {
                        ActivityManagerApis.removeContentProviderExternal(name, token)
                    } catch (tr: Throwable) {
                        LOGGER.w(tr, "移除 ContentProvider 失败")
                    }
                }
            }
        }
    }
}