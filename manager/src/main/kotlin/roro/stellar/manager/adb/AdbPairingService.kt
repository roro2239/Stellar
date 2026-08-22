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
import roro.stellar.manager.compat.BuildUtils.atLeast31
import roro.stellar.manager.compat.BuildUtils.atLeast34
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
        private const val hostKey = "host"

        @Volatile
        private var isRunning = false

        fun startIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(startAction)

        private fun stopIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(stopAction)

        private fun stopAndRetryIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(stopAndRetryAction)

        private fun replyIntent(context: Context, host: String, port: Int): Intent =
            Intent(context, AdbPairingService::class.java)
                .setAction(replyAction)
                .putExtra(hostKey, host)
                .putExtra(portKey, port)
    }

    private var adbMdns: AdbMdns? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private var discoveredHost: String = "127.0.0.1"
    private var discoveredPort: Int = -1

    private val observer = Observer<Pair<String, Int>> { service ->
        val host = service.first
        val port = service.second
        Log.i(tag, "配对服务端口: $port")
        if (port <= 0) {
            return@Observer
        }

        discoveredHost = host
        discoveredPort = port

        val notification = createInputNotification(host, port)
        try {
            if (atLeast34) {
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
                .addAction(replyNotificationAction(host, port))
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
                val host = intent.getStringExtra(hostKey) ?: "127.0.0.1"
                val port = intent.getIntExtra(portKey, -1)
                if (port != -1) {
                    onInput(code.toString(), host, port)
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
            if (atLeast34) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Throwable) {
            Log.e(tag, "启动前台服务失败", e)

            if (atLeast31
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
        return createManualInputNotification(discoveredHost, discoveredPort)
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
            if (atLeast34) {
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
    private fun onInput(code: String, host: String, port: Int): Notification {
        if (port == -1) {
            return createManualInputNotification("127.0.0.1", -1)
        }

        GlobalScope.launch(Dispatchers.IO) {
            val key = try {
                AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "Stellar")
            } catch (e: Throwable) {
                e.printStackTrace()
                return@launch
            }

            AdbPairingClient(host, port, code, key).runCatching {
                start()
            }.onFailure {
                handleResult(false)
            }.onSuccess {
                handleResult(it)
            }
        }

        return workingNotification
    }

    private var connectMdns: AdbMdns? = null

    private fun handleResult(success: Boolean) {
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
                    if (atLeast34) {
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
                    if (atLeast34) {
                        startForeground(notificationId, failureNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(notificationId, failureNotification)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "更新前台通知失败", e)
                }

                retryHandler.postDelayed({
                    val retryNotification = createManualInputNotification(discoveredHost, discoveredPort)
                    try {
                        if (atLeast34) {
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
        val connectObserver = Observer<Pair<String, Int>> { service ->
            val host = service.first
            val port = service.second
            Log.i(tag, "连接服务端口: $port")
            if (port <= 0) return@Observer

            connectMdns?.destroy()
            connectMdns = null

            onConnectServiceFound(host, port)
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

    private fun onConnectServiceFound(host: String, port: Int) {
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

            grantSecureSettingsPermission(host, port)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun grantSecureSettingsPermission(host: String, port: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val key = AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar")

                val maxWait = 5000L
                val interval = 200L
                var elapsed = 0L
                while (elapsed < maxWait) {
                    try {
                        java.net.Socket(host, port).close()
                        break
                    } catch (_: Exception) {
                        kotlinx.coroutines.delay(interval)
                        elapsed += interval
                    }
                }

                AdbClient(host, port, key).use { client ->
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
                navigateToStarter(host, port)
            }
        }
    }

    private fun navigateToStarter(host: String, port: Int) {
        val intent = roro.stellar.manager.ui.features.manager.ManagerActivity.createStarterIntent(
            this,
            isRoot = false,
            host = host,
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
                grantSecureSettingsPermission("127.0.0.1", systemPort)
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
            if (atLeast31)
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
            if (atLeast31)
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
            if (atLeast31)
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
            replyIntent(this, "127.0.0.1", -1),
            if (atLeast31)
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

    private fun replyNotificationAction(host: String, port: Int): Notification.Action {
        val action = replyNotificationAction

        PendingIntent.getForegroundService(
            this,
            replyRequestId,
            replyIntent(this, host, port),
            if (atLeast31)
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

    private fun createInputNotification(host: String, port: Int): Notification =
        Notification.Builder(this, notificationChannel)
            .setContentTitle(getString(R.string.pairing_service_found))
            .setSmallIcon(R.drawable.ic_stellar)
            .addAction(replyNotificationAction(host, port))
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

    private fun createManualInputNotification(host: String, port: Int): Notification =
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.drawable.ic_stellar)
            .setContentTitle(getString(R.string.search_stopped))
            .setContentText(if (port > 0) getString(R.string.enter_pairing_code) else getString(R.string.pairing_service_not_found_retry))
            .addAction(if (port > 0) replyNotificationAction(host, port) else retryNotificationAction)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}

