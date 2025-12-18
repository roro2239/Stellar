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

class AdbWirelessHelper {

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

    suspend fun validateThenEnableWirelessAdbAsync(
        contentResolver: ContentResolver,
        context: Context,
        timeoutMs: Long = 15_000L
    ): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val intervalMs = 500L
        var elapsed = 0L

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

    private fun enableWirelessADB(contentResolver: ContentResolver, context: Context) {
        try {
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

    fun launchStarterActivity(context: Context, host: String, port: Int) {
        val intent = Intent(context, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
            putExtra(StarterActivity.EXTRA_HOST, host)
            putExtra(StarterActivity.EXTRA_PORT, port)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun changeTcpipPortIfNeeded(
        host: String,
        port: Int,
        newPort: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ): Boolean {
        if (newPort !in 1..65535) {
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

            if (flag) {
                Thread.sleep(500)
            }

            return flag
        }
    }

    private fun waitForAdbPortAvailable(
        host: String,
        port: Int,
        timeoutMs: Long = 15000L
    ): Boolean {
        val intervalMs = 500L
        var elapsed = 0L
        Log.d(AppConstants.TAG, "等待 ADB 端口 ${host}:${port} 可用...")
        while (elapsed < timeoutMs) {
            try {
                Socket(host, port).use { socket ->
                    socket.soTimeout = 2000
                    Log.i(AppConstants.TAG, "ADB 端口 ${host}:${port} 已可用")
                    return true
                }
            } catch (e: Exception) {
                Log.v(AppConstants.TAG, "端口 ${port} 尚未就绪 (已等待 ${elapsed}ms): ${e.message}")
                Thread.sleep(intervalMs)
                elapsed += intervalMs
            }
        }
        Log.w(AppConstants.TAG, "等待 ADB 端口 ${host}:${port} 超时")
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
                        PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar"
                    )
                } catch (e: Throwable) {
                    Log.e(AppConstants.TAG, "ADB密钥错误", e)
                    onError(AdbKeyException(e))
                    return@launch
                }

                val commandOutput = StringBuilder()

                val portEnabled = StellarSettings.getPreferences().getBoolean(TCPIP_PORT_ENABLED, true)
                
                var newPort: Int = -1
                val shouldChangePort = if (portEnabled) {
                    StellarSettings.getPreferences().getString(TCPIP_PORT, "").let {
                        if (it.isNullOrEmpty()) {
                            false
                        } else {
                            try {
                                newPort = it.toInt()
                                newPort != port
                            } catch (_: NumberFormatException) {
                                false
                            }
                        }
                    }
                } else {
                    false
                }
                
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
                    delay(2000)
                    if (!waitForAdbPortAvailable(host, newPort, timeoutMs = 20000L)) {
                        Log.w(
                            AppConstants.TAG,
                            "等待ADB在新端口${newPort}上监听超时"
                        )
                        onError(Exception("等待ADB在新端口${newPort}上监听超时"))
                        return@launch
                    }
                    delay(500)
                    newPort
                } else {
                    if (newPort == port && newPort > 0) {
                        Log.i(AppConstants.TAG, "目标端口${newPort}与当前端口相同，跳过切换")
                    }
                    port
                }

                if (!waitForAdbPortAvailable(host, finalPort, timeoutMs = 15000L)) {
                    Log.w(AppConstants.TAG, "等待ADB端口${finalPort}可用超时")
                    onError(Exception("等待ADB端口${finalPort}可用超时"))
                    return@launch
                }

                var lastError: Throwable? = null
                val maxRetries = 5
                for (attempt in 1..maxRetries) {
                    try {
                        AdbClient(host, finalPort, key).use { client ->
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
                        }
                        lastError = null
                        break
                    } catch (e: Throwable) {
                        lastError = e
                        Log.w(AppConstants.TAG, "ADB连接尝试 $attempt/$maxRetries 失败: ${e.message}")
                        if (attempt < maxRetries) {
                            val delayTime = 1000L * attempt
                            Log.d(AppConstants.TAG, "等待 ${delayTime}ms 后重试...")
                            delay(delayTime)
                        }
                    }
                }

                if (lastError != null) {
                    Log.e(AppConstants.TAG, "ADB连接/命令执行失败，已重试 $maxRetries 次", lastError)
                    onError(lastError)
                    return@launch
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

