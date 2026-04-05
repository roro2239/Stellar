package roro.stellar.manager.startup.worker

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.startup.notification.BootStartNotifications
import roro.stellar.manager.util.EnvironmentUtils
import java.util.concurrent.TimeoutException

class AdbStartWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = AppConstants.TAG
        private const val UNIQUE_WORK_NAME = "stellar_adb_boot_start"

        fun enqueue(context: Context) {
            val constraintsBuilder = Constraints.Builder()
            if (EnvironmentUtils.getAdbTcpPort() <= 0) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            }
            val constraints = constraintsBuilder.build()

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
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

            val cr = applicationContext.contentResolver
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)

            val port = EnvironmentUtils.getAdbTcpPort().takeIf { it > 0 } ?: waitForAdbPort(cr)

            setForeground(createForegroundInfo(
                applicationContext.getString(R.string.boot_start_connecting)
            ))

            val started = AdbStarter.startAdb("127.0.0.1", port)
            if (!started) {
                Log.w(TAG, "ADB 连接失败")
                return retryWithNotification(
                    applicationContext.getString(R.string.boot_start_connect_failed)
                )
            }

            if (AdbStarter.waitForBinder()) {
                Log.i(TAG, "Stellar 服务已通过 ADB 在开机时成功启动")
                StellarSettings.setLastLaunchMethod(StellarSettings.LaunchMethod.ADB)
                Settings.Global.putInt(cr, "adb_wifi_enabled", 0)
                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            }

            Log.w(TAG, "等待 Binder 超时")
            return retryWithNotification(
                applicationContext.getString(R.string.boot_start_binder_timeout)
            )
        } catch (e: CancellationException) {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                    BootStartNotifications.showNotification(
                        applicationContext,
                        applicationContext.getString(R.string.boot_start_connect_failed)
                    )
                }
                stopReason == WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> {
                    BootStartNotifications.showNotification(
                        applicationContext,
                        applicationContext.getString(R.string.boot_start_waiting_wifi)
                    )
                }
                stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP -> {
                    BootStartNotifications.dismiss(applicationContext)
                }
                else -> {
                    BootStartNotifications.showNotification(
                        applicationContext,
                        applicationContext.getString(R.string.boot_start_connect_failed)
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AdbStartWorker 异常", e)
            if (Stellar.pingBinder()) {
                BootStartNotifications.dismiss(applicationContext)
                return Result.success()
            }
            return when (e) {
                is TimeoutException -> retryWithNotification(
                    applicationContext.getString(R.string.boot_start_port_not_found)
                )
                is SecurityException -> retryWithNotification(
                    applicationContext.getString(R.string.boot_start_connect_failed)
                )
                else -> retryWithNotification(
                    applicationContext.getString(R.string.boot_start_connect_failed)
                )
            }
        }
    }

    private suspend fun waitForAdbPort(cr: ContentResolver): Int = callbackFlow {
        setForegroundAsync(createForegroundInfo(
            applicationContext.getString(R.string.boot_start_discovering_port)
        ))

        val observer = Observer<Int> { port ->
            if (port > 0) {
                trySend(port)
            }
        }
        val adbMdns = AdbMdns(
            context = applicationContext,
            serviceType = AdbMdns.TLS_CONNECT,
            observer = observer
        )

        var awaitingAuth = false
        var timeoutJob: Job? = null
        var unlockReceiver: BroadcastReceiver? = null

        fun startDiscoveryWithTimeout() {
            adbMdns.start()
            timeoutJob?.cancel()
            timeoutJob = launch {
                delay(30_000L)
                close(TimeoutException("mDNS 发现超时"))
            }
        }

        fun handleAuth() {
            val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                setForegroundAsync(createForegroundInfo(
                    applicationContext.getString(R.string.boot_start_waiting_unlock)
                ))
                val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                unlockReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == Intent.ACTION_USER_PRESENT) {
                            context.unregisterReceiver(this)
                            unlockReceiver = null
                            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                        }
                    }
                }
                applicationContext.registerReceiver(unlockReceiver, filter)
            } else {
                awaitingAuth = true
            }
            timeoutJob?.cancel()
            adbMdns.stop()
        }

        val adbWifiObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                    0 -> {
                        if (awaitingAuth) {
                            close(SecurityException("无线调试网络未授权"))
                        } else {
                            handleAuth()
                        }
                    }
                    1 -> startDiscoveryWithTimeout()
                }
            }
        }

        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        cr.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, adbWifiObserver)
        startDiscoveryWithTimeout()

        awaitClose {
            adbMdns.stop()
            timeoutJob?.cancel()
            cr.unregisterContentObserver(adbWifiObserver)
            unlockReceiver?.let { applicationContext.unregisterReceiver(it) }
        }
    }.first()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(
            applicationContext.getString(R.string.boot_start_enabling_wireless_adb)
        )
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
