/**
 * Stellar服务核心实现类
 * Stellar Service Core Implementation
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>Stellar服务端的主要实现 - Main implementation of Stellar service</li>
 * <li>运行在特权进程中（root或adb） - Runs in privileged process (root or adb)</li>
 * <li>管理客户端连接和权限 - Manages client connections and permissions</li>
 * <li>提供系统服务访问能力 - Provides system service access capabilities</li>
 * <li>处理跨进程Binder事务 - Handles cross-process Binder transactions</li>
 * </ul>
 * 
 * <p>主要职责 Main Responsibilities：</p>
 * <ul>
 * <li>客户端管理：跟踪和管理连接的应用 - Client management: tracks and manages connected apps</li>
 * <li>权限管理：验证和授予API访问权限 - Permission management: validates and grants API access</li>
 * <li>Binder转发：代理系统服务的Binder调用 - Binder forwarding: proxies system service Binder calls</li>
 * <li>进程管理：创建和管理远程进程 - Process management: creates and manages remote processes</li>
 * <li>配置管理：持久化权限和设置 - Configuration management: persists permissions and settings</li>
 * </ul>
 * 
 * <p>启动流程 Startup Flow：</p>
 * <ul>
 * <li>1. 等待关键系统服务就绪 - Wait for critical system services</li>
 * <li>2. 初始化客户端管理器和配置管理器 - Initialize client and config managers</li>
 * <li>3. 监听Manager应用的APK变化 - Monitor Manager app APK changes</li>
 * <li>4. 注册Binder发送器 - Register Binder sender</li>
 * <li>5. 发送Binder到客户端和Manager - Send Binder to clients and Manager</li>
 * </ul>
 * 
 * <p>安全机制 Security Mechanism：</p>
 * <ul>
 * <li>调用者UID/PID验证 - Caller UID/PID validation</li>
 * <li>权限检查和授予流程 - Permission checking and granting flow</li>
 * <li>Manager应用身份验证 - Manager app identity verification</li>
 * </ul>
 */
package roro.stellar.server;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static roro.stellar.StellarApiConstants.ATTACH_APPLICATION_API_VERSION;
import static roro.stellar.StellarApiConstants.ATTACH_APPLICATION_PACKAGE_NAME;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_PERMISSION_GRANTED;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_SECONTEXT;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_UID;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SERVER_VERSION;
import static roro.stellar.StellarApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE;
import static roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;
import static roro.stellar.StellarApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME;
import static roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID;
import static roro.stellar.server.ServerConstants.PERMISSION;

import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.ddm.DdmHandleAppName;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import kotlin.collections.ArraysKt;
import com.stellar.api.BinderContainer;
import com.stellar.common.util.BuildUtils;
import com.stellar.common.util.OsUtils;
import com.stellar.server.IStellarApplication;
import rikka.hidden.compat.ActivityManagerApis;
import rikka.hidden.compat.DeviceIdleControllerApis;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.hidden.compat.UserManagerApis;
import rikka.parcelablelist.ParcelableListSlice;
import roro.stellar.StellarApiConstants;
import roro.stellar.server.api.IContentProviderUtils;
import roro.stellar.server.util.HandlerUtil;
import roro.stellar.server.util.UserHandleCompat;

public class StellarService extends Service<StellarClientManager, StellarConfigManager> {

    /**
     * 服务程序入口
     * Service program entry point
     * 
     * <p>启动流程：</p>
     * <ul>
     * <li>设置进程名称为"Stellar_server"</li>
     * <li>准备主Looper</li>
     * <li>创建StellarService实例</li>
     * <li>启动消息循环</li>
     * </ul>
     * 
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        DdmHandleAppName.setAppName("Stellar_server", 0);

        Looper.prepareMainLooper();
        new StellarService();
        Looper.loop();
    }

    /**
     * 等待系统服务就绪
     * Wait for system service to be ready
     * 
     * <p>阻塞当前线程直到指定系统服务可用</p>
     * 
     * @param name 系统服务名称
     */
    private static void waitSystemService(String name) {
        while (ServiceManager.getService(name) == null) {
            try {
                LOGGER.i("服务 " + name + " 尚未启动，等待 1 秒。");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.w(e.getMessage(), e);
            }
        }
    }

