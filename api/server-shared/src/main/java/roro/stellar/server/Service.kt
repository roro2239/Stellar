package roro.stellar.server;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.SystemProperties;
import android.system.Os;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.stellar.server.IRemoteProcess;
import com.stellar.server.IStellarApplication;
import com.stellar.server.IStellarService;

import rikka.hidden.compat.PermissionManagerApis;
import roro.stellar.StellarApiConstants;
import roro.stellar.server.api.RemoteProcessHolder;
import roro.stellar.server.util.Logger;
import roro.stellar.server.util.OsUtils;
import roro.stellar.server.util.UserHandleCompat;

/**
 * Stellar服务抽象基类
 * Stellar Service Abstract Base Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>实现IStellarService AIDL接口 - Implements IStellarService AIDL interface</li>
 * <li>提供客户端管理和权限管理基础设施 - Provides client and permission management infrastructure</li>
 * <li>处理Binder事务转发 - Handles Binder transaction forwarding</li>
 * <li>管理远程进程创建 - Manages remote process creation</li>
 * <li>提供权限检查和请求机制 - Provides permission check and request mechanism</li>
 * </ul>
 * 
 * <p>核心职责 Core Responsibilities：</p>
 * <ul>
 * <li>1. 客户端连接管理 - Client connection management</li>
 * <li>2. 权限验证和授权 - Permission verification and authorization</li>
 * <li>3. Binder事务代理 - Binder transaction proxying</li>
 * <li>4. 系统属性访问 - System property access</li>
 * <li>5. 远程进程创建和管理 - Remote process creation and management</li>
 * </ul>
 * 
 * <p>权限机制 Permission Mechanism：</p>
 * <ul>
 * <li>enforceCallingPermission() - 强制检查调用者权限</li>
 * <li>enforceManagerPermission() - 强制检查管理员权限</li>
 * <li>checkSelfPermission() - 检查调用者是否已授权</li>
 * <li>requestPermission() - 请求权限（触发UI确认）</li>
 * </ul>
 * 
 * <p>主要API Main APIs：</p>
 * <ul>
 * <li>transactRemote() - 转发Binder事务到系统服务</li>
 * <li>newProcess() - 创建远程进程</li>
 * <li>getSystemProperty() / setSystemProperty() - 系统属性访问</li>
 * <li>getSELinuxContext() - 获取SELinux上下文</li>
 * <li>checkPermission() - 检查系统权限</li>
 * </ul>
 * 
 * <p>子类需实现 Subclasses Must Implement：</p>
 * <ul>
 * <li>onCreateClientManager() - 创建客户端管理器</li>
 * <li>onCreateConfigManager() - 创建配置管理器</li>
 * <li>showPermissionConfirmation() - 显示权限确认UI</li>
 * <li>checkCallerManagerPermission() - 检查管理员权限（可选）</li>
 * <li>checkCallerPermission() - 检查调用者权限（可选）</li>
 * </ul>
 * 
 * @param <ClientMgr> 客户端管理器类型
 * @param <ConfigMgr> 配置管理器类型
 */
