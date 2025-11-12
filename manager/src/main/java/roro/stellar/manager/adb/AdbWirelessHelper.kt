package roro.stellar.manager.adb

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import roro.stellar.manager.AppConstants
import roro.stellar.manager.StellarSettings

import roro.stellar.manager.StellarSettings.TCPIP_PORT
import roro.stellar.manager.StellarSettings.TCPIP_PORT_ENABLED
import roro.stellar.manager.ui.features.starter.Starter
import roro.stellar.manager.ui.features.starter.StarterActivity
import java.net.Socket

/**
 * 无线ADB辅助类
 * Wireless ADB Helper Class
 * 
 * 功能说明 Features：
 * - 启用和管理无线ADB连接 - Enables and manages wireless ADB connection
 * - 验证WiFi连接状态 - Validates WiFi connection status
 * - 自动连接到本地ADB服务 - Auto connects to local ADB service
 * - 支持同步和异步操作 - Supports sync and async operations
 * 
 * 工作流程 Workflow：
 * - 1. 检查WiFi连接状态
 * - 2. 启用无线ADB（修改系统设置）
 * - 3. 连接到本地ADB服务器
 * - 4. 启动Stellar服务
 * 
 * 注意事项 Notes：
 * - 需要WRITE_SECURE_SETTINGS权限
 * - 仅在WiFi连接时有效
 * - Android 11+支持原生无线ADB
 */
class AdbWirelessHelper {

