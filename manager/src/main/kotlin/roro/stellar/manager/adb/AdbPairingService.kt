package roro.stellar.manager.adb

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import kotlin.getValue
import androidx.core.content.edit

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {

        const val notificationChannel = "adb_pairing"
        const val alertNotificationChannel = "adb_pairing_alert"

        private const val tag = "AdbPairingService"

        private const val notificationId = 1
        private const val alertNotificationId = 2
        private const val replyRequestId = 1
        private const val stopRequestId = 2
        private const val retryRequestId = 3
        private const val stopAndRetryRequestId = 4
        private const val startAction = "start"
        private const val stopAction = "stop"
        private const val stopAndRetryAction = "stop_and_retry"
        private const val replyAction = "reply"
        private const val remoteInputResultKey = "paring_code"
        private const val portKey = "paring_code"

        @Volatile
        private var isRunning = false

        fun startIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(startAction)

        private fun stopIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(stopAction)

        private fun stopAndRetryIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(stopAndRetryAction)

        private fun replyIntent(context: Context, port: Int): Intent =
            Intent(context, AdbPairingService::class.java).setAction(replyAction).putExtra(portKey, port)
    }

    private var adbMdns: AdbMdns? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private var discoveredPort: Int = -1

    private val observer = Observer<Int> { port ->
        Log.i(tag, "配对服务端口: $port")
        if (port <= 0) {
            return@Observer
        }

        discoveredPort = port

        val notification = createInputNotification(port)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
            Log.i(tag, "已更新通知为输入配对码")
        } catch (e: Exception) {
            Log.e(tag, "更新通知失败", e)
            getSystemService(NotificationManager::class.java).notify(notificationId, notification)

            val alertNotification = Notification.Builder(this, alertNotificationChannel)
                .setSmallIcon(R.drawable.ic_stellar)
                .setContentTitle(getString(R.string.pairing_service_found))
                .setContentText(getString(R.string.enter_pairing_code))
                .addAction(replyNotificationAction(port))
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(alertNotificationId, alertNotification)
        }
    }

    private var started = false

    override fun onCreate() {
        super.onCreate()

        val notificationManager = getSystemService(NotificationManager::class.java)

        notificationManager.createNotificationChannel(
            NotificationChannel(
                notificationChannel,
                getString(R.string.wireless_debugging_pairing_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                setAllowBubbles(false)
            })

        notificationManager.createNotificationChannel(
            NotificationChannel(
                alertNotificationChannel,
                getString(R.string.pairing_alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                enableVibration(true)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = when (intent?.action) {
            startAction -> {
                onStart()
            }
            replyAction -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(remoteInputResultKey) ?: ""
                val port = intent.getIntExtra(portKey, -1)
                if (port != -1) {
                    onInput(code.toString(), port)
                } else {
                    onStart()
                }
            }
            stopAction -> {
                onStopSearch()
            }
            stopAndRetryAction -> {
                onStopAndRetry()
            }
            else -> {
                return START_NOT_STICKY
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Throwable) {
            Log.e(tag, "启动前台服务失败", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException) {
                getSystemService(NotificationManager::class.java).notify(notificationId, notification)
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (started) return
        started = true
        adbMdns = AdbMdns(
            this,
            AdbMdns.TLS_PAIRING,
            observer,
            onMaxRefresh = { onSearchMaxRefresh() }
        ).apply { start() }
    }

    private fun stopSearch() {
        if (!started) return
        started = false
        try {
            adbMdns?.stop()
        } catch (e: Exception) {
            Log.e(tag, "停止搜索失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        retryHandler.removeCallbacksAndMessages(null)
        stopSearch()
        adbMdns?.destroy()
        adbMdns = null
        connectMdns?.destroy()
        connectMdns = null
    }

    private fun onStart(): Notification {
        if (isRunning && started) {
            Log.i(tag, "服务已在运行，忽略重复启动")
            return searchingNotification
        }
        isRunning = true
        startSearch()
        return searchingNotification
    }

    private fun onStopSearch(): Notification {
        stopSearch()
        return createManualInputNotification(discoveredPort)
    }

    private fun onStopAndRetry(): Notification {
        stopSearch()
        adbMdns?.destroy()
        adbMdns = null
        return onStart()
    }

    private fun onSearchMaxRefresh() {
        Log.i(tag, "搜索次数已达上限")
        stopSearch()
        val notification = createMaxRefreshNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "更新前台通知失败", e)
            getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onInput(code: String, port: Int): Notification {
        if (port == -1) {
            return createManualInputNotification(-1)
        }

        GlobalScope.launch(Dispatchers.IO) {
            val host = "127.0.0.1"

            val key = try {
                AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "Stellar")
            } catch (e: Throwable) {
                e.printStackTrace()
                return@launch
            }

            AdbPairingClient(host, port, code, key).runCatching {
                start()
            }.onFailure {
                handleResult(false, it)
            }.onSuccess {
                handleResult(it, null)
            }
        }

        return workingNotification
    }

    private var connectMdns: AdbMdns? = null

    private fun handleResult(success: Boolean, exception: Throwable?) {
        retryHandler.post {
            if (success) {
                Log.i(tag, "配对成功，开始搜索连接服务")
                stopSearch()

                val successNotification = Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.drawable.ic_stellar)
                    .setContentTitle(getString(R.string.pairing_success))
                    .setContentText(getString(R.string.searching_connect_service))
                    .setOngoing(true)
                    .build()

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(notificationId, successNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(notificationId, successNotification)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "更新前台通知失败", e)
                }

                searchConnectService()
            } else {
                val title = getString(R.string.pairing_failed_retrying)
                val text = getString(R.string.please_wait_auto_return)

                Log.i(tag, "配对失败，正在重试")

                val failureNotification = Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.drawable.ic_stellar)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .build()

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(notificationId, failureNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(notificationId, failureNotification)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "更新前台通知失败", e)
                }

                retryHandler.postDelayed({
                    val retryNotification = createManualInputNotification(discoveredPort)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(notificationId, retryNotification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else {
                            startForeground(notificationId, retryNotification)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "更新前台通知失败", e)
                    }
                }, 2000)
            }
        }
    }

    private fun searchConnectService() {
        val connectObserver = Observer<Int> { port ->
            Log.i(tag, "连接服务端口: $port")
            if (port <= 0) return@Observer

            connectMdns?.destroy()
            connectMdns = null

            onConnectServiceFound(port)
        }

        connectMdns = AdbMdns(
            this,
            AdbMdns.TLS_CONNECT,
            connectObserver,
            onMaxRefresh = {
                Log.w(tag, "搜索连接服务次数已达上限")
                onConnectServiceMaxRefresh()
            }
        ).apply { start() }
    }

    private fun onConnectServiceFound(port: Int) {
        retryHandler.post {
            Log.i(tag, "找到连接服务端口: $port")

            val preferences = StellarSettings.getPreferences()
            val tcpipPortEnabled = preferences.getBoolean(StellarSettings.TCPIP_PORT_ENABLED, true)
            val currentPort = preferences.getString(StellarSettings.TCPIP_PORT, "")

            if (tcpipPortEnabled && currentPort.isNullOrEmpty()) {
                preferences.edit {
                    putString(StellarSettings.TCPIP_PORT, port.toString())
                }
                Log.i(tag, "自动设置 TCP 端口: $port")
            }

            grantSecureSettingsPermission(port)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun grantSecureSettingsPermission(port: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val key = AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar")

                val maxWait = 5000L
                val interval = 200L
                var elapsed = 0L
                while (elapsed < maxWait) {
                    try {
                        java.net.Socket("127.0.0.1", port).close()
                        break
                    } catch (_: Exception) {
                        kotlinx.coroutines.delay(interval)
                        elapsed += interval
                    }
                }

                AdbClient("127.0.0.1", port, key).use { client ->
                    client.connect()
                    val command = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
                    client.shellCommand(command) { output ->
                        Log.d(tag, "授权命令输出: ${String(output)}")
                    }
                }
                Log.i(tag, "WRITE_SECURE_SETTINGS 权限授权成功")
            } catch (e: Exception) {
                Log.e(tag, "自动授权 WRITE_SECURE_SETTINGS 失败", e)
            }

            retryHandler.post {
                navigateToStarter(port)
            }
        }
    }

    private fun navigateToStarter(port: Int) {
        val intent = roro.stellar.manager.ui.features.manager.ManagerActivity.createStarterIntent(
            this,
            isRoot = false,
            host = "127.0.0.1",
            port = port
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false
        stopSelf()
    }

    private fun onConnectServiceMaxRefresh() {
        retryHandler.post {
            Log.w(tag, "连接服务搜索次数已达上限，尝试使用系统端口")

            val systemPort = roro.stellar.manager.util.EnvironmentUtils.getAdbTcpPort()
            if (systemPort in 1..65535) {
                grantSecureSettingsPermission(systemPort)
            } else {
                val notification = Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.drawable.ic_stellar)
                    .setContentTitle(getString(R.string.connect_service_not_found))
                    .setContentText(getString(R.string.please_open_app_manually))
                    .setAutoCancel(true)
                    .build()

                stopForeground(STOP_FOREGROUND_REMOVE)
                getSystemService(NotificationManager::class.java).notify(notificationId, notification)
                isRunning = false
                stopSelf()
            }
        }
    }

    private val stopNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this,
            stopRequestId,
            stopIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.stop_search),
            pendingIntent
        )
            .build()
    }

    private val retryNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this,
            retryRequestId,
            startIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.retry),
            pendingIntent
        )
            .build()
    }

    private val stopAndRetryNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this,
            stopAndRetryRequestId,
            stopAndRetryIntent(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.cannot_find_pairing),
            pendingIntent
        )
            .build()
    }

    private val replyNotificationAction by lazy {
        val remoteInput = RemoteInput.Builder(remoteInputResultKey).run {
            setLabel(getString(R.string.pairing_code))
            build()
        }

        val pendingIntent = PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, -1),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        Notification.Action.Builder(
            null,
            getString(R.string.enter_pairing_code_action),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun replyNotificationAction(port: Int): Notification.Action {
        val action = replyNotificationAction

        PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, port),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return action
    }

    private val searchingNotification by lazy {
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.drawable.ic_stellar)
            .setContentTitle(getString(R.string.searching_pairing_service))
            .addAction(stopNotificationAction)
            .addAction(stopAndRetryNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification =
        Notification.Builder(this, notificationChannel)
            .setContentTitle(getString(R.string.pairing_service_found))
            .setSmallIcon(R.drawable.ic_stellar)
            .addAction(replyNotificationAction(port))
            .build()

    private fun createMaxRefreshNotification(): Notification =
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.drawable.ic_stellar)
            .setContentTitle(getString(R.string.pairing_service_not_found))
            .setContentText(getString(R.string.ensure_wireless_debugging_open))
            .addAction(retryNotificationAction)
            .build()

    private val workingNotification by lazy {
        Notification.Builder(this, notificationChannel)
            .setContentTitle(getString(R.string.pairing_in_progress))
            .setSmallIcon(R.drawable.ic_stellar)
            .build()
    }

    private fun createManualInputNotification(port: Int): Notification =
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.drawable.ic_stellar)
            .setContentTitle(getString(R.string.search_stopped))
            .setContentText(if (port > 0) getString(R.string.enter_pairing_code) else getString(R.string.pairing_service_not_found_retry))
            .addAction(if (port > 0) replyNotificationAction(port) else retryNotificationAction)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}

