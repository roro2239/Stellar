package roro.stellar.manager.ui.features.starter

import roro.stellar.manager.application
import java.io.File

/**
 * Stellar启动器
 * Stellar Starter
 * 
 * 功能说明 Features：
 * - 提供各种启动命令 - Provides various startup commands
 * - 支持Root启动、ADB启动 - Supports Root startup, ADB startup
 * - 自动定位native库路径 - Auto locates native library path
 * 
 * 启动方式 Startup Methods：
 * - userCommand: Root模式下直接执行
 * - adbCommand: ADB shell命令
 * - internalCommand: 内部启动命令（带APK路径）
 */
object Starter {

    /** Stellar native库文件 Stellar native library file */
    private val starterFile = File(application.applicationInfo.nativeLibraryDir, "libstellar.so")

    /** 
     * 用户命令（Root模式）
     * User command (Root mode)
     * 
     * 直接执行native库的绝对路径
     */
    val userCommand: String = starterFile.absolutePath

    /** 
     * ADB命令
     * ADB command
     * 
     * 用于在PC端通过ADB执行
     * For execution via ADB from PC
     */
    val adbCommand = "adb shell $userCommand"

    /** 
     * 内部命令
     * Internal command
     * 
     * 包含APK路径参数，用于内部启动
     * Contains APK path parameter for internal startup
     */
    val internalCommand = "$userCommand --apk=${application.applicationInfo.sourceDir}"
}