    /**
     * 验证WiFi连接并启用无线ADB（同步）
     * Validate WiFi and enable wireless ADB (sync)
     * 
     * @param contentResolver 内容解析器
     * @param context 上下文
     * @return 是否成功启用
     */
    fun validateThenEnableWirelessAdb(
        contentResolver: ContentResolver,
        context: Context
    ): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            enableWirelessADB(contentResolver, context)
            return true
        } else {
            Log.w(AppConstants.TAG, "无线ADB自动启动条件不满足：未连接Wi-Fi")
        }
        return false
    }

    /**
     * 验证WiFi连接并启用无线ADB（异步，带超时）
     * Validate WiFi and enable wireless ADB (async with timeout)
     * 
     * 会等待WiFi连接最多timeoutMs毫秒
     * Will wait for WiFi connection up to timeoutMs milliseconds
     * 
     * @param contentResolver 内容解析器
     * @param context 上下文
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否成功启用
     */
    suspend fun validateThenEnableWirelessAdbAsync(
        contentResolver: ContentResolver,
        context: Context,
        timeoutMs: Long = 15_000L
    ): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val intervalMs = 500L
        var elapsed = 0L

        // 轮询检查WiFi连接状态
        while (elapsed < timeoutMs) {
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (networkCapabilities != null && networkCapabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                )
            ) {
                enableWirelessADB(contentResolver, context)
                return true
            }
            delay(intervalMs)
            elapsed += intervalMs
        }
        
        Log.w(AppConstants.TAG, "等待WiFi连接超时，无法启用无线ADB")
        return false
    }

    /**
     * 启用无线ADB（内部方法）
     * Enable wireless ADB (internal method)
     * 
     * @param contentResolver 内容解析器
     * @param context 上下文
     */
    private fun enableWirelessADB(contentResolver: ContentResolver, context: Context) {
        try {
            // 检查是否已启用
            val isAlreadyEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1 &&
                                  Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            
            if (isAlreadyEnabled) {
                Log.i(AppConstants.TAG, "无线调试已经启用，跳过重复操作")
                return
            }
            
            Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(contentResolver, "adb_allowed_connection_time", 0L)

            Log.i(AppConstants.TAG, "通过安全设置启用无线调试")
            Toast.makeText(context, "无线调试已启用", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            Log.e(AppConstants.TAG, "启用无线调试时权限被拒绝", se)
            throw se
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "启用无线调试时出错", e)
            throw e
        }
    }

    /**
     * 启动Starter Activity
     * Launch Starter Activity
     * 
     * @param context 上下文
     * @param host ADB主机地址
     * @param port ADB端口
     */
    fun launchStarterActivity(context: Context, host: String, port: Int) {
        val intent = Intent(context, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
            putExtra(StarterActivity.EXTRA_HOST, host)
            putExtra(StarterActivity.EXTRA_PORT, port)
        }
        context.startActivity(intent)
    }




    /**
     * 如需要则更改TCP/IP端口
     * Change TCP/IP port if needed
     * 
     * @param host ADB主机地址
     * @param port 当前ADB端口
     * @param newPort 新的TCP/IP端口
     * @param key ADB密钥
     * @param commandOutput 命令输出缓冲区
     * @param onOutput 输出回调
     * @return true表示更改成功
     */
    private fun changeTcpipPortIfNeeded(
        host: String,
        port: Int,
        newPort: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ): Boolean {
        if (newPort < 1 || newPort > 65535) {
            Log.w(AppConstants.TAG, "无效的TCP/IP端口: $newPort")
            return false
        }

        AdbClient(host, port, key).use { client ->
            client.connect()

            var flag = false
            client.tcpip(newPort) {
                commandOutput.append(String(it).apply {
                    if (contains(Regex("restarting in TCP mode port: [0-9]*"))) flag = true
                }).append("\n")
                onOutput(commandOutput.toString())
            }

            return flag
        }
    }

    /**
     * 等待ADB端口可用
     * Wait for ADB port available
     * 
     * @param host ADB主机地址
     * @param port ADB端口
     * @param timeoutMs 超时时间（毫秒）
     * @return true表示端口可用
     */
    private fun waitForAdbPortAvailable(
        host: String,
        port: Int,
        timeoutMs: Long = 15000L
    ): Boolean {
        val intervalMs = 300L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            try {
                Socket(host, port).use {
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(intervalMs)
                elapsed += intervalMs
            }
        }
        return false
    }

    fun startStellarViaAdb(
        host: String,
        port: Int,
        coroutineScope: CoroutineScope,
        onOutput: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(AppConstants.TAG, "尝试通过ADB在${host}:${port}启动Stellar")

                val key = try {
                    AdbKey(
                        PreferenceAdbKeyStore(StellarSettings.getPreferences()), "Stellar"
                    )
                } catch (e: Throwable) {
                    Log.e(AppConstants.TAG, "ADB密钥错误", e)
                    onError(AdbKeyException(e))
                    return@launch
                }

                val commandOutput = StringBuilder()

                // 检查是否启用了端口切换功能
                val portEnabled = StellarSettings.getPreferences().getBoolean(TCPIP_PORT_ENABLED, true)
                
                // 检查用户是否设置了要切换的ADB端口
                var newPort: Int = -1
                val shouldChangePort = if (portEnabled) {
                    StellarSettings.getPreferences().getString(TCPIP_PORT, "").let {
                        if (it.isNullOrEmpty()) {
                            false
                        } else {
                            try {
                                newPort = it.toInt()
                                // 只有当目标端口与当前端口不同时才需要切换
                                newPort != port
                            } catch (_: NumberFormatException) {
                                false
                            }
                        }
                    }
                } else {
                    // 功能关闭，不切换端口
                    false
                }
                
                // 只有当用户设置了端口且与当前端口不同时才进行切换
                val finalPort = if (shouldChangePort && changeTcpipPortIfNeeded(
                        host,
                        port,
                        newPort,
                        key,
                        commandOutput,
                        onOutput
                    )
                ) {
                    Log.i(AppConstants.TAG, "ADB端口从${port}切换到${newPort}，等待新端口可用...")
                    if (!waitForAdbPortAvailable(host, newPort)) {
                        Log.w(
                            AppConstants.TAG,
                            "等待ADB在新端口${newPort}上监听超时"
                        )
                        onError(Exception("等待ADB在新端口${newPort}上监听超时"))
                        return@launch
                    }
                    newPort
                } else {
                    if (newPort == port && newPort > 0) {
                        Log.i(AppConstants.TAG, "目标端口${newPort}与当前端口相同，跳过切换")
                    }
                    port
                }

                AdbClient(host, finalPort, key).use { client ->
                    try {
                        client.connect()
                        Log.i(
                            AppConstants.TAG,
                            "ADB已连接到${host}:${finalPort}。正在执行启动命令..."
                        )

                        client.shellCommand(Starter.internalCommand) { output ->
                            val outputString = String(output)
                            commandOutput.append(outputString)
                            onOutput(outputString)
                            Log.d(AppConstants.TAG, "Stellar启动输出片段: $outputString")
                        }
                    } catch (e: Throwable) {
                        Log.e(AppConstants.TAG, "ADB连接/命令执行时出错", e)
                        onError(e)
                        return@launch
                    }
                }

                Log.i(AppConstants.TAG, "通过ADB启动Stellar成功完成")
                onSuccess()
            } catch (e: Throwable) {
                Log.e(AppConstants.TAG, "startStellarViaAdb中出错", e)
                onError(e)
            }
        }
    }
}

