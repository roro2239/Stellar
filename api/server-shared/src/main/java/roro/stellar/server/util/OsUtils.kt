package roro.stellar.server.util

import android.os.SELinux
import android.system.Os

/**
 * 操作系统工具类（服务端版本）
 * Operating System Utility Class (Server Version)
 *
 *
 * 功能说明 Features：
 *
 *  * 获取当前进程的UID和PID - Gets current process UID and PID
 *  * 获取SELinux上下文信息 - Gets SELinux context information
 *  * 提供进程标识信息 - Provides process identification information
 *
 *
 *
 * 注意 Note：
 * 这些值在类加载时获取并缓存
 * These values are obtained and cached when the class is loaded
 */
object OsUtils {
    /**
     * 获取当前进程UID
     * Get current process UID
     */
    /** 当前进程UID Current process UID  */
    val uid: Int = Os.getuid()

    /**
     * 获取当前进程PID
     * Get current process PID
     */
    /** 当前进程PID Current process PID  */
    val pid: Int = Os.getpid()

    /**
     * 获取SELinux上下文
     * Get SELinux context
     */
    /** SELinux上下文 SELinux context  */
    val sELinuxContext: String?

    init {
        var context: String? = try {
            SELinux.getContext()
        } catch (_: Throwable) {
            null
        }
        sELinuxContext = context
    }
}