public abstract class Service<
        ClientMgr extends ClientManager<ConfigMgr>,
        ConfigMgr extends ConfigManager> extends IStellarService.Stub {

    /** 配置管理器 Configuration manager */
    private final ConfigMgr configManager;
    
    /** 客户端管理器 Client manager */
    private final ClientMgr clientManager;

    protected static final Logger LOGGER = new Logger("Service");

    /**
     * 构造服务
     * Construct service
     * 
     * 初始化配置管理器和客户端管理器
     * Initializes configuration manager and client manager
     */
    public Service() {
        configManager = onCreateConfigManager();
        clientManager = onCreateClientManager();
    }

    /**
     * 创建客户端管理器（子类实现）
     * Create client manager (subclass implementation)
     * 
     * @return 客户端管理器实例
     */
    public abstract ClientMgr onCreateClientManager();

    /**
     * 创建配置管理器（子类实现）
     * Create configuration manager (subclass implementation)
     * 
     * @return 配置管理器实例
     */
    public abstract ConfigMgr onCreateConfigManager();

    /**
     * 获取客户端管理器
     * Get client manager
     * 
     * @return 客户端管理器实例
     */
    public final ClientMgr getClientManager() {
        return clientManager;
    }

    /**
     * 获取配置管理器
     * Get configuration manager
     * 
     * @return 配置管理器实例
     */
    public ConfigMgr getConfigManager() {
        return configManager;
    }

    /**
     * 检查调用者是否为管理员（子类可覆盖）
     * Check if caller is manager (subclass can override)
     * 
     * @param func 函数名（用于日志）
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @return true表示是管理员
     */
    public boolean checkCallerManagerPermission(String func, int callingUid, int callingPid) {
        return false;
    }

    /**
     * 强制检查管理员权限
     * Enforce manager permission
     * 
     * <p>通过条件 Pass conditions：</p>
     * <ul>
     * <li>调用者PID与服务PID相同（同进程调用）</li>
     * <li>checkCallerManagerPermission返回true</li>
     * </ul>
     * 
     * @param func 函数名（用于日志和异常信息）
     * @throws SecurityException 如果调用者不是管理员
     */
    public final void enforceManagerPermission(String func) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        // 同进程调用自动通过
        if (callingPid == Os.getpid()) {
            return;
        }

        // 检查是否为管理员
        if (checkCallerManagerPermission(func, callingUid, callingPid)) {
            return;
        }

        // 拒绝访问
        String msg = "Permission Denial: " + func + " from pid="
                + Binder.getCallingPid()
                + " is not manager ";
        LOGGER.w(msg);
        throw new SecurityException(msg);
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
    public boolean checkCallerPermission(String func, int callingUid, int callingPid, @Nullable ClientRecord clientRecord) {
        return false;
    }

    /**
     * 强制检查调用者权限
     * Enforce calling permission
     * 
     * <p>通过条件 Pass conditions：</p>
     * <ul>
     * <li>调用者UID与服务UID相同（同进程或服务本身）</li>
     * <li>checkCallerPermission返回true</li>
     * <li>调用者是已连接且已授权的客户端</li>
     * </ul>
     * 
     * @param func 函数名（用于日志和异常信息）
     * @throws SecurityException 如果调用者无权限
     */
    public final void enforceCallingPermission(String func) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        // 服务自身调用自动通过
        if (callingUid == OsUtils.getUid()) {
            return;
        }

        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);

        // 子类自定义权限检查
        if (checkCallerPermission(func, callingUid, callingPid, clientRecord)) {
            return;
        }

        // 检查是否为已连接客户端
        if (clientRecord == null) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid()
                    + " is not an attached client";
            LOGGER.w(msg);
            throw new SecurityException(msg);
        }

        // 检查是否已授权
        if (!clientRecord.allowed) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid()
                    + " requires permission";
            LOGGER.w(msg);
            throw new SecurityException(msg);
        }
    }

    /**
     * 转发Binder事务到目标服务
     * Forward Binder transaction to target service
     * 
     * <p>工作原理 How It Works：</p>
     * <ul>
     * <li>1. 从data中读取目标Binder、事务代码和标志</li>
     * <li>2. 使用clearCallingIdentity清除调用者身份</li>
     * <li>3. 以服务身份执行事务（获得系统权限）</li>
     * <li>4. 恢复调用者身份</li>
     * </ul>
     * 
     * <p>API版本兼容 API Version Compatibility：</p>
     * <ul>
     * <li>API < 13: 使用传入的flags参数</li>
     * <li>API >= 13: 从data中读取targetFlags</li>
     * </ul>
     * 
     * @param data 事务数据（包含目标Binder、代码、标志和参数）
     * @param reply 事务返回值
     * @param flags 事务标志（用于API < 13）
     * @throws RemoteException 如果事务失败
     */
    public final void transactRemote(Parcel data, Parcel reply, int flags) throws RemoteException {
        enforceCallingPermission("transactRemote");

        IBinder targetBinder = data.readStrongBinder();
        int targetCode = data.readInt();
        int targetFlags;

        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        ClientRecord clientRecord = clientManager.findClient(callingUid, callingPid);

        // API 13+从data中读取targetFlags
        if (clientRecord != null && clientRecord.apiVersion >= 13) {
            targetFlags = data.readInt();
        } else {
            targetFlags = flags;
        }

        LOGGER.d("transact: uid=%d, descriptor=%s, code=%d", Binder.getCallingUid(), targetBinder.getInterfaceDescriptor(), targetCode);
        
        // 复制data避免影响原始数据
        Parcel newData = Parcel.obtain();
        try {
            newData.appendFrom(data, data.dataPosition(), data.dataAvail());
        } catch (Throwable tr) {
            LOGGER.w(tr, "appendFrom");
            return;
        }
        try {
            // 清除调用者身份，以服务身份执行事务
            long id = Binder.clearCallingIdentity();
            targetBinder.transact(targetCode, newData, reply, targetFlags);
            Binder.restoreCallingIdentity(id);
        } finally {
            newData.recycle();
        }
    }

    /**
     * 获取服务API版本号
     * Get service API version
     * 
     * @return API版本号
     */
    @Override
    public final int getVersion() {
        enforceCallingPermission("getVersion");
        return StellarApiConstants.SERVER_VERSION;
    }

    /**
     * 获取服务进程UID
     * Get service process UID
     * 
     * @return 服务UID
     */
    @Override
    public final int getUid() {
        enforceCallingPermission("getUid");
        return Os.getuid();
    }

    /**
     * 检查服务是否拥有指定权限
     * Check if service has specified permission
     * 
     * @param permission 权限名称
     * @return 权限检查结果（PackageManager.PERMISSION_GRANTED或PERMISSION_DENIED）
     * @throws RemoteException 如果检查失败
     */
    @Override
    public final int checkPermission(String permission) throws RemoteException {
        enforceCallingPermission("checkPermission");
        return PermissionManagerApis.checkPermission(permission, Os.getuid());
    }

    /**
     * 获取服务进程的SELinux上下文
     * Get service process SELinux context
     * 
     * @return SELinux上下文字符串
     * @throws IllegalStateException 如果获取失败
     */
    @Override
    public final String getSELinuxContext() {
        enforceCallingPermission("getSELinuxContext");

        try {
            return SELinux.getContext();
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }

    /**
     * 获取Manager应用版本名称（子类实现）
     * Get Manager app version name (subclass implementation)
     * 
     * @return 版本名称字符串
     */
    public abstract String getManagerVersionName();

    /**
     * 获取Manager应用版本代码（子类实现）
     * Get Manager app version code (subclass implementation)
     * 
     * @return 版本代码
     */
    public abstract int getManagerVersionCode();

    /**
     * 获取Manager应用的版本名称
     * Get Manager app version name
     * 
     * @return 版本名称（如"1.0.0"）
     */
    @Override
    public final String getVersionName() {
        enforceCallingPermission("getVersionName");
        return getManagerVersionName();
    }

    /**
     * 获取Manager应用的版本代码
     * Get Manager app version code
     * 
     * @return 版本代码
     */
    @Override
    public final int getVersionCode() {
        enforceCallingPermission("getVersionCode");
        return getManagerVersionCode();
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
    @Override
    public final String getSystemProperty(String name, String defaultValue) {
        enforceCallingPermission("getSystemProperty");

        try {
            return SystemProperties.get(name, defaultValue);
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
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
    @Override
    public final void setSystemProperty(String name, String value) {
        enforceCallingPermission("setSystemProperty");

        try {
            SystemProperties.set(name, value);
        } catch (Throwable tr) {
            throw new IllegalStateException(tr.getMessage());
        }
    }



    /**
     * 检查调用者是否已授权
     * Check if caller has permission
     * 
     * @return true表示已授权
     */
    @Override
    public final boolean checkSelfPermission() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        // 服务自身调用自动通过
        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return true;
        }

        return clientManager.requireClient(callingUid, callingPid).allowed;
    }

    /**
     * 请求权限
     * Request permission
     * 
     * <p>处理流程 Process Flow：</p>
     * <ul>
     * <li>1. 如果是服务自身调用，直接返回</li>
     * <li>2. 如果客户端已授权，直接返回成功结果</li>
     * <li>3. 如果配置中已拒绝，直接返回失败结果</li>
     * <li>4. 否则显示权限确认UI</li>
     * </ul>
     * 
     * @param requestCode 请求码（用于识别回调）
     */
    @Override
    public final void requestPermission(int requestCode) {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        int userId = UserHandleCompat.getUserId(callingUid);

        // 服务自身调用直接返回
        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return;
        }

        ClientRecord clientRecord = clientManager.requireClient(callingUid, callingPid);

        // 已授权，直接返回成功
        if (clientRecord.allowed) {
            clientRecord.dispatchRequestPermissionResult(requestCode, true);
            return;
        }

        // 配置中已拒绝，直接返回失败
        ConfigPackageEntry entry = configManager.find(callingUid);
        if (entry != null && entry.isDenied()) {
            clientRecord.dispatchRequestPermissionResult(requestCode, false);
            return;
        }

        // 显示权限确认UI
        showPermissionConfirmation(requestCode, clientRecord, callingUid, callingPid, userId);
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
    public abstract void showPermissionConfirmation(
            int requestCode, @NonNull ClientRecord clientRecord, int callingUid, int callingPid, int userId);

    /**
     * 检查是否应该显示权限请求说明
     * Check if should show request permission rationale
     * 
     * @return true表示应该显示（即之前已被拒绝）
     */
    @Override
    public final boolean shouldShowRequestPermissionRationale() {
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();

        // 服务自身调用总是返回true
        if (callingUid == OsUtils.getUid() || callingPid == OsUtils.getPid()) {
            return true;
        }

        clientManager.requireClient(callingUid, callingPid);

        // 检查配置中是否已拒绝
        ConfigPackageEntry entry = configManager.find(callingUid);
        return entry != null && entry.isDenied();
    }

    /**
     * 创建远程进程
     * Create remote process
     * 
     * <p>功能 Features：</p>
     * <ul>
     * <li>在服务端创建进程（拥有服务权限）</li>
     * <li>返回RemoteProcessHolder供客户端访问</li>
     * <li>监听客户端死亡并自动清理进程</li>
     * </ul>
     * 
     * @param cmd 命令数组
     * @param env 环境变量数组（可选）
     * @param dir 工作目录（可选）
     * @return 远程进程接口
     * @throws IllegalStateException 如果进程创建失败
     */
    @Override
    public final IRemoteProcess newProcess(String[] cmd, String[] env, String dir) {
        enforceCallingPermission("newProcess");

        LOGGER.d("newProcess: uid=%d, cmd=%s, env=%s, dir=%s", Binder.getCallingUid(), Arrays.toString(cmd), Arrays.toString(env), dir);

        // 创建进程
        java.lang.Process process;
        try {
            process = Runtime.getRuntime().exec(cmd, env, dir != null ? new File(dir) : null);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage());
        }

        // 获取客户端token用于监听死亡
        ClientRecord clientRecord = clientManager.findClient(Binder.getCallingUid(), Binder.getCallingPid());
        IBinder token = clientRecord != null ? clientRecord.client.asBinder() : null;

        return new RemoteProcessHolder(process, token);
    }

    /**
     * 处理Binder事务
     * Handle Binder transaction
     * 
     * <p>特殊处理 Special Handling：</p>
     * <ul>
     * <li>BINDER_TRANSACTION_transact - 转发事务到系统服务</li>
     * <li>code=14 (attachApplication <= v12) - 兼容旧版本客户端连接</li>
     * </ul>
     * 
     * @param code 事务代码
     * @param data 事务数据
     * @param reply 返回数据
     * @param flags 事务标志
     * @return true表示已处理
     * @throws RemoteException 如果处理失败
     */
    @CallSuper
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == StellarApiConstants.BINDER_TRANSACTION_transact) {
            // 转发事务到系统服务
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR);
            transactRemote(data, reply, flags);
            return true;
        } else if (code == 14 /* attachApplication <= v12 */) {
            // 兼容API v12及以下版本的attachApplication
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR);
            IBinder binder = data.readStrongBinder();
            String packageName = data.readString();
            Bundle args = new Bundle();
            args.putString(StellarApiConstants.ATTACH_APPLICATION_PACKAGE_NAME, packageName);
            args.putInt(StellarApiConstants.ATTACH_APPLICATION_API_VERSION, -1);
            attachApplication(IStellarApplication.Stub.asInterface(binder), args);
            reply.writeNoException();
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}

