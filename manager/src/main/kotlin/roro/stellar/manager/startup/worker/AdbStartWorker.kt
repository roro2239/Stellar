package roro.stellar.manager.startup.worker

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.startup.notification.BootStartNotifications
import roro.stellar.manager.util.EnvironmentUtils
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AdbStartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = AppConstants.TAG
        private const val UNIQUE_WORK_NAME = "stellar_adb_boot_start"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            BootStartNotifications.showNotification(
                context,
                context.getString(R.string.boot_start_waiting_wifi)
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            BootStartNotifications.dismiss(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun doWork(): Result {
        try {
            setForeground(createForegroundInfo(
                applicationContext.getString(R.string.boot_start_enabling_wireless_adb)
            ))

            if (Stellar.pingBinder()) {
                Log.i(TAG, "Stellar 已在运行，无需重启")
                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            }

            // 启用无线 ADB
            enableWirelessAdb()

            // 等待设备解锁（无线调试在锁屏时可能无法启用）
            waitForDeviceUnlockIfNeeded()

            setForeground(createForegroundInfo(
                applicationContext.getString(R.string.boot_start_discovering_port)
            ))

            // 发现 ADB 端口
            val port = discoverAdbPort()
            if (port <= 0) {
                Log.w(TAG, "未能发现 ADB 端口")
                return retryWithNotification(
                    applicationContext.getString(R.string.boot_start_port_not_found)
                )
            }

            // 处理 TCP 端口切换
            val finalPort = handlePortSwitch(port)

            setForeground(createForegroundInfo(
                applicationContext.getString(R.string.boot_start_connecting)
            ))

            // 连接 ADB 并执行启动命令
            val started = AdbStarter.startAdb("127.0.0.1", finalPort)
            if (!started) {
                Log.w(TAG, "ADB 连接失败")
                return retryWithNotification(
                    applicationContext.getString(R.string.boot_start_connect_failed)
                )
            }

            // 等待 Binder
            if (AdbStarter.waitForBinder()) {
                Log.i(TAG, "Stellar 服务已通过 ADB 在开机时成功启动")
                StellarSettings.setLastLaunchMethod(StellarSettings.LaunchMethod.ADB)
                disableWirelessAdb()
                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            }

            Log.w(TAG, "等待 Binder 超时")
            return retryWithNotification(
                applicationContext.getString(R.string.boot_start_binder_timeout)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AdbStartWorker 异常", e)
            // 检查是否在此期间已成功启动
            if (Stellar.pingBinder()) {
                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            }
            return retryWithNotification(
                applicationContext.getString(R.string.boot_start_failed, e.message ?: "")
            )
        }
    }

    private fun enableWirelessAdb() {
        val cr = applicationContext.contentResolver
        try {
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            Log.i(TAG, "已启用无线调试")
        } catch (e: SecurityException) {
            Log.e(TAG, "启用无线调试权限不足", e)
            throw e
        }
    }

    private fun disableWirelessAdb() {
        if (applicationContext.checkSelfPermission(WRITE_SECURE_SETTINGS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                Settings.Global.putInt(applicationContext.contentResolver, "adb_wifi_enabled", 0)
                Log.i(TAG, "已关闭无线调试")
            } catch (e: Exception) {
                Log.w(TAG, "关闭无线调试失败", e)
            }
        }
    }

    private suspend fun waitForDeviceUnlockIfNeeded() {
        val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) return

        Log.i(TAG, "设备已锁屏，等待解锁...")
        setForeground(createForegroundInfo(
            applicationContext.getString(R.string.boot_start_waiting_unlock)
        ))

        try {
            withTimeout(120_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (intent.action == Intent.ACTION_USER_PRESENT) {
                                context.unregisterReceiver(this)
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }
                    }
                    applicationContext.registerReceiver(
                        receiver,
                        IntentFilter(Intent.ACTION_USER_PRESENT)
                    )
                    cont.invokeOnCancellation {
                        try {
                            applicationContext.unregisterReceiver(receiver)
                        } catch (_: Exception) {}
                    }

                    // 再次检查，可能在注册期间已解锁
                    if (!km.isKeyguardLocked) {
                        try {
                            applicationContext.unregisterReceiver(receiver)
                        } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "等待设备解锁超时，继续尝试")
        }

        // 解锁后重新启用无线 ADB
        enableWirelessAdb()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun discoverAdbPort(): Int {
        // 先检查 SystemProperties 中的 TCP 端口
        val tcpPort = EnvironmentUtils.getAdbTcpPort()
        if (tcpPort > 0) {
            Log.i(TAG, "通过 SystemProperties 发现 ADB 端口: $tcpPort")
            return tcpPort
        }

        // 通过 mDNS 发现
        Log.i(TAG, "开始 mDNS 发现无线 ADB 端口")
        return withContext(Dispatchers.Main) {
            try {
                withTimeout(30_000L) {
                    suspendCancellableCoroutine { cont ->
                        val observer = Observer<Int> { port ->
                            if (port > 0 && cont.isActive) {
                                cont.resume(port)
                            }
                        }
                        val mdns = AdbMdns(
                            context = applicationContext,
                            serviceType = AdbMdns.TLS_CONNECT,
                            observer = observer,
                            onMaxRefresh = {
                                if (cont.isActive) cont.resume(-1)
                            }
                        )
                        mdns.start()
                        cont.invokeOnCancellation { mdns.destroy() }
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "mDNS 发现超时")
                -1
            }
        }
    }

    private fun handlePortSwitch(currentPort: Int): Int {
        val adbWirelessHelper = AdbWirelessHelper()
        val (shouldChange, newPort) = adbWirelessHelper.shouldChangePort(currentPort)
        return if (shouldChange && newPort > 0) {
            Log.i(TAG, "需要切换端口: $currentPort -> $newPort (暂不在 Worker 中切换)")
            // 端口切换需要 ADB 连接，在 Worker 中进行可能增加复杂度
            // 直接使用发现到的端口，端口切换留给服务启动后处理
            currentPort
        } else {
            currentPort
        }
    }

    private fun retryWithNotification(message: String): Result {
        BootStartNotifications.showNotification(applicationContext, message)
        return Result.retry()
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        return ForegroundInfo(
            BootStartNotifications.NOTIFICATION_ID,
            BootStartNotifications.buildStartingNotification(applicationContext, message)
        )
    }
}
