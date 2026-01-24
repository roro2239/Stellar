package com.stellar.demo

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import roro.stellar.Stellar
import roro.stellar.StellarSystemProperties
import roro.stellar.userservice.StellarUserService
import roro.stellar.userservice.UserServiceArgs
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

object DemoFunctions {

    fun interface Logger {
        fun log(message: String)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getBasicInfo(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("--- 服务基本信息 ---")

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

            logger.log("----------------")
            logger.log("[OK] 获取成功")
        } catch (e: Exception) {
            logger.log("[Error] 错误: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getVersionInfo(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("--- 版本信息 ---")

            val apiVersion = Stellar.version
            logger.log("服务 API 版本: $apiVersion")

            val latestVersion = Stellar.latestServiceVersion
            logger.log("客户端支持版本: $latestVersion")

            logger.log("")

            val versionName = Stellar.versionName ?: "unknown"
            logger.log("Manager 版本名称: $versionName")

            val versionCode = Stellar.versionCode
            if (versionCode > 0) {
                logger.log("Manager 版本代码: $versionCode")
            } else {
                logger.log("Manager 版本代码: 未知")
            }

            logger.log("")

            val patchVersion = Stellar.serverPatchVersion
            logger.log("服务补丁版本: $patchVersion")

            logger.log("")
            logger.log("----------------")

            if (apiVersion < latestVersion) {
                logger.log("[Warning] 服务版本较旧，建议更新 Manager")
            } else if (apiVersion == latestVersion) {
                logger.log("[OK] 版本匹配，功能完整")
            } else {
                logger.log("[Info] 服务版本较新")
            }

            logger.log("[OK] 获取成功")
        } catch (e: Exception) {
            logger.log("[Error] 错误: ${e.message}")
            e.printStackTrace()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun checkPermission(context: Context, logger: Logger) {
        if (!Stellar.pingBinder()) {
            logger.log("[Error] 服务未运行")
            return
        }

        try {
            logger.log("--- 权限检查 ---")

            val status = if (Stellar.checkSelfPermission()) {
                "已授予"
            } else {
                "未授予"
            }
            logger.log("权限状态: $status")

            val shouldShow = Stellar.shouldShowRequestPermissionRationale()
            logger.log("应显示权限说明: $shouldShow")

            logger.log("----------------")
            logger.log("[OK] 检查完成")
        } catch (e: Exception) {
            logger.log("[Error] 错误: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun runLsCommand(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        thread {
            try {
                logger.log("--- 执行 ls 命令 ---")
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
                logger.log("[OK] 执行完成")

                process.destroy()
            } catch (e: Exception) {
                logger.log("[Error] 错误: ${e.message}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun runPsCommand(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        thread {
            try {
                logger.log("--- 执行 ps 命令 ---")
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
                logger.log("[OK] 执行完成")

                process.destroy()
            } catch (e: Exception) {
                logger.log("[Error] 错误: ${e.message}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun runGetPropCommand(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        thread {
            try {
                logger.log("--- 执行 getprop 命令 ---")
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
                logger.log("[OK] 执行完成")

                process.destroy()
            } catch (e: Exception) {
                logger.log("[Error] 错误: ${e.message}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun readDeviceProperties(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("--- 设备信息 ---")

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

            logger.log("----------------")
            logger.log("[OK] 读取成功")
        } catch (e: Exception) {
            logger.log("[Error] 错误: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun readDebugProperties(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("--- 调试属性 ---")

            val debuggable = StellarSystemProperties.getBoolean("ro.debuggable", false)
            logger.log("ro.debuggable: $debuggable")

            val secure = StellarSystemProperties.getBoolean("ro.secure", true)
            logger.log("ro.secure: $secure")

            val buildType = StellarSystemProperties.get("ro.build.type", "unknown")
            logger.log("ro.build.type: $buildType")

            val buildTags = StellarSystemProperties.get("ro.build.tags", "unknown")
            logger.log("ro.build.tags: $buildTags")

            logger.log("----------------")
            logger.log("[OK] 读取成功")
        } catch (e: Exception) {
            logger.log("[Error] 错误: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun checkRemotePermissions(context: Context, logger: Logger) {
        if (!checkReady(logger)) return

        try {
            logger.log("--- 检查远程权限 ---")

            val permissions = arrayOf(
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.READ_LOGS",
                "android.permission.DUMP",
                "android.permission.PACKAGE_USAGE_STATS"
            )

            permissions.forEach { permission ->
                val result = Stellar.checkRemotePermission(permission)
                val status = if (result == PackageManager.PERMISSION_GRANTED) "[OK]" else "[X]"
                val shortName = permission.substringAfterLast(".")
                logger.log("$status $shortName")
            }

            logger.log("----------------")
            logger.log("[OK] 检查完成")
        } catch (e: Exception) {
            logger.log("[Error] 错误: ${e.message}")
        }
    }

    private var userServiceBinder: IBinder? = null
    private var onServiceStateChanged: ((Boolean) -> Unit)? = null

    fun setOnServiceStateChanged(listener: ((Boolean) -> Unit)?) {
        onServiceStateChanged = listener
    }

    fun setUserServiceBinder(binder: IBinder?) {
        userServiceBinder = binder
        onServiceStateChanged?.invoke(binder != null)
    }

    private const val USE_STANDALONE_DEX = true

    fun startUserService(logger: Logger) {
        if (!checkReady(logger)) return

        if (userServiceBinder?.pingBinder() == true) {
            logger.log("[Warning] UserService 已在运行")
            return
        }

        logger.log("--- 启动 UserService ---")
        logger.log("服务类: ${DemoUserService::class.java.name}")
        logger.log("加载模式: ${if (USE_STANDALONE_DEX) "独立 dex" else "APK 直接加载"}")

        val args = UserServiceArgs.Builder(DemoUserService::class.java)
            .processNameSuffix("demo_service")
            .versionCode(1)
            .useStandaloneDex(USE_STANDALONE_DEX)
            .build()

        logger.log("参数已构建, 调用 bindUserService...")

        StellarUserService.bindUserService(args, object : StellarUserService.ServiceCallback {
            override fun onServiceConnected(service: IBinder) {
                setUserServiceBinder(service)
                logger.log("[OK] UserService 已连接")
                logger.log("Binder: $service")
            }

            override fun onServiceDisconnected() {
                setUserServiceBinder(null)
                logger.log("[Warning] UserService 已断开")
            }

            override fun onServiceStartFailed(errorCode: Int, message: String) {
                setUserServiceBinder(null)
                logger.log("[Error] 启动失败: [$errorCode] $message")
            }
        })

        logger.log("正在启动... (查看 logcat 获取详细日志)")
    }

    fun stopUserService(logger: Logger) {
        logger.log("--- 停止 UserService ---")

        val args = UserServiceArgs.Builder(DemoUserService::class.java)
            .processNameSuffix("demo_service")
            .versionCode(1)
            .useStandaloneDex(USE_STANDALONE_DEX)
            .build()

        StellarUserService.unbindUserService(args)
        setUserServiceBinder(null)
        logger.log("[OK] 已请求停止")
    }

    fun callUserService(context: Context, logger: Logger) {
        logger.log("--- 调用 UserService ---")

        val binder = userServiceBinder
        if (binder == null) {
            logger.log("[Error] UserService 未连接")
            return
        }

        try {
            val service = IDemoUserService.Stub.asInterface(binder)

            val uid = roro.stellar.userservice.UserServiceHelper.getUid(binder)
            logger.log("服务 UID: $uid")

            val pid = roro.stellar.userservice.UserServiceHelper.getPid(binder)
            logger.log("服务 PID: $pid")

            val whoami = service.executeCommand("whoami")
            logger.log("whoami: $whoami")

            val model = service.getSystemProperty("ro.product.model")
            logger.log("设备型号: $model")

            logger.log("----------------")
            logger.log("[OK] 调用成功")
        } catch (e: Exception) {
            logger.log("[Error] 调用失败: ${e.message}")
        }
    }

    private fun checkReady(logger: Logger): Boolean {
        if (!Stellar.pingBinder()) {
            logger.log("[Error] 服务未运行")
            return false
        }


        if (!Stellar.checkSelfPermission()) {
            logger.log("[Error] 权限未授予")
            return false
        }

        return true
    }
}
