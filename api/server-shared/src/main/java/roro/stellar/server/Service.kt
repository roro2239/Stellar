package roro.stellar.server

import android.os.Parcel
import android.os.RemoteException
import android.os.SELinux
import android.os.SystemProperties
import android.system.Os
import androidx.annotation.CallSuper
import com.stellar.server.IRemoteProcess
import com.stellar.server.IStellarService
import rikka.hidden.compat.PermissionManagerApis
import roro.stellar.StellarApiConstants
import roro.stellar.server.api.RemoteProcessHolder
import roro.stellar.server.util.Logger
import roro.stellar.server.util.OsUtils
import roro.stellar.server.util.OsUtils.pid
import roro.stellar.server.util.UserHandleCompat.getUserId
import java.io.File
import java.io.IOException

/**
 * Stellar服务抽象基类
 * Stellar Service Abstract Base Class
 *
 *
 * 功能说明 Features：
 *
 *  * 实现IStellarService AIDL接口 - Implements IStellarService AIDL interface
 *  * 提供客户端管理和权限管理基础设施 - Provides client and permission management infrastructure
 *  * 处理Binder事务转发 - Handles Binder transaction forwarding
 *  * 管理远程进程创建 - Manages remote process creation
 *  * 提供权限检查和请求机制 - Provides permission check and request mechanism
 *
 *
 *
 * 核心职责 Core Responsibilities：
 *
 *  * 1. 客户端连接管理 - Client connection management
 *  * 2. 权限验证和授权 - Permission verification and authorization
 *  * 3. Binder事务代理 - Binder transaction proxying
 *  * 4. 系统属性访问 - System property access
 *  * 5. 远程进程创建和管理 - Remote process creation and management
 *
 *
 *
 * 权限机制 Permission Mechanism：
 *
 *  * enforceCallingPermission() - 强制检查调用者权限
 *  * enforceManagerPermission() - 强制检查管理员权限
 *  * checkSelfPermission() - 检查调用者是否已授权
 *  * requestPermission() - 请求权限（触发UI确认）
 *
 *
 *
 * 主要API Main APIs：
 *
 *  * transactRemote() - 转发Binder事务到系统服务
 *  * newProcess() - 创建远程进程
 *  * getSystemProperty() / setSystemProperty() - 系统属性访问
 *  * getSELinuxContext() - 获取SELinux上下文
 *  * checkPermission() - 检查系统权限
 *
 *
 *
 * 子类需实现 Subclasses Must Implement：
 *
 *  * onCreateClientManager() - 创建客户端管理器
 *  * onCreateConfigManager() - 创建配置管理器
 *  * showPermissionConfirmation() - 显示权限确认UI
 *  * checkCallerManagerPermission() - 检查管理员权限（可选）
 *  * checkCallerPermission() - 检查调用者权限（可选）
 *
 *
 * @param <ClientMgr> 客户端管理器类型
 * @param <ConfigMgr> 配置管理器类型
</ConfigMgr></ClientMgr> */
abstract class Service<ClientMgr : ClientManager<ConfigMgr>, ConfigMgr : ConfigManager> :
    IStellarService.Stub() {
    /**
     * 获取配置管理器
     * Get configuration manager
     *
     * @return 配置管理器实例
     */
    /** 配置管理器 Configuration manager  */
    val configManager: ConfigMgr

    /**
     * 获取客户端管理器
     * Get client manager
     *
     * @return 客户端管理器实例
     */
    /** 客户端管理器 Client manager  */
    val clientManager: ClientMgr

    /**
     * 构造服务
     * Construct service
     *
     * 初始化配置管理器和客户端管理器
     * Initializes configuration manager and client manager
     */
    init {
        configManager = onCreateConfigManager()
        clientManager = onCreateClientManager()
    }

    /**
     * 创建客户端管理器（子类实现）
     * Create client manager (subclass implementation)
     *
     * @return 客户端管理器实例
     */
    abstract fun onCreateClientManager(): ClientMgr

    /**
     * 创建配置管理器（子类实现）
     * Create configuration manager (subclass implementation)
     *
     * @return 配置管理器实例
     */
    abstract fun onCreateConfigManager(): ConfigMgr

    /**
     * 检查调用者是否为管理员（子类可覆盖）
     * Check if caller is manager (subclass can override)
     *
     * @param func 函数名（用于日志）
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @return true表示是管理员
     */
    open fun checkCallerManagerPermission(
        func: String?,
        callingUid: Int,
        callingPid: Int
    ): Boolean {
        return false
    }

    /**
     * 强制检查管理员权限
     * Enforce manager permission
     *
     *
     * 通过条件 Pass conditions：
     *
     *  * 调用者PID与服务PID相同（同进程调用）
     *  * checkCallerManagerPermission返回true
     *
     *
     * @param func 函数名（用于日志和异常信息）
     * @throws SecurityException 如果调用者不是管理员
     */
    fun enforceManagerPermission(func: String) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        // 同进程调用自动通过
        if (callingPid == Os.getpid()) {
            return
        }

        // 检查是否为管理员
        if (checkCallerManagerPermission(func, callingUid, callingPid)) {
            return
        }

        // 拒绝访问
        val msg = ("Permission Denial: " + func + " from pid="
                + getCallingPid()
                + " is not manager ")
        LOGGER.w(msg)
        throw SecurityException(msg)
    }

    /**
     * 检查调用者权限（子类可覆盖）
     * Check caller permission (subclass can override)
     *
     * @param func 函数名
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param clientRecord 客户端记录（可能为null）
     * @return true表示有权限
     */
    open fun checkCallerPermission(
        func: String?,
        callingUid: Int,
        callingPid: Int,
        clientRecord: ClientRecord?
    ): Boolean {
        return false
    }

    /**
     * 强制检查调用者权限
     * Enforce calling permission
     *
     *
     * 通过条件 Pass conditions：
     *
     *  * 调用者UID与服务UID相同（同进程或服务本身）
     *  * checkCallerPermission返回true
     *  * 调用者是已连接且已授权的客户端
     *
     *
     * @param func 函数名（用于日志和异常信息）
     * @throws SecurityException 如果调用者无权限
     */
    fun enforceCallingPermission(func: String) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        // 服务自身调用自动通过
        if (callingUid == OsUtils.uid) {
            return
        }

        val clientRecord = clientManager.findClient(callingUid, callingPid)

        // 子类自定义权限检查
        if (checkCallerPermission(func, callingUid, callingPid, clientRecord)) {
            return
        }

        // 检查是否为已连接客户端
        if (clientRecord == null) {
            val msg = ("Permission Denial: " + func + " from pid="
                    + getCallingPid()
                    + " is not an attached client")
            LOGGER.w(msg)
            throw SecurityException(msg)
        }

        // 检查是否已授权
        if (!clientRecord.allowed) {
            val msg = ("Permission Denial: " + func + " from pid="
                    + getCallingPid()
                    + " requires permission")
            LOGGER.w(msg)
            throw SecurityException(msg)
        }
    }

    /**
     * 转发Binder事务到目标服务
     * Forward Binder transaction to target service
     *
     *
     * 工作原理 How It Works：
     *
     *  * 1. 从data中读取目标Binder、事务代码和标志
     *  * 2. 使用clearCallingIdentity清除调用者身份
     *  * 3. 以服务身份执行事务（获得系统权限）
     *  * 4. 恢复调用者身份
     *
     *
     *
     * API版本兼容 API Version Compatibility：
     *
     *  * API < 13: 使用传入的flags参数
     *  * API >= 13: 从data中读取targetFlags
     *
     *
     * @param data 事务数据（包含目标Binder、代码、标志和参数）
     * @param reply 事务返回值
     * @param flags 事务标志（用于API < 13）
     * @throws RemoteException 如果事务失败
     */
    @Throws(RemoteException::class)
    fun transactRemote(data: Parcel, reply: Parcel?, flags: Int) {
        enforceCallingPermission("transactRemote")

        val targetBinder = data.readStrongBinder()
        val targetCode = data.readInt()
        val targetFlags: Int

        val callingUid = getCallingUid()
        val callingPid = getCallingPid()
        val clientRecord = clientManager.findClient(callingUid, callingPid)

        // API 13+从data中读取targetFlags
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


        // 复制data避免影响原始数据
        val newData = Parcel.obtain()
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail())
        } catch (tr: Throwable) {
            LOGGER.w(tr, "appendFrom")
            return
        }
        try {
            // 清除调用者身份，以服务身份执行事务
            val id = clearCallingIdentity()
            targetBinder.transact(targetCode, newData, reply, targetFlags)
            restoreCallingIdentity(id)
        } finally {
            newData.recycle()
        }
    }

    /**
     * 获取服务API版本号
     * Get service API version
     *
     * @return API版本号
     */
    override fun getVersion(): Int {
        enforceCallingPermission("getVersion")
        return StellarApiConstants.SERVER_VERSION
    }

    /**
     * 获取服务进程UID
     * Get service process UID
     *
     * @return 服务UID
     */
    override fun getUid(): Int {
        enforceCallingPermission("getUid")
        return Os.getuid()
    }

    /**
     * 检查服务是否拥有指定权限
     * Check if service has specified permission
     *
     * @param permission 权限名称
     * @return 权限检查结果（PackageManager.PERMISSION_GRANTED或PERMISSION_DENIED）
     * @throws RemoteException 如果检查失败
     */
    @Throws(RemoteException::class)
    override fun checkPermission(permission: String?): Int {
        enforceCallingPermission("checkPermission")
        return PermissionManagerApis.checkPermission(permission, Os.getuid())
    }

    /**
     * 获取服务进程的SELinux上下文
     * Get service process SELinux context
     *
     * @return SELinux上下文字符串
     * @throws IllegalStateException 如果获取失败
     */
    override fun getSELinuxContext(): String? {
        enforceCallingPermission("getSELinuxContext")

        try {
            return SELinux.getContext()
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }

    /**
     * 获取Manager应用版本名称（子类实现）
     * Get Manager app version name (subclass implementation)
     *
     * @return 版本名称字符串
     */
    abstract val managerVersionName: String?

    /**
     * 获取Manager应用版本代码（子类实现）
     * Get Manager app version code (subclass implementation)
     *
     * @return 版本代码
     */
    abstract val managerVersionCode: Int

    /**
     * 获取Manager应用的版本名称
     * Get Manager app version name
     *
     * @return 版本名称（如"1.0.0"）
     */
    override fun getVersionName(): String? {
        enforceCallingPermission("getVersionName")
        return this.managerVersionName
    }

    /**
     * 获取Manager应用的版本代码
     * Get Manager app version code
     *
     * @return 版本代码
     */
    override fun getVersionCode(): Int {
        enforceCallingPermission("getVersionCode")
        return this.managerVersionCode
    }

    /**
     * 获取系统属性
     * Get system property
     *
     * @param name 属性名称
     * @param defaultValue 默认值（属性不存在时返回）
     * @return 属性值
     * @throws IllegalStateException 如果读取失败
     */
    override fun getSystemProperty(name: String?, defaultValue: String?): String {
        enforceCallingPermission("getSystemProperty")

        try {
            return SystemProperties.get(name, defaultValue)
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }

    /**
     * 设置系统属性
     * Set system property
     *
     * @param name 属性名称
     * @param value 属性值
     * @throws IllegalStateException 如果设置失败
     */
    override fun setSystemProperty(name: String?, value: String?) {
        enforceCallingPermission("setSystemProperty")

        try {
            SystemProperties.set(name, value)
        } catch (tr: Throwable) {
            throw IllegalStateException(tr.message)
        }
    }


    /**
     * 检查调用者是否已授权
     * Check if caller has permission
     *
     * @return true表示已授权
     */
    override fun checkSelfPermission(): Boolean {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        // 服务自身调用自动通过
        if (callingUid == OsUtils.uid || callingPid == pid) {
            return true
        }

        return clientManager.requireClient(callingUid, callingPid).allowed
    }

    /**
     * 请求权限
     * Request permission
     *
     *
     * 处理流程 Process Flow：
     *
     *  * 1. 如果是服务自身调用，直接返回
     *  * 2. 如果客户端已授权，直接返回成功结果
     *  * 3. 如果配置中已拒绝，直接返回失败结果
     *  * 4. 否则显示权限确认UI
     *
     *
     * @param requestCode 请求码（用于识别回调）
     */
    override fun requestPermission(requestCode: Int) {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()
        val userId = getUserId(callingUid)

        // 服务自身调用直接返回
        if (callingUid == OsUtils.uid || callingPid == pid) {
            return
        }

        val clientRecord = clientManager.requireClient(callingUid, callingPid)

        // 已授权，直接返回成功
        if (clientRecord.allowed) {
            clientRecord.dispatchRequestPermissionResult(requestCode,
                allowed = true,
                onetime = false
            )
            return
        }

        // 配置中已拒绝，直接返回失败
        val entry = configManager.find(callingUid)
        if (entry != null && entry.isDenied) {
            clientRecord.dispatchRequestPermissionResult(requestCode,
                allowed = false,
                onetime = false
            )
            return
        }

        // 显示权限确认UI
        showPermissionConfirmation(requestCode, clientRecord, callingUid, callingPid, userId)
    }

    /**
     * 显示权限确认UI（子类实现）
     * Show permission confirmation UI (subclass implementation)
     *
     * @param requestCode 请求码
     * @param clientRecord 客户端记录
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param userId 用户ID
     */
    abstract fun showPermissionConfirmation(
        requestCode: Int, clientRecord: ClientRecord, callingUid: Int, callingPid: Int, userId: Int
    )

    /**
     * 检查是否应该显示权限请求说明
     * Check if should show request permission rationale
     *
     * @return true表示应该显示（即之前已被拒绝）
     */
    override fun shouldShowRequestPermissionRationale(): Boolean {
        val callingUid = getCallingUid()
        val callingPid = getCallingPid()

        // 服务自身调用总是返回true
        if (callingUid == OsUtils.uid || callingPid == pid) {
            return true
        }

        clientManager.requireClient(callingUid, callingPid)

        // 检查配置中是否已拒绝
        val entry = configManager.find(callingUid)
        return entry != null && entry.isDenied
    }

    /**
     * 创建远程进程
     * Create remote process
     *
     *
     * 功能 Features：
     *
     *  * 在服务端创建进程（拥有服务权限）
     *  * 返回RemoteProcessHolder供客户端访问
     *  * 监听客户端死亡并自动清理进程
     *
     *
     * @param cmd 命令数组
     * @param env 环境变量数组（可选）
     * @param dir 工作目录（可选）
     * @return 远程进程接口
     * @throws IllegalStateException 如果进程创建失败
     */
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

        // 创建进程
        val process: Process
        try {
            process = Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
        } catch (e: IOException) {
            throw IllegalStateException(e.message)
        }

        // 获取客户端token用于监听死亡
        val clientRecord = clientManager.findClient(getCallingUid(), getCallingPid())
        val token = clientRecord?.client?.asBinder()

        return RemoteProcessHolder(process, token)
    }

    /**
     * 处理Binder事务
     * Handle Binder transaction
     *
     *
     * 特殊处理 Special Handling：
     *
     *  * BINDER_TRANSACTION_transact - 转发事务到系统服务
     *  * code=14 (attachApplication <= v12) - 兼容旧版本客户端连接
     *
     *
     * @param code 事务代码
     * @param data 事务数据
     * @param reply 返回数据
     * @param flags 事务标志
     * @return true表示已处理
     * @throws RemoteException 如果处理失败
     */
    @CallSuper
    @Throws(RemoteException::class)
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == StellarApiConstants.BINDER_TRANSACTION_transact) {
            // 转发事务到系统服务
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR)
            transactRemote(data, reply, flags)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        protected val LOGGER: Logger = Logger("Service")
    }
}