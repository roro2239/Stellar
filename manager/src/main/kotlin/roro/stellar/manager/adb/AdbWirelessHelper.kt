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
import roro.stellar.manager.startup.command.Starter
import java.net.Socket
import javax.net.ssl.SSLException

class AdbWirelessHelper {

    /**
     * 快速检测是否有 ADB 连接权限（已配对）
     * 通过尝试连接 ADB 来判断
     * @return true 表示有权限可以直接启动，false 表示需要配对
     */
    suspend fun hasAdbPermission(host: String, port: Int): Boolean {
        if (port !in 1..65535) return false

        val key = try {
            AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar")
        } catch (e: Throwable) {
            return false
        }

        return try {
            AdbClient(host, port, key).use { client ->
                client.connect()
            }
            true
        } catch (e: SSLException) {
            // SSL 错误表示证书不被信任，需要配对
            false
        } catch (e: java.net.ConnectException) {
            // 连接失败，端口可能未开启
            false
        } catch (e: Throwable) {
            // 其他错误也视为需要配对
            false
        }
    }

    suspend fun checkAdbConnection(host: String, port: Int): Throwable? {
        val key = try {
            AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar")
        } catch (e: Throwable) {
            return AdbKeyException(e)
        }

        return try {
            AdbClient(host, port, key).use { client ->
                client.connect()
            }
            null
        } catch (e: SSLException) {
            e
        } catch (e: java.net.ConnectException) {
            e
        } catch (e: Throwable) {
            null
        }
    }

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

        val maxRetries = 3
        for (attempt in 1..maxRetries) {
            try {
                var success = false
                AdbClient(host, port, key).use { client ->
                    client.connect()

                    client.tcpip(newPort) {
                        val output = String(it)
                        commandOutput.append(output).append("\n")
                        onOutput(commandOutput.toString())
                        if (output.contains(Regex("restarting in TCP mode port: [0-9]*"))) {
                            success = true
                        }
                    }
                }
                if (success) {
                    Thread.sleep(500)
                    return true
                }
            } catch (e: Exception) {
                if (commandOutput.contains("restarting in TCP mode port:")) {
                    Log.i(AppConstants.TAG, "端口切换成功（连接已断开，这是正常的）")
                    return true
                }
                Log.w(AppConstants.TAG, "切换端口尝试 $attempt/$maxRetries 失败: ${e.message}")
                if (attempt < maxRetries) {
                    Thread.sleep(1000L * attempt)
                }
            }
        }
        return false
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

                if (!waitForAdbPortAvailable(host, port, timeoutMs = 15000L)) {
                    Log.w(AppConstants.TAG, "等待ADB端口${port}可用超时")
                    onError(Exception("等待ADB端口${port}可用超时"))
                    return@launch
                }

                try {
                    AdbClient(host, port, key).use { client ->
                        client.connect()
                        Log.i(AppConstants.TAG, "ADB已连接到${host}:${port}。正在执行启动命令...")

                        client.shellCommand(Starter.internalCommand) { output ->
                            val outputString = String(output)
                            onOutput(outputString)
                            Log.d(AppConstants.TAG, "Stellar启动输出片段: $outputString")
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(AppConstants.TAG, "ADB连接/命令执行失败", e)
                    onError(e)
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

    fun changeTcpipPortAfterStart(
        host: String,
        port: Int,
        newPort: Int,
        coroutineScope: CoroutineScope,
        onOutput: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onSuccess: () -> Unit
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (newPort !in 1..65535 || newPort == port) {
                    Log.i(AppConstants.TAG, "无需切换端口")
                    onSuccess()
                    return@launch
                }

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
                val success = changeTcpipPortIfNeeded(host, port, newPort, key, commandOutput, onOutput)

                if (success) {
                    Log.i(AppConstants.TAG, "端口切换成功: $port -> $newPort")
                    onSuccess()
                } else {
                    onError(Exception("端口切换失败"))
                }
            } catch (e: Throwable) {
                Log.e(AppConstants.TAG, "changeTcpipPortAfterStart出错", e)
                onError(e)
            }
        }
    }

    fun shouldChangePort(currentPort: Int): Pair<Boolean, Int> {
        val portEnabled = StellarSettings.getPreferences().getBoolean(TCPIP_PORT_ENABLED, true)
        if (!portEnabled) return false to -1

        val portStr = StellarSettings.getPreferences().getString(TCPIP_PORT, "")
        if (portStr.isNullOrEmpty()) return false to -1

        return try {
            val newPort = portStr.toInt()
            (newPort != currentPort && newPort in 1..65535) to newPort
        } catch (_: NumberFormatException) {
            false to -1
        }
    }
}

