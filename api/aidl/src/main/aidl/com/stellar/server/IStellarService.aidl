package com.stellar.server;

import com.stellar.server.IRemoteProcess;
import com.stellar.server.IStellarApplication;

/**
 * Stellar服务主接口
 * Stellar Service Main Interface
 * 
 * 功能说明：
 * - 定义Stellar服务端提供的所有API方法
 * - 运行在特权进程（root/adb）中
 * - 客户端通过此接口调用特权操作
 * 
 * 接口分类：
 * 1. 服务信息查询：getVersion, getUid, getSELinuxContext, getVersionName, getVersionCode
 * 2. 权限管理：checkPermission, checkSelfPermission, requestPermission
 * 3. 系统属性：getSystemProperty, setSystemProperty
 * 4. 进程管理：newProcess
 * 5. 应用连接：attachApplication
 * 6. 内部管理：exit, getFlagsForUid, updateFlagsForUid
 */
interface IStellarService {

    // ============================================
    // 服务信息查询 Service Information
    // ============================================
    
    /**
     * 获取Stellar服务版本号
     * Get Stellar service version
     * @return 服务API版本号
     */
    int getVersion() = 2;

    /**
     * 获取Stellar服务运行的UID
     * Get UID that Stellar service runs as
     * @return UID（0=root, 2000=shell/adb）
     */
    int getUid() = 3;

    /**
     * 获取Stellar服务的SELinux上下文
     * Get SELinux context of Stellar service
     * @return SELinux上下文字符串
     */
    String getSELinuxContext() = 8;

    /**
     * 获取Manager应用的版本名称
     * Get Manager app version name
     * @return 版本名称（如"1.0.0"）
     */
    String getVersionName() = 18;

    /**
     * 获取Manager应用的版本代码
     * Get Manager app version code
     * @return 版本代码
     */
    int getVersionCode() = 19;

    // ============================================
    // 权限管理 Permission Management
    // ============================================
    
    /**
     * 检查服务端是否拥有特定权限
     * Check if service has specific permission
     * @param permission 权限名称
     * @return PackageManager.PERMISSION_GRANTED 或 PERMISSION_DENIED
     */
    int checkPermission(String permission) = 4;

    /**
     * 检查调用者是否已授权
     * Check if caller is authorized
     * @return true表示已授权
     */
    boolean checkSelfPermission() = 15;

    /**
     * 请求权限授权
     * Request permission authorization
     * @param requestCode 请求码，用于匹配回调结果
     */
    void requestPermission(int requestCode) = 14;

    /**
     * 是否应该显示权限请求说明
     * Whether should show permission request rationale
     * @return true表示应该显示说明
     */
    boolean shouldShowRequestPermissionRationale() = 16;

    // ============================================
    // 进程管理 Process Management
    // ============================================
    
    /**
     * 创建远程进程
     * Create remote process
     * @param cmd 命令参数数组
     * @param env 环境变量数组
     * @param dir 工作目录
     * @return 远程进程接口
     */
    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 7;

    // ============================================
    // 系统属性 System Properties
    // ============================================
    
    /**
     * 获取系统属性值
     * Get system property value
     * @param name 属性名称
     * @param defaultValue 默认值
     * @return 属性值
     */
    String getSystemProperty(in String name, in String defaultValue) = 9;

    /**
     * 设置系统属性值
     * Set system property value
     * 
     * <p>权限说明：</p>
     * <ul>
     * <li>ADB模式(uid=2000): 可写入 debug.*, persist.debug.*, log.*, vendor.debug.*</li>
     * <li>Root模式(uid=0): 可写入大部分属性（ro.* 只读属性除外）</li>
     * </ul>
     * 
     * @param name 属性名称
     * @param value 属性值
     */
    void setSystemProperty(in String name, in String value) = 10;

    // ============================================
    // 应用连接管理 Application Connection
    // ============================================
    
    /**
     * 附加应用到服务
     * Attach application to service
     * @param application 应用回调接口
     * @param args 附加参数
     */
    void attachApplication(in IStellarApplication application,in Bundle args) = 17;

    // ============================================
    // 内部管理方法 Internal Management (仅供管理器使用)
    // ============================================
    
    /**
     * 退出服务进程
     * Exit service process
     */
    void exit() = 100;

    /**
     * 分发权限确认结果（管理器内部使用）
     * Dispatch permission confirmation result (internal use)
     */
    oneway void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, in Bundle data) = 104;

    /**
     * 获取UID的标志位（管理器内部使用）
     * Get flags for UID (internal use)
     */
    int getFlagsForUid(int uid, int mask) = 105;

    /**
     * 更新UID的标志位（管理器内部使用）
     * Update flags for UID (internal use)
     */
    void updateFlagsForUid(int uid, int mask, int value) = 106;
 }

