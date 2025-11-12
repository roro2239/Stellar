package com.stellar.common.util;

import android.os.SELinux;

/**
 * 操作系统工具类
 * Operating System Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>获取当前进程的UID和PID - Gets current process UID and PID</li>
 * <li>获取SELinux上下文信息 - Gets SELinux context information</li>
 * <li>提供进程标识信息 - Provides process identification information</li>
 * </ul>
 * 
 * <p>使用场景 Use Cases：</p>
 * <ul>
 * <li>权限检查 - Permission checking</li>
 * <li>进程识别 - Process identification</li>
 * <li>SELinux上下文验证 - SELinux context verification</li>
 * </ul>
 * 
 * <p>注意 Note：</p>
 * 这些值在类加载时获取并缓存，整个进程生命周期内保持不变
 * These values are obtained and cached when the class is loaded, remaining constant throughout the process lifecycle
 */
public class OsUtils {

    /** 当前进程UID Current process UID */
    private static final int UID = android.system.Os.getuid();
    
    /** 当前进程PID Current process PID */
    private static final int PID = android.system.Os.getpid();
    
    /** SELinux上下文 SELinux context */
    private static final String SELINUX_CONTEXT;

    static {
        String context;
        try {
            // 尝试获取SELinux上下文
            context = SELinux.getContext();
        } catch (Throwable tr) {
            // 获取失败时设为null
            context = null;
        }
        SELINUX_CONTEXT = context;
    }

    /**
     * 获取当前进程UID
     * Get current process UID
     * 
     * @return 进程UID
     */
    public static int getUid() {
        return UID;
    }

    /**
     * 获取当前进程PID
     * Get current process PID
     * 
     * @return 进程PID
     */
    public static int getPid() {
        return PID;
    }

    /**
     * 获取SELinux上下文
     * Get SELinux context
     * 
     * @return SELinux上下文字符串，如果获取失败则返回null
     */
    public static String getSELinuxContext() {
        return SELINUX_CONTEXT;
    }
}


