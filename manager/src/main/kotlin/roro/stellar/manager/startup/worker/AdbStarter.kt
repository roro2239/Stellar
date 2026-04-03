package roro.stellar.manager.startup.worker

import android.util.Log
import kotlinx.coroutines.delay
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbClient
import roro.stellar.manager.adb.AdbKey
import roro.stellar.manager.adb.PreferenceAdbKeyStore
import roro.stellar.manager.startup.command.Starter

object AdbStarter {

    private const val TAG = AppConstants.TAG

    /**
     * 连接到 ADB 并执行 starter 命令。
     * 带指数退避重试，最多 [maxRetries] 次。
     */
    suspend fun startAdb(host: String, port: Int, maxRetries: Int = 5): Boolean {
        val key = AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar")

        repeat(maxRetries) { attempt ->
            try {
                AdbClient(host, port, key).use { client ->
                    client.connect()
                    client.shellCommand(Starter.internalCommand) { /* consume output */ }
                }
                return true
            } catch (_: java.io.EOFException) {
                // shell stream 关闭是正常的（服务进程已 fork）
                return true
            } catch (e: Exception) {
                Log.w(TAG, "ADB 启动尝试 ${attempt + 1}/$maxRetries 失败", e)
                if (attempt < maxRetries - 1) {
                    delay(1000L * (1 shl attempt)) // 1s, 2s, 4s, 8s
                }
            }
        }
        return false
    }

    /**
     * 轮询等待 Stellar Binder 可用。
     */
    suspend fun waitForBinder(timeoutMs: Long = 15_000): Boolean {
        val interval = 300L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (Stellar.pingBinder()) return true
            delay(interval)
            elapsed += interval
        }
        return false
    }
}
