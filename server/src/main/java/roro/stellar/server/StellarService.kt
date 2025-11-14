/**
 * Stellar服务核心实现类
 * Stellar Service Core Implementation
 *
 *
 * 功能说明 Features：
 *
 *  * Stellar服务端的主要实现 - Main implementation of Stellar service
 *  * 运行在特权进程中（root或adb） - Runs in privileged process (root or adb)
 *  * 管理客户端连接和权限 - Manages client connections and permissions
 *  * 提供系统服务访问能力 - Provides system service access capabilities
 *  * 处理跨进程Binder事务 - Handles cross-process Binder transactions
 *
 *
 *
 * 主要职责 Main Responsibilities：
 *
 *  * 客户端管理：跟踪和管理连接的应用 - Client management: tracks and manages connected apps
 *  * 权限管理：验证和授予API访问权限 - Permission management: validates and grants API access
 *  * Binder转发：代理系统服务的Binder调用 - Binder forwarding: proxies system service Binder calls
 *  * 进程管理：创建和管理远程进程 - Process management: creates and manages remote processes
 *  * 配置管理：持久化权限和设置 - Configuration management: persists permissions and settings
 *
 *
 *
 * 启动流程 Startup Flow：
 *
 *  * 1. 等待关键系统服务就绪 - Wait for critical system services
 *  * 2. 初始化客户端管理器和配置管理器 - Initialize client and config managers
 *  * 3. 监听Manager应用的APK变化 - Monitor Manager app APK changes
 *  * 4. 注册Binder发送器 - Register Binder sender
 *  * 5. 发送Binder到客户端和Manager - Send Binder to clients and Manager
 *
 *
 *
 * 安全机制 Security Mechanism：
 *
 *  * 调用者UID/PID验证 - Caller UID/PID validation
 *  * 权限检查和授予流程 - Permission checking and granting flow
 *  * Manager应用身份验证 - Manager app identity verification
 *
 */
package roro.stellar.server

import android.Manifest
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import android.os.ServiceManager
import com.stellar.api.BinderContainer
import com.stellar.common.util.BuildUtils
import com.stellar.common.util.OsUtils
import com.stellar.server.IStellarApplication
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.DeviceIdleControllerApis
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.PermissionManagerApis
import rikka.hidden.compat.UserManagerApis
import rikka.parcelablelist.ParcelableListSlice
import roro.stellar.StellarApiConstants
import roro.stellar.server.ApkChangedObservers.start
import roro.stellar.server.BinderSender.register
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.ServerConstants.PERMISSION
import roro.stellar.server.api.IContentProviderUtils.callCompat
import roro.stellar.server.util.HandlerUtil
import roro.stellar.server.util.UserHandleCompat.getAppId
import roro.stellar.server.util.UserHandleCompat.getUserId
import kotlin.system.exitProcess

class StellarService : Service<StellarClientManager, StellarConfigManager>() {
    private val mainHandler = Handler(Looper.myLooper()!!)

    //private final Context systemContext = HiddenApiBridge.getSystemContext();
    private val mClientManager: StellarClientManager?
    private val mConfigManager: StellarConfigManager?
    private val managerAppId: Int

    /**
     * 构造Stellar服务
     * Construct Stellar service
     *
     *
     * 初始化流程 Initialization flow：
     *
     *  1. 等待系统服务就绪（PackageManager、ActivityManager等）
     *  1. 检查Manager应用是否安装
     *  1. 创建配置和客户端管理器
     *  1. 启动APK变化监听器
     *  1. 注册Binder发送器
     *  1. 向客户端和Manager发送Binder
     *
     *
     *
     * 如果Manager应用未安装，服务将退出
     */
    init {
        HandlerUtil.mainHandler = mainHandler

        LOGGER.i("正在启动服务器...")

        waitSystemService("package")
        waitSystemService(Context.ACTIVITY_SERVICE)
        waitSystemService(Context.USER_SERVICE)
        waitSystemService(Context.APP_OPS_SERVICE)

        val ai = managerApplicationInfo ?: exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)

        managerAppId = ai.uid

        mConfigManager = configManager
        mClientManager = clientManager

