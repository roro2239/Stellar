package com.stellar.server;

import com.stellar.server.IRemoteProcess;
import com.stellar.server.IStellarApplication;

// Stellar 服务主接口
interface IStellarService {

    // 获取 Stellar 服务版本号
    int getVersion() = 2;

    // 获取 Stellar 服务 UID
    int getUid() = 3;

    // 获取 Stellar 服务的 SELinux 上下文
    String getSELinuxContext() = 8;

    // 获取管理器版本名
    String getVersionName() = 18;

    // 获取管理器版本号
    int getVersionCode() = 19;


    // 检查服务端是否拥有特定权限
    int checkPermission(String permission) = 4;

    // 是否应该显示权限请求说明
    boolean shouldShowRequestPermissionRationale() = 16;

    // 获取支持授予的权限
    String[] getSupportedPermissions() = 110;

    // 检查应用自身是否被授予某项权限
    boolean checkSelfPermission(String permission) = 111;

    // 请求权限
    void requestPermission(String permission, int requestCode) = 112;


    // 创建远程进程
    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 7;


    // 获取系统 prop 值
    String getSystemProperty(in String name, in String defaultValue) = 9;

    // 设置系统 prop 值
    void setSystemProperty(in String name, in String value) = 10;


    // 附加应用到服务
    void attachApplication(in IStellarApplication application, in Bundle args) = 17;


    // **内部使用**

    // 退出服务进程
    void exit() = 100;

    // 分发权限确认结果
    oneway void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, in Bundle data) = 104;

    // 获取 UID 的权限标志
    int getFlagForUid(int uid, String permission) = 105;

    // 更新 UID 的权限标志
    void updateFlagForUid(int uid, String permission, int flag) = 106;

    // 授予运行时权限
    void grantRuntimePermission(String packageName, String permissionName, int userId) = 107;

    // 撤销运行时权限
    void revokeRuntimePermission(String packageName, String permissionName, int userId) = 108;

}
