package roro.stellar.manager.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemProperties
import java.io.File

/**
 * 环境工具类
 * Environment Utility Class
 * 
 * 功能说明 Features：
 * - 检测设备环境信息 - Detects device environment information
 * - 判断Root状态 - Determines root status
 * - 获取ADB TCP端口 - Gets ADB TCP port
 * - 检测设备类型 - Detects device type
 * 
 * 使用场景 Use Cases：
 * - 判断是否为手表设备
 * - 检测设备是否已Root
 * - 获取ADB无线调试端口
 */
object EnvironmentUtils {

    /**
     * 判断是否为手表设备
     * Check if device is a watch
     * 
     * @param context 上下文
     * @return true表示是手表设备
     */
    @JvmStatic
    fun isWatch(context: Context): Boolean {
        return (context.getSystemService(UiModeManager::class.java).currentModeType
                == Configuration.UI_MODE_TYPE_WATCH)
    }

    /**
     * 判断设备是否已Root
     * Check if device is rooted
     * 
     * 通过检查PATH中是否存在su命令来判断
     * Determines by checking if 'su' command exists in PATH
     * 
     * @return true表示已Root
     */
    fun isRooted(): Boolean {
        return System.getenv("PATH")?.split(File.pathSeparatorChar)?.find { File("$it/su").exists() } != null
    }

    /**
     * 获取ADB TCP端口
     * Get ADB TCP port
     * 
     * 依次检查service.adb.tcp.port和persist.adb.tcp.port
     * Checks service.adb.tcp.port and persist.adb.tcp.port sequentially
     * 
     * @return ADB TCP端口，-1表示未启用
     */
    fun getAdbTcpPort(): Int {
        var port = SystemProperties.getInt("service.adb.tcp.port", -1)
        if (port == -1) port = SystemProperties.getInt("persist.adb.tcp.port", -1)
        return port
    }
}

