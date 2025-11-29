package com.stellar.demo

import android.content.Context
import android.content.pm.PackageManager
import roro.stellar.Stellar
import roro.stellar.StellarSystemProperties
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * Stellar API 功能演示集合
 * 
 * 提供各种 Stellar API 的使用示例
 */
object DemoFunctions {

    /** 日志接口 */
    fun interface Logger {
        fun log(message: String)
    }

    // ========== 基础功能 ==========

    /**
     * 获取 Stellar 服务基本信息
     */
    @Suppress("UNUSED_PARAMETER")
    fun getBasicInfo(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("━━━ 服务基本信息 ━━━")
            
            val version = Stellar.version
            logger.log("服务版本: $version")
            
            val latestVersion = Stellar.latestServiceVersion
            logger.log("客户端支持版本: $latestVersion")
            
            val uid = Stellar.uid
            logger.log("服务 UID: $uid")
            
            val seContext = Stellar.sELinuxContext
            logger.log("SELinux 上下文: $seContext")
            
            val mode = when (uid) {
                0 -> "Root"
                2000 -> "ADB"
                else -> "其他 (UID=$uid)"
            }
            logger.log("运行模式: $mode")
            
            logger.log("━━━━━━━━━━━━━━━━")
            logger.log("✓ 获取成功")
        } catch (e: Exception) {
            logger.log("✗ 错误: ${e.message}")
        }
    }

    /**
     * 获取版本信息（包括 Manager 应用版本）
     */
    @Suppress("UNUSED_PARAMETER")
    fun getVersionInfo(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("━━━ 版本信息 ━━━")
            
            // 服务API版本
            val apiVersion = Stellar.version
            logger.log("服务 API 版本: $apiVersion")
            
            val latestVersion = Stellar.latestServiceVersion
            logger.log("客户端支持版本: $latestVersion")
            
            logger.log("")
            
            // Manager应用版本信息（新增功能）
            val versionName = Stellar.versionName ?: "unknown"
            logger.log("Manager 版本名称: $versionName")
            
            val versionCode = Stellar.versionCode
            if (versionCode > 0) {
                logger.log("Manager 版本代码: $versionCode")
            } else {
                logger.log("Manager 版本代码: 未知")
            }
            
            logger.log("")
            
            // 服务端补丁版本
            val patchVersion = Stellar.serverPatchVersion
            logger.log("服务补丁版本: $patchVersion")
            
            logger.log("")
            logger.log("━━━━━━━━━━━━━━━━")
            
            // 版本兼容性检查
            if (apiVersion < latestVersion) {
                logger.log("⚠ 服务版本较旧，建议更新 Manager")
            } else if (apiVersion == latestVersion) {
                logger.log("✓ 版本匹配，功能完整")
            } else {
                logger.log("ℹ 服务版本较新")
            }
            
            logger.log("✓ 获取成功")
        } catch (e: Exception) {
            logger.log("✗ 错误: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 检查 Stellar 权限
     */
    @Suppress("UNUSED_PARAMETER")
    fun checkPermission(context: Context, logger: Logger) {
        if (!Stellar.pingBinder()) {
            logger.log("✗ 服务未运行")
            return
        }

        try {
            logger.log("━━━ 权限检查 ━━━")

            val status = if (Stellar.checkSelfPermission()) {
                "已授予 ✓"
            } else {
                "未授予 ✗"
            }
            logger.log("权限状态: $status")
            
            val shouldShow = Stellar.shouldShowRequestPermissionRationale()
            logger.log("应显示权限说明: $shouldShow")
            
            logger.log("━━━━━━━━━━━━━━━━")
            logger.log("✓ 检查完成")
        } catch (e: Exception) {
            logger.log("✗ 错误: ${e.message}")
        }
    }

    // ========== 进程执行 ==========

    /**
     * 运行 ls 命令
     */
    @Suppress("UNUSED_PARAMETER")
    fun runLsCommand(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        thread {
            try {
                logger.log("━━━ 执行 ls 命令 ━━━")
                logger.log("$ ls -la /sdcard")
                logger.log("")
                
                val process = Stellar.newProcess(
                    arrayOf("ls", "-la", "/sdcard"),
                    null,
                    null
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var lineCount = 0
                reader.lineSequence().take(15).forEach { line ->
                    logger.log(line)
                    lineCount++
                }
                
                if (lineCount == 15) {
                    logger.log("... (输出过多，已截断)")
                }

                val exitCode = process.waitFor()
                logger.log("")
                logger.log("退出码: $exitCode")
                logger.log("✓ 执行完成")
                
                process.destroy()
            } catch (e: Exception) {
                logger.log("✗ 错误: ${e.message}")
            }
        }
    }

    /**
     * 运行 ps 命令
     */
    @Suppress("UNUSED_PARAMETER")
    fun runPsCommand(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        thread {
            try {
                logger.log("━━━ 执行 ps 命令 ━━━")
                logger.log("$ ps -A")
                logger.log("")
                
                val process = Stellar.newProcess(
                    arrayOf("ps", "-A"),
                    null,
                    null
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var lineCount = 0
                reader.lineSequence().take(12).forEach { line ->
                    logger.log(line)
                    lineCount++
                }
                
                if (lineCount == 12) {
                    logger.log("... (输出过多，已截断)")
                }

                val exitCode = process.waitFor()
                logger.log("")
                logger.log("退出码: $exitCode")
                logger.log("✓ 执行完成")
                
                process.destroy()
            } catch (e: Exception) {
                logger.log("✗ 错误: ${e.message}")
            }
        }
    }

    /**
     * 运行 getprop 命令
     */
    @Suppress("UNUSED_PARAMETER")
    fun runGetPropCommand(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        thread {
            try {
                logger.log("━━━ 执行 getprop 命令 ━━━")
                logger.log("$ getprop ro.product.model")
                logger.log("")
                
                val process = Stellar.newProcess(
                    arrayOf("getprop", "ro.product.model"),
                    null,
                    null
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                reader.readLine()?.let { line ->
                    logger.log("设备型号: $line")
                }

                process.waitFor()
                logger.log("")
                logger.log("✓ 执行完成")
                
                process.destroy()
            } catch (e: Exception) {
                logger.log("✗ 错误: ${e.message}")
            }
        }
    }

    // ========== 系统属性 ==========

    /**
     * 读取设备属性
     */
    @Suppress("UNUSED_PARAMETER")
    fun readDeviceProperties(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("━━━ 设备信息 ━━━")
            
            val brand = StellarSystemProperties.get("ro.product.brand")
            logger.log("品牌: $brand")
            
            val model = StellarSystemProperties.get("ro.product.model")
            logger.log("型号: $model")
            
            val manufacturer = StellarSystemProperties.get("ro.product.manufacturer")
            logger.log("制造商: $manufacturer")
            
            val androidVersion = StellarSystemProperties.get("ro.build.version.release")
            logger.log("Android 版本: $androidVersion")
            
            val sdkInt = StellarSystemProperties.getInt("ro.build.version.sdk", 0)
            logger.log("SDK 版本: $sdkInt")
            
            val buildId = StellarSystemProperties.get("ro.build.id")
            logger.log("Build ID: $buildId")
            
            logger.log("━━━━━━━━━━━━━━━━")
            logger.log("✓ 读取成功")
        } catch (e: Exception) {
            logger.log("✗ 错误: ${e.message}")
        }
    }

    /**
     * 读取调试属性
     */
    @Suppress("UNUSED_PARAMETER")
    fun readDebugProperties(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("━━━ 调试属性 ━━━")
            
            val debuggable = StellarSystemProperties.getBoolean("ro.debuggable", false)
            logger.log("ro.debuggable: $debuggable")
            
            val secure = StellarSystemProperties.getBoolean("ro.secure", true)
            logger.log("ro.secure: $secure")
            
            val buildType = StellarSystemProperties.get("ro.build.type", "unknown")
            logger.log("ro.build.type: $buildType")
            
            val buildTags = StellarSystemProperties.get("ro.build.tags", "unknown")
            logger.log("ro.build.tags: $buildTags")
            
            logger.log("━━━━━━━━━━━━━━━━")
            logger.log("✓ 读取成功")
        } catch (e: Exception) {
            logger.log("✗ 错误: ${e.message}")
        }
    }

    // ========== 高级功能 ==========

    /**
     * 检查远程权限
     */
    @Suppress("UNUSED_PARAMETER")
    fun checkRemotePermissions(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("━━━ 检查远程权限 ━━━")
            
            val permissions = arrayOf(
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.READ_LOGS",
                "android.permission.DUMP",
                "android.permission.PACKAGE_USAGE_STATS"
            )
            
            permissions.forEach { permission ->
                val result = Stellar.checkRemotePermission(permission)
                val status = if (result == PackageManager.PERMISSION_GRANTED) "✓" else "✗"
                val shortName = permission.substringAfterLast(".")
                logger.log("$status $shortName")
            }
            
            logger.log("━━━━━━━━━━━━━━━━")
            logger.log("✓ 检查完成")
        } catch (e: Exception) {
            logger.log("✗ 错误: ${e.message}")
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 检查服务是否就绪
     */
    private fun checkReady(logger: Logger): Boolean {
        if (!Stellar.pingBinder()) {
            logger.log("✗ 服务未运行")
            return false
        }

        if (!Stellar.checkSelfPermission()) {
            logger.log("✗ 权限未授予")
            return false
        }
        
        return true
    }
}