    /**
     * 获取Manager应用信息
     * Get Manager application info
     * 
     * @return Manager应用的ApplicationInfo，如果未安装则返回null
     */
    public static ApplicationInfo getManagerApplicationInfo() {
        return PackageManagerApis.getApplicationInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0);
    }

    /**
     * 获取Manager应用的PackageInfo
     * Get Manager app package info
     * 
     * @return Manager应用的PackageInfo，如果未安装则返回null
     */
    public static PackageInfo getManagerPackageInfo() {
        return PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, 0);
    }

    @SuppressWarnings({"FieldCanBeLocal"})
    private final Handler mainHandler = new Handler(Looper.myLooper());
    //private final Context systemContext = HiddenApiBridge.getSystemContext();
    private final StellarClientManager clientManager;
    private final StellarConfigManager configManager;
    private final int managerAppId;

    /**
     * 构造Stellar服务
     * Construct Stellar service
     * 
     * <p>初始化流程 Initialization flow：</p>
     * <ol>
     * <li>等待系统服务就绪（PackageManager、ActivityManager等）</li>
     * <li>检查Manager应用是否安装</li>
     * <li>创建配置和客户端管理器</li>
     * <li>启动APK变化监听器</li>
     * <li>注册Binder发送器</li>
     * <li>向客户端和Manager发送Binder</li>
     * </ol>
     * 
     * <p>如果Manager应用未安装，服务将退出</p>
     */
    public StellarService() {
        super();

        HandlerUtil.setMainHandler(mainHandler);

        LOGGER.i("正在启动服务器...");

        waitSystemService("package");
        waitSystemService(Context.ACTIVITY_SERVICE);
        waitSystemService(Context.USER_SERVICE);
        waitSystemService(Context.APP_OPS_SERVICE);

        ApplicationInfo ai = getManagerApplicationInfo();
        if (ai == null) {
            System.exit(ServerConstants.MANAGER_APP_NOT_FOUND);
        }

        assert ai != null;
        managerAppId = ai.uid;

        configManager = getConfigManager();
        clientManager = getClientManager();

        ApkChangedObservers.start(ai.sourceDir, () -> {
            if (getManagerApplicationInfo() == null) {
                LOGGER.w("用户 0 中的管理器应用已卸载，正在退出...");
                System.exit(ServerConstants.MANAGER_APP_NOT_FOUND);
            }
        });

        BinderSender.register(this);

        mainHandler.post(() -> {
            sendBinderToClient();
            sendBinderToManager();
        });
    }



    /**
     * 创建客户端管理器
     * Create client manager
     * 
     * @return StellarClientManager实例
     */
    @Override
    public StellarClientManager onCreateClientManager() {
        return new StellarClientManager(getConfigManager());
    }

    /**
     * 创建配置管理器
     * Create config manager
     * 
     * @return StellarConfigManager实例
     */
    @Override
    public StellarConfigManager onCreateConfigManager() {
        return new StellarConfigManager();
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
    @Override
    public boolean checkCallerManagerPermission(String func, int callingUid, int callingPid) {
        return UserHandleCompat.getAppId(callingUid) == managerAppId;
    }

    /**
     * 获取Manager应用的版本名称
     * Get Manager app version name
     * 
     * @return 版本名称字符串（如"1.0.0"），如果未安装则返回"unknown"
     */
    @Override
    public String getManagerVersionName() {
        PackageInfo pi = getManagerPackageInfo();
        if (pi == null) {
            return "unknown";
        }
        // versionName可能为null，需要处理
        return pi.versionName != null ? pi.versionName : "unknown";
    }

    /**
     * 获取Manager应用的版本代码
     * Get Manager app version code
     * 
     * @return 版本代码，如果未安装则返回-1
     */
    @Override
    public int getManagerVersionCode() {
        PackageInfo pi = getManagerPackageInfo();
        if (pi == null) {
            return -1;
        }
        try {
            long versionCode = pi.getLongVersionCode();
            return (int) versionCode;
        } catch (Exception e) {
            // 如果getLongVersionCode失败，返回-1
            return -1;
        }
    }

    /**
     * 检查调用者权限
     * Check calling permission
     * 
     * <p>通过ActivityManager检查调用者是否持有Stellar权限</p>
     * 
     * @return PERMISSION_GRANTED或PERMISSION_DENIED
     */
    private int checkCallingPermission() {
        try {
            return ActivityManagerApis.checkPermission(ServerConstants.PERMISSION,
                    Binder.getCallingPid(),
                    Binder.getCallingUid());
        } catch (Throwable tr) {
            LOGGER.w(tr, "checkCallingPermission");
            return PackageManager.PERMISSION_DENIED;
        }
    }

    /**
     * 检查调用者权限
     * Check caller permission
     * 
     * <p>权限检查优先级：</p>
     * <ol>
     * <li>Manager应用：直接通过</li>
     * <li>已注册客户端：根据clientRecord判断</li>
     * <li>未注册客户端：检查系统权限</li>
     * </ol>
     * 
     * @param func 调用的方法名
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param clientRecord 客户端记录
     * @return true表示有权限
     */
    @Override
    public boolean checkCallerPermission(String func, int callingUid, int callingPid, @Nullable ClientRecord clientRecord) {
        if (UserHandleCompat.getAppId(callingUid) == managerAppId) {
            return true;
        }
        return clientRecord == null && checkCallingPermission() == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 退出服务
     * Exit service
     * 
     * <p>仅Manager应用可调用此方法</p>
     */
    @Override
    public void exit() {
        enforceManagerPermission("exit");
        LOGGER.i("exit");
        System.exit(0);
    }



    /**
     * 绑定应用程序
     * Attach application
     * 
     * <p>功能说明 Features：</p>
     * <ul>
     * <li>注册客户端应用到服务 - Register client app to service</li>
     * <li>验证请求包名归属 - Verify package name ownership</li>
     * <li>返回服务器信息（UID、版本、SELinux上下文等）</li>
     * <li>为Manager应用授予WRITE_SECURE_SETTINGS权限</li>
     * <li>通知客户端绑定结果</li>
     * </ul>
     * 
     * @param application 客户端应用的Binder接口
     * @param args 包含包名和API版本的参数
     */
    @Override
    public void attachApplication(IStellarApplication application, Bundle args) {
        if (application == null || args == null) {
            return;
        }

        String requestPackageName = args.getString(ATTACH_APPLICATION_PACKAGE_NAME);
        if (requestPackageName == null) {
            return;
        }
        int apiVersion = args.getInt(ATTACH_APPLICATION_API_VERSION, -1);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        boolean isManager;
        ClientRecord clientRecord = null;

        List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(callingUid);
        if (!packages.contains(requestPackageName)) {
            LOGGER.w("请求包 " + requestPackageName + " 不属于 uid " + callingUid);
            throw new SecurityException("请求包 " + requestPackageName + " 不属于 uid " + callingUid);
        }

        isManager = MANAGER_APPLICATION_ID.equals(requestPackageName);

        if (clientManager.findClient(callingUid, callingPid) == null) {
            synchronized (this) {
                clientRecord = clientManager.addClient(callingUid, callingPid, application, requestPackageName, apiVersion);
            }
            if (clientRecord == null) {
                LOGGER.w("添加客户端失败");
                return;
            }
        }

        LOGGER.d("attachApplication: %s %d %d", requestPackageName, callingUid, callingPid);

        int replyServerVersion = StellarApiConstants.SERVER_VERSION;
        if (apiVersion == -1) {
            // StellarBinderWrapper has adapted API v13 in dev.rikka.stellar:api 12.2.0, however
            // attachApplication in 12.2.0 is still old, so that server treat the client as pre 13.
            // This finally cause transactRemote fails.
            // So we can pass 12 here to pretend we are v12 server.
            replyServerVersion = 12;
        }

        Bundle reply = new Bundle();
        reply.putInt(BIND_APPLICATION_SERVER_UID, OsUtils.getUid());
        reply.putInt(BIND_APPLICATION_SERVER_VERSION, replyServerVersion);
        reply.putString(BIND_APPLICATION_SERVER_SECONTEXT, OsUtils.getSELinuxContext());
        reply.putInt(BIND_APPLICATION_SERVER_PATCH_VERSION, StellarApiConstants.SERVER_PATCH_VERSION);
        if (!isManager) {
            reply.putBoolean(BIND_APPLICATION_PERMISSION_GRANTED, Objects.requireNonNull(clientRecord).allowed);
            reply.putBoolean(BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false);
        } else {
            try {
                PermissionManagerApis.grantRuntimePermission(MANAGER_APPLICATION_ID,
                        WRITE_SECURE_SETTINGS, UserHandleCompat.getUserId(callingUid));
            } catch (RemoteException e) {
                LOGGER.w(e, "grant WRITE_SECURE_SETTINGS");
            }
        }
        try {
            application.bindApplication(reply);
        } catch (Throwable e) {
            LOGGER.w(e, "attachApplication");
        }
    }

    /**
     * 显示权限确认对话框
     * Show permission confirmation dialog
     * 
     * <p>功能说明 Features：</p>
     * <ul>
     * <li>启动Manager应用的权限请求Activity</li>
     * <li>传递应用信息和请求代码</li>
     * <li>处理工作配置文件用户的特殊情况</li>
     * <li>如果Manager未安装且非工作配置文件，则直接拒绝</li>
     * </ul>
     * 
     * @param requestCode 请求代码
     * @param clientRecord 客户端记录
     * @param callingUid 调用者UID
     * @param callingPid 调用者PID
     * @param userId 用户ID
     */
    @Override
    public void showPermissionConfirmation(int requestCode, @NonNull ClientRecord clientRecord, int callingUid, int callingPid, int userId) {
        ApplicationInfo ai = PackageManagerApis.getApplicationInfoNoThrow(clientRecord.packageName, 0, userId);
        if (ai == null) {
            return;
        }

        PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(MANAGER_APPLICATION_ID, 0, userId);
        UserInfo userInfo = UserManagerApis.getUserInfo(userId);
        boolean isWorkProfileUser = BuildUtils.atLeast30() ?
                "android.os.usertype.profile.MANAGED".equals(userInfo.userType) :
                (userInfo.flags & UserInfo.FLAG_MANAGED_PROFILE) != 0;
        if (pi == null && !isWorkProfileUser) {
            LOGGER.w("在非工作配置文件用户 %d 中未找到管理器，撤销权限", userId);
            clientRecord.dispatchRequestPermissionResult(requestCode, false);
            return;
        }

        Intent intent = new Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
                .setPackage(MANAGER_APPLICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra("uid", callingUid)
                .putExtra("pid", callingPid)
                .putExtra("requestCode", requestCode)
                .putExtra("applicationInfo", ai);
        ActivityManagerApis.startActivityNoThrow(intent, null, isWorkProfileUser ? 0 : userId);
    }

    /**
     * 分发权限确认结果
     * Dispatch permission confirmation result
     * 
     * <p>功能说明 Features：</p>
     * <ul>
     * <li>仅Manager应用可调用</li>
     * <li>更新客户端权限状态</li>
     * <li>通知客户端权限结果</li>
     * <li>保存永久权限配置（非一次性）</li>
     * <li>授予或撤销系统权限</li>
     * </ul>
     * 
     * @param requestUid 请求权限的应用UID
     * @param requestPid 请求权限的应用PID
     * @param requestCode 请求代码
     * @param data 包含权限结果的数据
     * @throws RemoteException IPC异常
     */
    @Override
    public void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, Bundle data) throws RemoteException {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("dispatchPermissionConfirmationResult 不是从管理器包调用的");
            return;
        }

        if (data == null) {
            return;
        }

        boolean allowed = data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED);
        boolean onetime = data.getBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME);

        LOGGER.i("dispatchPermissionConfirmationResult: uid=%d, pid=%d, requestCode=%d, allowed=%s, onetime=%s",
                requestUid, requestPid, requestCode, Boolean.toString(allowed), Boolean.toString(onetime));

        List<ClientRecord> records = clientManager.findClients(requestUid);
        List<String> packages = new ArrayList<>();
        if (records.isEmpty()) {
            LOGGER.w("dispatchPermissionConfirmationResult：未找到 uid %d 的客户端", requestUid);
        } else {
            for (ClientRecord record : records) {
                packages.add(record.packageName);
                record.allowed = allowed;
                record.onetime = onetime;
                if (record.pid == requestPid) {
                    record.dispatchRequestPermissionResult(requestCode, allowed);
                }
            }
        }

        if (!onetime) {
            configManager.update(requestUid, packages, ConfigManager.MASK_PERMISSION, allowed ? ConfigManager.FLAG_ALLOWED : ConfigManager.FLAG_DENIED);
        }

        if (!onetime) {
            int userId = UserHandleCompat.getUserId(requestUid);

            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(requestUid)) {
                PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null || pi.requestedPermissions == null || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

//                int deviceId = 0;//Context.DEVICE_ID_DEFAULT
                if (allowed) {
                    PermissionManagerApis.grantRuntimePermission(packageName, PERMISSION, userId);
                } else {
                    PermissionManagerApis.revokeRuntimePermission(packageName, PERMISSION, userId);
                }
            }
        }
    }

    /**
     * 获取UID的标志位（内部方法）
     * Get flags for UID (internal method)
     * 
     * <p>查询流程：</p>
     * <ol>
     * <li>先查询配置管理器中的保存的标志</li>
     * <li>如果未找到且允许运行时权限检查，则检查系统权限</li>
     * </ol>
     * 
     * @param uid 应用UID
     * @param mask 标志掩码
     * @param allowRuntimePermission 是否允许检查运行时权限
     * @return 标志位
     */
    private int getFlagsForUidInternal(int uid, int mask, boolean allowRuntimePermission) {
        StellarConfig.PackageEntry entry = configManager.find(uid);
        if (entry != null) {
            return entry.flags & mask;
        }

        if (allowRuntimePermission && (mask & ConfigManager.MASK_PERMISSION) != 0) {
            int userId = UserHandleCompat.getUserId(uid);
            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null || pi.requestedPermissions == null || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                try {
                    if (PermissionManagerApis.checkPermission(PERMISSION, uid) == PackageManager.PERMISSION_GRANTED) {
                        return ConfigManager.FLAG_ALLOWED;
                    }
                } catch (Throwable e) {
                    LOGGER.w("getFlagsForUid 失败");
                }
            }
        }
        return 0;
    }

    /**
     * 获取UID的标志位
     * Get flags for UID
     * 
     * <p>仅Manager应用可调用</p>
     * 
     * @param uid 应用UID
     * @param mask 标志掩码
     * @return 标志位
     */
    @Override
    public int getFlagsForUid(int uid, int mask) {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("getFlagsForUid 只允许从管理器调用");
            return 0;
        }
        return getFlagsForUidInternal(uid, mask, true);
    }

    /**
     * 更新UID的标志位
     * Update flags for UID
     * 
     * <p>功能说明 Features：</p>
     * <ul>
     * <li>仅Manager应用可调用</li>
     * <li>更新客户端权限状态</li>
     * <li>授予或撤销系统权限</li>
     * <li>撤销权限时强制停止应用</li>
     * <li>保存配置到文件</li>
     * </ul>
     * 
     * @param uid 应用UID
     * @param mask 标志掩码
     * @param value 标志值
     * @throws RemoteException IPC异常
     */
    @Override
    public void updateFlagsForUid(int uid, int mask, int value) throws RemoteException {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid 只允许从管理器调用");
            return;
        }

        int userId = UserHandleCompat.getUserId(uid);

        if ((mask & ConfigManager.MASK_PERMISSION) != 0) {
            boolean allowed = (value & ConfigManager.FLAG_ALLOWED) != 0;
            boolean denied = (value & ConfigManager.FLAG_DENIED) != 0;

            List<ClientRecord> records = clientManager.findClients(uid);
            for (ClientRecord record : records) {
                if (allowed) {
                    record.allowed = true;
                } else {
                    record.allowed = false;
                    ActivityManagerApis.forceStopPackageNoThrow(record.packageName, UserHandleCompat.getUserId(record.uid));
                    onPermissionRevoked(record.packageName);
                }
            }

            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                PackageInfo pi = PackageManagerApis.getPackageInfoNoThrow(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null || pi.requestedPermissions == null || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                int deviceId = 0;//Context.DEVICE_ID_DEFAULT
                if (allowed) {
                    PermissionManagerApis.grantRuntimePermission(packageName, PERMISSION, userId);
                } else {
                    PermissionManagerApis.revokeRuntimePermission(packageName, PERMISSION, userId);
                }


            }
        }

        configManager.update(uid, null, mask, value);
    }

    /**
     * 权限被撤销时的回调
     * Callback when permission is revoked
     * 
     * @param packageName 包名
     */
    private void onPermissionRevoked(String packageName) {
        // TODO add runtime permission listener
    }

    /**
     * 获取应用程序列表
     * Get applications list
     * 
     * <p>功能说明 Features：</p>
     * <ul>
     * <li>获取请求Stellar权限的应用列表</li>
     * <li>过滤Manager应用</li>
     * <li>包含已授权/拒绝的应用</li>
     * <li>包含声明V3支持的应用</li>
     * <li>支持指定用户或所有用户（userId=-1）</li>
     * </ul>
     * 
     * @param userId 用户ID，-1表示所有用户
     * @return 应用程序包信息列表
     */
    private ParcelableListSlice<PackageInfo> getApplications(int userId) {
        List<PackageInfo> list = new ArrayList<>();
        List<Integer> users = new ArrayList<>();
        if (userId == -1) {
            users.addAll(UserManagerApis.getUserIdsNoThrow());
        } else {
            users.add(userId);
        }

        for (int user : users) {
            for (PackageInfo pi : PackageManagerApis.getInstalledPackagesNoThrow(PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS, user)) {
                if (Objects.equals(MANAGER_APPLICATION_ID, pi.packageName)) continue;
                if (pi.applicationInfo == null) continue;

                int uid = pi.applicationInfo.uid;
                int flags = 0;
                StellarConfig.PackageEntry entry = configManager.find(uid);
                if (entry != null) {
                    if (entry.packages != null && !entry.packages.contains(pi.packageName))
                        continue;
                    flags = entry.flags & ConfigManager.MASK_PERMISSION;
                }

                if (flags != 0) {
                    list.add(pi);
                } else if (pi.applicationInfo.metaData != null
                        && pi.applicationInfo.metaData.getBoolean("com.stellar.client.V3_SUPPORT", false)
                        && pi.requestedPermissions != null
                        && ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    list.add(pi);
                }
            }

        }
        return new ParcelableListSlice<>(list);
    }

    /**
     * Binder事务处理
     * Handle Binder transaction
     * 
     * <p>处理自定义事务代码：</p>
     * <ul>
     * <li>getApplications: 获取应用列表</li>
     * </ul>
     * 
     * @param code 事务代码
     * @param data 输入数据
     * @param reply 返回数据
     * @param flags 标志位
     * @return true表示已处理
     * @throws RemoteException IPC异常
     */
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        //LOGGER.d("transact: code=%d, calling uid=%d", code, Binder.getCallingUid());
        if (code == ServerConstants.BINDER_TRANSACTION_getApplications) {
            data.enforceInterface(StellarApiConstants.BINDER_DESCRIPTOR);
            int userId = data.readInt();
            ParcelableListSlice<PackageInfo> result = getApplications(userId);
            reply.writeNoException();
            result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    /**
     * 向所有用户的客户端应用发送Binder
     * Send Binder to client apps in all users
     */
    void sendBinderToClient() {
        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClient(this, userId);
        }
    }

    /**
     * 向指定用户的客户端应用发送Binder
     * Send Binder to client apps in specified user
     * 
     * <p>遍历所有请求Stellar权限的应用并发送Binder</p>
     * 
     * @param binder Binder对象
     * @param userId 用户ID
     */
    private static void sendBinderToClient(Binder binder, int userId) {
        try {
            for (PackageInfo pi : PackageManagerApis.getInstalledPackagesNoThrow(PackageManager.GET_PERMISSIONS, userId)) {
                if (pi == null || pi.requestedPermissions == null)
                    continue;

                if (ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    sendBinderToUserApp(binder, pi.packageName, userId);
                }
            }
        } catch (Throwable tr) {
            LOGGER.e("调用 getInstalledPackages 时发生异常", tr);
        }
    }

    /**
     * 向所有用户的Manager应用发送Binder
     * Send Binder to Manager app in all users
     */
    void sendBinderToManager() {
        sendBinderToManger(this);
    }

    /**
     * 向所有用户的Manager应用发送Binder
     * Send Binder to Manager app in all users
     * 
     * @param binder Binder对象
     */
    private static void sendBinderToManger(Binder binder) {
        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToManger(binder, userId);
        }
    }

    /**
     * 向指定用户的Manager应用发送Binder
     * Send Binder to Manager app in specified user
     * 
     * @param binder Binder对象
     * @param userId 用户ID
     */
    static void sendBinderToManger(Binder binder, int userId) {
        sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId);
    }

    /**
     * 向指定用户的应用发送Binder
     * Send Binder to app in specified user
     * 
     * @param binder Binder对象
     * @param packageName 包名
     * @param userId 用户ID
     */
    static void sendBinderToUserApp(Binder binder, String packageName, int userId) {
        sendBinderToUserApp(binder, packageName, userId, true);
    }

    /**
     * 向指定用户的应用发送Binder
     * Send Binder to app in specified user
     * 
     * <p>功能说明 Features：</p>
     * <ul>
     * <li>添加到电池优化白名单（30秒）</li>
     * <li>通过ContentProvider发送Binder</li>
     * <li>支持失败重试机制</li>
     * </ul>
     * 
     * @param binder Binder对象
     * @param packageName 包名
     * @param userId 用户ID
     * @param retry 是否允许重试
     */
    static void sendBinderToUserApp(Binder binder, String packageName, int userId, boolean retry) {
        try {
            DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(packageName, 30 * 1000, userId,
                    316/* PowerExemptionManager#REASON_SHELL */, "shell");
            LOGGER.v("将 %d:%s 添加到省电临时白名单 30 秒", userId, packageName);
        } catch (Throwable tr) {
            LOGGER.e(tr, "添加 %d:%s 到省电临时白名单失败", userId, packageName);
        }

        String name = packageName + ".stellar";
        IContentProvider provider = null;

        /*
         When we pass IBinder through binder (and really crossed process), the receive side (here is system_server process)
         will always get a new instance of android.os.BinderProxy.

         In the implementation of getContentProviderExternal and removeContentProviderExternal, received
         IBinder is used as the key of a HashMap. But hashCode() is not implemented by BinderProxy, so
         removeContentProviderExternal will never work.

         Luckily, we can pass null. When token is token, count will be used.
         */
        IBinder token = null;

        try {
            provider = ActivityManagerApis.getContentProviderExternal(name, userId, token, name);
            if (provider == null) {
                LOGGER.e("provider 为 null %s %d", name, userId);
                return;
            }
            if (!provider.asBinder().pingBinder()) {
                LOGGER.e("provider 已失效 %s %d", name, userId);

                if (retry) {
                    // For unknown reason, sometimes this could happens
                    // Kill Stellar app and try again could work
                    ActivityManagerApis.forceStopPackageNoThrow(packageName, userId);
                    LOGGER.e("终止用户 %d 中的 %s 并重试", userId, packageName);
                    Thread.sleep(1000);
                    sendBinderToUserApp(binder, packageName, userId, false);
                }
                return;
            }

            if (!retry) {
                LOGGER.e("重试成功");
            }

            Bundle extra = new Bundle();
            extra.putParcelable("roro.stellar.manager.intent.extra.BINDER", new BinderContainer(binder));

            Bundle reply = IContentProviderUtils.callCompat(provider, null, name, "sendBinder", null, extra);
            if (reply != null) {
                LOGGER.i("已向用户 %d 中的用户应用 %s 发送 binder", userId, packageName);
            } else {
                LOGGER.w("向用户 %d 中的用户应用 %s 发送 binder 失败", userId, packageName);
            }
        } catch (Throwable tr) {
            LOGGER.e(tr, "向用户 %d 中的用户应用 %s 发送 binder 失败", userId, packageName);
        } finally {
            if (provider != null) {
                try {
                    ActivityManagerApis.removeContentProviderExternal(name, token);
                } catch (Throwable tr) {
                    LOGGER.w(tr, "移除 ContentProvider 失败");
                }
            }
        }
    }
}

