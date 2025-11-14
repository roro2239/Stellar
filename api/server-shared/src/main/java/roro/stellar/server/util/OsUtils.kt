package roro.stellar.server.util;

import android.os.SELinux;

/**
 * 操作系统工具类（服务端版本）
 * Operating System Utility Class (Server Version)
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>获取当前进程的UID和PID - Gets current process UID and PID</li>
 * <li>获取SELinux上下文信息 - Gets SELinux context information</li>
 * <li>提供进程标识信息 - Provides process identification information</li>
 * </ul>
 * 
 * <p>注意 Note：</p>
 * 这些值在类加载时获取并缓存
 * These values are obtained and cached when the class is loaded
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
            context = SELinux.getContext();
        } catch (Throwable tr) {
            context = null;
        }
        SELINUX_CONTEXT = context;
    }

    /**
     * 获取当前进程UID
     * Get current process UID
     */
    public static int getUid() {
        return UID;
    }

    /**
     * 获取当前进程PID
     * Get current process PID
     */
    public static int getPid() {
        return PID;
    }

    /**
     * 获取SELinux上下文
     * Get SELinux context
     */
    public static String getSELinuxContext() {
        return SELINUX_CONTEXT;
    }
}


