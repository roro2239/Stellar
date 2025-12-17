package roro.stellar.manager.adb

import android.annotation.TargetApi
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
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import kotlin.getValue

@TargetApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {

        const val notificationChannel = "adb_pairing"

        private const val tag = "AdbPairingService"

        private const val notificationId = 1
        private const val replyRequestId = 1
        private const val stopRequestId = 2
        private const val retryRequestId = 3
        private const val startAction = "start"
        private const val stopAction = "stop"
        private const val replyAction = "reply"
        private const val remoteInputResultKey = "paring_code"
        private const val portKey = "paring_code"

        fun startIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(startAction)
        }

        private fun stopIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(stopAction)
        }

        private fun replyIntent(context: Context, port: Int): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(replyAction).putExtra(portKey, port)
        }
    }

    private var adbMdns: AdbMdns? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private var discoveredPort: Int = -1

    private val observer = Observer<Int> { port ->
        Log.i(tag, "配对服务端口: $port")
        if (port <= 0) return@Observer

        discoveredPort = port
        val notification = createInputNotification(port)

        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private var started = false

    override fun onCreate() {
        super.onCreate()

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                notificationChannel,
                "无线调试配对",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                setAllowBubbles(false)
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
            else -> {
                return START_NOT_STICKY
            }
        }
        if (notification != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+ requires explicit foreground service type
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
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (started) return
        started = true
        adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING, observer).apply { start() }
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
        retryHandler.removeCallbacksAndMessages(null)
        stopSearch()
    }

    private fun onStart(): Notification {
        startSearch()
        return searchingNotification
    }

    private fun onStopSearch(): Notification {
        stopSearch()
        return createManualInputNotification(discoveredPort)
    }

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

    private fun handleResult(success: Boolean, exception: Throwable?) {
        if (success) {
            Log.i(tag, "配对成功")
            stopForeground(STOP_FOREGROUND_REMOVE)

            val title = "配对成功"
            val text = "您现在可以启动 Stellar 服务了。"

            getSystemService(NotificationManager::class.java).notify(
                notificationId,
                Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.drawable.stellar_icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .build()
            )
            
            stopSearch()
            stopSelf()
        } else {
            val title = "配对失败，正在重试..."
            val text = "请稍候，将自动返回输入界面"
            
            Log.i(tag, "配对失败，正在重试")
            
            getSystemService(NotificationManager::class.java).notify(
                notificationId,
                Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.drawable.stellar_icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .build()
            )
            
            retryHandler.postDelayed({
                getSystemService(NotificationManager::class.java).notify(notificationId, createManualInputNotification(discoveredPort))
            }, 300)
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
            "停止搜索",
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
            "重试",
            pendingIntent
        )
            .build()
    }

    private val replyNotificationAction by lazy {
        val remoteInput = RemoteInput.Builder(remoteInputResultKey).run {
            setLabel("配对码")
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
            "输入配对码",
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun replyNotificationAction(port: Int): Notification.Action {
        // Ensure pending intent is created
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
            .setSmallIcon(R.drawable.stellar_icon)
            .setContentTitle("正在搜索配对服务")
            .addAction(stopNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification {
        return Notification.Builder(this, notificationChannel)
            .setContentTitle("已找到配对服务")
            .setSmallIcon(R.drawable.stellar_icon)
            .addAction(replyNotificationAction(port))
            .build()
    }

    private val workingNotification by lazy {
        Notification.Builder(this, notificationChannel)
            .setContentTitle("正在进行配对")
            .setSmallIcon(R.drawable.stellar_icon)
            .build()
    }

    private fun createManualInputNotification(port: Int): Notification {
        return Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.drawable.stellar_icon)
            .setContentTitle("已停止搜索")
            .setContentText(if (port > 0) "请输入配对码" else "未找到配对服务，请重试")
            .addAction(if (port > 0) replyNotificationAction(port) else retryNotificationAction)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