        start(ai.sourceDir) {
            if (managerApplicationInfo == null) {
                LOGGER.w("用户 0 中的管理器应用已卸载，正在退出...")
                exitProcess(ServerConstants.MANAGER_APP_NOT_FOUND)
            }
        }

        register(this)

        mainHandler.post {
            sendBinderToClient()
            sendBinderToManager()
        }
    }


    /**
     * 创建客户端管理器
     * Create client manager
     *
     * @return StellarClientManager实例
     */
    override fun onCreateClientManager(): StellarClientManager {
        return StellarClientManager(configManager)
    }

    /**
     * 创建配置管理器
     * Create config manager
     *
     * @return StellarConfigManager实例
     */
    override fun onCreateConfigManager(): StellarConfigManager {
        return StellarConfigManager()
    }

    /**
     * 检查调用者是否为Manager
     * Check if caller is Manager
     *
     * @param func 调用的方法名
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @return true表示是Manager应用
     */
    override fun checkCallerManagerPermission(
        func: String?,
        callingUid: Int,
        callingPid: Int
    ): Boolean {
        return getAppId(callingUid) == managerAppId
    }

    override val managerVersionName: String?
        /**
         * 获取Manager应用的版本名称
         * Get Manager app version name
         *
         * @return 版本名称字符串（如"1.0.0"），如果未安装则返回"unknown"
         */
        get() {
            val pi: PackageInfo = managerPackageInfo ?: return "unknown"
            // versionName可能为null，需要处理
            return if (pi.versionName != null) pi.versionName else "unknown"
        }

    override val managerVersionCode: Int
        /**
         * 获取Manager应用的版本代码
         * Get Manager app version code
         *
         * @return 版本代码，如果未安装则返回-1
         */
        get() {
            val pi: PackageInfo = managerPackageInfo ?: return -1
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pi.longVersionCode.toInt()
                } else {
                    pi.versionCode
                }
            } catch (_: Exception) {
                // 如果getLongVersionCode失败，返回-1
                -1
            }
        }

    /**
     * 检查调用者权限
     * Check calling permission
     *
     *
     * 通过ActivityManager检查调用者是否持有Stellar权限
     *
     * @return PERMISSION_GRANTED或PERMISSION_DENIED
     */
    private fun checkCallingPermission(): Int {
        try {
            return ActivityManagerApis.checkPermission(
                PERMISSION,
                getCallingPid(),
                getCallingUid()
            )
        } catch (tr: Throwable) {
            LOGGER.w(tr, "checkCallingPermission")
            return PackageManager.PERMISSION_DENIED
        }
    }

    /**
     * 检查调用者权限
     * Check caller permission
     *
     *
     * 权限检查优先级：
     *
     *  1. Manager应用：直接通过
     *  1. 已注册客户端：根据clientRecord判断
     *  1. 未注册客户端：检查系统权限
     *
     *
     * @param func 调用的方法名
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param clientRecord 客户端记录
     * @return true表示有权限
     */
    override fun checkCallerPermission(
        func: String?,
        callingUid: Int,
        callingPid: Int,
        clientRecord: ClientRecord?
    ): Boolean {
        if (getAppId(callingUid) == managerAppId) {
            return true
        }
        return clientRecord == null && checkCallingPermission() == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 退出服务
     * Exit service
     *
     *
     * 仅Manager应用可调用此方法
     */
    override fun exit() {
        enforceManagerPermission("exit")
        LOGGER.i("exit")
        exitProcess(0)
    }


    /**
     * 绑定应用程序
     * Attach application
     *
     *
     * 功能说明 Features：
     *
     *  * 注册客户端应用到服务 - Register client app to service
     *  * 验证请求包名归属 - Verify package name ownership
     *  * 返回服务器信息（UID、版本、SELinux上下文等）
     *  * 为Manager应用授予WRITE_SECURE_SETTINGS权限
     *  * 通知客户端绑定结果
     *
     *
     * @param application 客户端应用的Binder接口
     * @param args 包含包名和API版本的参数
     */
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

        if (mClientManager!!.findClient(callingUid, callingPid) == null) {
            synchronized(this) {
                clientRecord = mClientManager.addClient(
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
        }

        LOGGER.d("attachApplication: %s %d %d", requestPackageName, callingUid, callingPid)

        val reply = Bundle()
        reply.putInt(StellarApiConstants.BIND_APPLICATION_SERVER_UID, OsUtils.getUid())
        reply.putInt(StellarApiConstants.BIND_APPLICATION_SERVER_VERSION, StellarApiConstants.SERVER_VERSION)
        reply.putString(
            StellarApiConstants.BIND_APPLICATION_SERVER_SECONTEXT,
            OsUtils.getSELinuxContext()
        )
        reply.putInt(
            StellarApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION,
            StellarApiConstants.SERVER_PATCH_VERSION
        )
        if (!isManager) {
            reply.putBoolean(
                StellarApiConstants.BIND_APPLICATION_PERMISSION_GRANTED,
                clientRecord?.allowed ?: false
            )
            reply.putBoolean(
                StellarApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE,
                false
            )
        } else {
            try {
                PermissionManagerApis.grantRuntimePermission(
                    MANAGER_APPLICATION_ID,
                    Manifest.permission.WRITE_SECURE_SETTINGS, getUserId(callingUid)
                )
            } catch (e: RemoteException) {
                LOGGER.w(e, "grant WRITE_SECURE_SETTINGS")
            }
        }
        try {
            application.bindApplication(reply)
        } catch (e: Throwable) {
            LOGGER.w(e, "attachApplication")
        }
    }

    /**
     * 显示权限确认对话框
     * Show permission confirmation dialog
     *
     *
     * 功能说明 Features：
     *
     *  * 启动Manager应用的权限请求Activity
     *  * 传递应用信息和请求代码
     *  * 处理工作配置文件用户的特殊情况
     *  * 如果Manager未安装且非工作配置文件，则直接拒绝
     *
     *
     * @param requestCode 请求代码
     * @param clientRecord 客户端记录
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param userId 用户ID
     */
    override fun showPermissionConfirmation(
        requestCode: Int,
        clientRecord: ClientRecord,
        callingUid: Int,
        callingPid: Int,
        userId: Int
    ) {
        val ai = PackageManagerApis.getApplicationInfoNoThrow(clientRecord.packageName, 0, userId)
            ?: return

        val pi = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, userId)
        val userInfo = UserManagerApis.getUserInfo(userId)
        val isWorkProfileUser =
            if (BuildUtils.atLeast30()) ("android.os.usertype.profile.MANAGED" == userInfo.userType) else (userInfo.flags and UserInfo.FLAG_MANAGED_PROFILE) != 0
        if (pi == null && !isWorkProfileUser) {
            LOGGER.w("在非工作配置文件用户 %d 中未找到管理器，撤销权限", userId)
            clientRecord.dispatchRequestPermissionResult(requestCode, false)
            return
        }

        val intent = Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
            .setPackage(MANAGER_APPLICATION_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            .putExtra("uid", callingUid)
            .putExtra("pid", callingPid)
            .putExtra("requestCode", requestCode)
            .putExtra("applicationInfo", ai)
        ActivityManagerApis.startActivityNoThrow(intent, null, if (isWorkProfileUser) 0 else userId)
    }

    /**
     * 分发权限确认结果
     * Dispatch permission confirmation result
     *
     *
     * 功能说明 Features：
     *
     *  * 仅Manager应用可调用
     *  * 更新客户端权限状态
     *  * 通知客户端权限结果
     *  * 保存永久权限配置（非一次性）
     *  * 授予或撤销系统权限
     *
     *
     * @param requestUid 请求权限的应用UID
     * @param requestPid 请求权限的应用PID
     * @param requestCode 请求代码
     * @param data 包含权限结果的数据
     * @throws RemoteException IPC异常
     */
    @Throws(RemoteException::class)
    override fun dispatchPermissionConfirmationResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        data: Bundle?
    ) {
        if (getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("dispatchPermissionConfirmationResult 不是从管理器包调用的")
            return
        }

        if (data == null) {
            return
        }

        val allowed = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED)
        val onetime = data.getBoolean(StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME)

        LOGGER.i(
            "dispatchPermissionConfirmationResult: uid=%d, pid=%d, requestCode=%d, allowed=%s, onetime=%s",
            requestUid, requestPid, requestCode, allowed.toString(), onetime.toString()
        )

        val records: MutableList<ClientRecord> = mClientManager!!.findClients(requestUid)
        val packages = ArrayList<String?>()
        if (records.isEmpty()) {
            LOGGER.w("dispatchPermissionConfirmationResult：未找到 uid %d 的客户端", requestUid)
        } else {
            for (record in records) {
                packages.add(record.packageName)
                record.allowed = allowed
                record.onetime = onetime
                if (record.pid == requestPid) {
                    record.dispatchRequestPermissionResult(requestCode, allowed)
                }
            }
        }

        if (!onetime) {
            mConfigManager!!.update(
                requestUid,
                packages,
                ConfigManager.MASK_PERMISSION,
                if (allowed) ConfigManager.FLAG_ALLOWED else ConfigManager.FLAG_DENIED
            )
        }

        if (!onetime) {
            val userId = getUserId(requestUid)

            for (packageName in PackageManagerApis.getPackagesForUidNoThrow(requestUid)) {
                val pi = PackageManagerApis.getPackageInfoNoThrow(
                    packageName,
                    PackageManager.GET_PERMISSIONS.toLong(),
                    userId
                )
                if (pi == null || pi.requestedPermissions == null || !(pi.requestedPermissions as Array<out String?>).contains(
                        PERMISSION
                    )
                ) {
                    continue
                }

                //                int deviceId = 0;//Context.DEVICE_ID_DEFAULT
                if (allowed) {
                    PermissionManagerApis.grantRuntimePermission(packageName, PERMISSION, userId)
                } else {
                    PermissionManagerApis.revokeRuntimePermission(packageName, PERMISSION, userId)
                }
            }
        }
    }

    /**
     * 获取UID的标志位（内部方法）
     * Get flags for UID (internal method)
     *
     *
     * 查询流程：
     *
     *  1. 先查询配置管理器中的保存的标志
     *  1. 如果未找到且允许运行时权限检查，则检查系统权限
     *
     *
     * @param uid 应用UID
     * @param mask 标志掩码
     * @param allowRuntimePermission 是否允许检查运行时权限
     * @return 标志位
     */
    private fun getFlagsForUidInternal(uid: Int, mask: Int, allowRuntimePermission: Boolean): Int {
        val entry = mConfigManager!!.find(uid)
        if (entry != null) {
            return entry.flags and mask
        }

        if (allowRuntimePermission && (mask and ConfigManager.MASK_PERMISSION) != 0) {
            val userId = getUserId(uid)
            for (packageName in PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                val pi = PackageManagerApis.getPackageInfoNoThrow(
                    packageName,
                    PackageManager.GET_PERMISSIONS.toLong(),
                    userId
                )
                if (pi == null || pi.requestedPermissions == null || !(pi.requestedPermissions as Array<out String?>).contains(
                        PERMISSION
                    )
                ) {
                    continue
                }

                try {
                    if (PermissionManagerApis.checkPermission(
                            PERMISSION,
                            uid
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        return ConfigManager.FLAG_ALLOWED
                    }
                } catch (_: Throwable) {
                    LOGGER.w("getFlagsForUid 失败")
                }
            }
        }
        return 0
    }

    /**
     * 获取UID的标志位
     * Get flags for UID
     *
     *
     * 仅Manager应用可调用
     *
     * @param uid 应用UID
     * @param mask 标志掩码
     * @return 标志位
     */
    override fun getFlagsForUid(uid: Int, mask: Int): Int {
        if (getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("getFlagsForUid 只允许从管理器调用")
            return 0
        }
        return getFlagsForUidInternal(uid, mask, true)
    }

    /**
     * 更新UID的标志位
     * Update flags for UID
     *
     *
     * 功能说明 Features：
     *
     *  * 仅Manager应用可调用
     *  * 更新客户端权限状态
     *  * 授予或撤销系统权限
     *  * 撤销权限时强制停止应用
     *  * 保存配置到文件
     *
     *
     * @param uid 应用UID
     * @param mask 标志掩码
     * @param value 标志值
     * @throws RemoteException IPC异常
     */
    @Throws(RemoteException::class)
    override fun updateFlagsForUid(uid: Int, mask: Int, value: Int) {
        if (getAppId(getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid 只允许从管理器调用")
            return
        }

        val userId = getUserId(uid)

        if ((mask and ConfigManager.MASK_PERMISSION) != 0) {
            val allowed = (value and ConfigManager.FLAG_ALLOWED) != 0
            val denied = (value and ConfigManager.FLAG_DENIED) != 0

            val records: MutableList<ClientRecord> = mClientManager!!.findClients(uid)
            for (record in records) {
                if (allowed) {
                    record.allowed = true
                } else {
                    record.allowed = false
                    ActivityManagerApis.forceStopPackageNoThrow(
                        record.packageName,
                        getUserId(record.uid)
                    )
                    onPermissionRevoked(record.packageName)
                }
            }

            for (packageName in PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                val pi = PackageManagerApis.getPackageInfoNoThrow(
                    packageName,
                    PackageManager.GET_PERMISSIONS.toLong(),
                    userId
                )
                if (pi == null || pi.requestedPermissions == null || !(pi.requestedPermissions as Array<out String?>).contains(
                        PERMISSION
                    )
                ) {
                    continue
                }

                val deviceId = 0 //Context.DEVICE_ID_DEFAULT
                if (allowed) {
                    PermissionManagerApis.grantRuntimePermission(packageName, PERMISSION, userId)
                } else {
                    PermissionManagerApis.revokeRuntimePermission(packageName, PERMISSION, userId)
                }
            }
        }

        mConfigManager!!.update(uid, null, mask, value)
    }

    /**
     * 权限被撤销时的回调
     * Callback when permission is revoked
     *
     * @param packageName 包名
     */
    private fun onPermissionRevoked(packageName: String?) {
        // TODO add runtime permission listener
    }

    /**
     * 获取应用程序列表
     * Get applications list
     *
     *
     * 功能说明 Features：
     *
     *  * 获取请求Stellar权限的应用列表
     *  * 过滤Manager应用
     *  * 包含已授权/拒绝的应用
     *  * 包含声明V3支持的应用
     *  * 支持指定用户或所有用户（userId=-1）
     *
     *
     * @param userId 用户ID，-1表示所有用户
     * @return 应用程序包信息列表
     */
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
                if (MANAGER_APPLICATION_ID == pi.packageName) continue
                if (pi.applicationInfo == null) continue

                val uid = pi.applicationInfo!!.uid
                var flags = 0
                val entry = mConfigManager!!.find(uid)
                if (entry != null) {
                    if (!entry.packages.contains(pi.packageName)) continue
                    flags = entry.flags and ConfigManager.MASK_PERMISSION
                }

                if (flags != 0) {
                    list.add(pi)
                } else if (pi.applicationInfo!!.metaData != null && pi.applicationInfo!!.metaData.getBoolean(
                        "com.stellar.client.V3_SUPPORT",
                        false
                    )
                    && pi.requestedPermissions != null && (pi.requestedPermissions as Array<out String?>).contains(
                        PERMISSION
                    )
                ) {
                    list.add(pi)
                }
            }
        }
        return ParcelableListSlice<PackageInfo?>(list)
    }

    /**
     * Binder事务处理
     * Handle Binder transaction
     *
     *
     * 处理自定义事务代码：
     *
     *  * getApplications: 获取应用列表
     *
     *
     * @param code 事务代码
     * @param data 输入数据
     * @param reply 返回数据
     * @param flags 标志位
     * @return true表示已处理
     * @throws RemoteException IPC异常
     */
    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        //LOGGER.d("transact: code=%d, calling uid=%d", code, Binder.getCallingUid());
        if (code == ServerConstants.BINDER_TRANSACTION_getApplications) {
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR)
            val userId = data.readInt()
            val result = getApplications(userId)
            reply?.let {
                it.writeNoException()
                result.writeToParcel(it, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            }
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    /**
     * 向所有用户的客户端应用发送Binder
     * Send Binder to client apps in all users
     */
    fun sendBinderToClient() {
        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClient(this, userId)
        }
    }

    /**
     * 向所有用户的Manager应用发送Binder
     * Send Binder to Manager app in all users
     */
    fun sendBinderToManager() {
        sendBinderToManger(this)
    }

    companion object {
        /**
         * 服务程序入口
         * Service program entry point
         *
         *
         * 启动流程：
         *
         *  * 设置进程名称为"Stellar_server"
         *  * 准备主Looper
         *  * 创建StellarService实例
         *  * 启动消息循环
         *
         *
         * @param args 命令行参数（未使用）
         */
        @JvmStatic
        fun main(args: Array<String>) {
            DdmHandleAppName.setAppName("stellar_server", 0)

            Looper.prepareMainLooper()
            StellarService()
            Looper.loop()
        }

        /**
         * 等待系统服务就绪
         * Wait for system service to be ready
         *
         *
         * 阻塞当前线程直到指定系统服务可用
         *
         * @param name 系统服务名称
         */
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
            /**
             * 获取Manager应用信息
             * Get Manager application info
             *
             * @return Manager应用的ApplicationInfo，如果未安装则返回null
             */
            get() = PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)

        val managerPackageInfo: PackageInfo?
            /**
             * 获取Manager应用的PackageInfo
             * Get Manager app package info
             *
             * @return Manager应用的PackageInfo，如果未安装则返回null
             */
            get() = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0)

        /**
         * 向指定用户的客户端应用发送Binder
         * Send Binder to client apps in specified user
         *
         *
         * 遍历所有请求Stellar权限的应用并发送Binder
         *
         * @param binder Binder对象
         * @param userId 用户ID
         */
        private fun sendBinderToClient(binder: Binder?, userId: Int) {
            try {
                for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                    PackageManager.GET_PERMISSIONS.toLong(),
                    userId
                )) {
                    if (pi == null || pi.requestedPermissions == null) continue

                    if ((pi.requestedPermissions as Array<out String?>).contains(PERMISSION)) {
                        sendBinderToUserApp(binder, pi.packageName, userId)
                    }
                }
            } catch (tr: Throwable) {
                LOGGER.e("调用 getInstalledPackages 时发生异常", tr = tr)
            }
        }

        /**
         * 向所有用户的Manager应用发送Binder
         * Send Binder to Manager app in all users
         *
         * @param binder Binder对象
         */
        private fun sendBinderToManger(binder: Binder?) {
            for (userId in UserManagerApis.getUserIdsNoThrow()) {
                sendBinderToManger(binder, userId)
            }
        }

        /**
         * 向指定用户的Manager应用发送Binder
         * Send Binder to Manager app in specified user
         *
         * @param binder Binder对象
         * @param userId 用户ID
         */
        fun sendBinderToManger(binder: Binder?, userId: Int) {
            sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId)
        }

        /**
         * 向指定用户的应用发送Binder
         * Send Binder to app in specified user
         *
         *
         * 功能说明 Features：
         *
         *  * 添加到电池优化白名单（30秒）
         *  * 通过ContentProvider发送Binder
         *  * 支持失败重试机制
         *
         *
         * @param binder Binder对象
         * @param packageName 包名
         * @param userId 用户ID
         * @param retry 是否允许重试
         */
        /**
         * 向指定用户的应用发送Binder
         * Send Binder to app in specified user
         *
         * @param binder Binder对象
         * @param packageName 包名
         * @param userId 用户ID
         */
        @JvmOverloads
        fun sendBinderToUserApp(
            binder: Binder?,
            packageName: String?,
            userId: Int,
            retry: Boolean = true
        ) {
            try {
                DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(
                    packageName, (30 * 1000).toLong(), userId,
                    316,  /* PowerExemptionManager#REASON_SHELL */"shell"
                )
                LOGGER.v("将 %d:%s 添加到省电临时白名单 30 秒", userId, packageName)
            } catch (tr: Throwable) {
                LOGGER.e(tr, "添加 %d:%s 到省电临时白名单失败", userId, packageName)
            }

            val name = "$packageName.stellar"
            var provider: IContentProvider? = null

            /*
         When we pass IBinder through binder (and really crossed process), the receive side (here is system_server process)
         will always get a new instance of android.os.BinderProxy.

         In the implementation of getContentProviderExternal and removeContentProviderExternal, received
         IBinder is used as the key of a HashMap. But hashCode() is not implemented by BinderProxy, so
         removeContentProviderExternal will never work.

         Luckily, we can pass null. When token is token, count will be used.
         */
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
                        // For unknown reason, sometimes this could happens
                        // Kill Stellar app and try again could work
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