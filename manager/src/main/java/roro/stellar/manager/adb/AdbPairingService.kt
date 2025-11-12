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

/**
 * ADB配对前台服务
 * ADB Pairing Foreground Service
 * 
 * 功能说明 Features：
 * - 在通知栏中提供无线ADB配对界面 - Provides wireless ADB pairing interface in notification
 * - 使用mDNS自动发现配对服务端口 - Auto discovers pairing service port using mDNS
 * - 支持在通知中直接输入配对码 - Supports entering pairing code directly in notification
 * - 自动执行配对流程 - Auto executes pairing process
 * - 显示配对状态和结果 - Shows pairing status and results
 * 
 * 工作流程 Workflow：
 * - 1. 启动为前台服务显示搜索通知
 * - 2. 使用mDNS发现配对服务端口
 * - 3. 发现端口后显示输入通知
 * - 4. 用户在通知中输入配对码
 * - 5. 生成ADB密钥并执行配对
 * - 6. 显示配对结果
 * - 7. 配对成功后自动停止服务
 * 
 * 通知状态 Notification States：
 * - searchingNotification: 正在搜索配对服务
 * - inputNotification: 已找到服务，等待输入配对码
 * - workingNotification: 正在进行配对
 * - manualInputNotification: 已停止搜索，手动输入
 * - 成功/失败通知：显示最终结果
 * 
 * 用户交互 User Interaction：
 * - 停止搜索按钮：停止mDNS发现
 * - 输入配对码按钮：在通知中输入
 * - 重试按钮：重新开始搜索
 * - RemoteInput：直接在通知中输入配对码
 * 
 * Intent Actions：
 * - startAction: 开始搜索
 * - stopAction: 停止搜索
 * - replyAction: 输入配对码
 * 
 * 使用场景 Use Cases：
 * - 从AdbPairingTutorialActivity启动
 * - 用户首次配对设备
 * - 重新配对设备
 * 
 * 注意事项 Notes：
 * - 仅限Android 11+
 * - 需要通知权限
 * - 需要WiFi连接
 * - Android 12+需要前台服务权限
 * - 配对成功后密钥永久保存
 */
@TargetApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {

        /** 通知渠道ID Notification channel ID */
        const val notificationChannel = "adb_pairing"

        /** 日志标签 Log tag */
        private const val tag = "AdbPairingService"

        /** 通知ID Notification ID */
        private const val notificationId = 1
        /** 回复请求ID Reply request ID */
        private const val replyRequestId = 1
        /** 停止请求ID Stop request ID */
        private const val stopRequestId = 2
        /** 重试请求ID Retry request ID */
        private const val retryRequestId = 3
        /** 启动动作 Start action */
        private const val startAction = "start"
        /** 停止动作 Stop action */
        private const val stopAction = "stop"
        /** 回复动作 Reply action */
        private const val replyAction = "reply"
        /** RemoteInput结果键 RemoteInput result key */
        private const val remoteInputResultKey = "paring_code"
        /** 端口键 Port key */
        private const val portKey = "paring_code"

        /**
         * 创建启动Intent
         * Create start intent
         */
        fun startIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(startAction)
        }

        /**
         * 创建停止Intent
         * Create stop intent
         */
        private fun stopIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(stopAction)
        }

        /**
         * 创建回复Intent
         * Create reply intent
         */
        private fun replyIntent(context: Context, port: Int): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(replyAction).putExtra(portKey, port)
        }
    }

    /** mDNS服务发现实例 mDNS service discovery instance */
    private var adbMdns: AdbMdns? = null
    /** 重试Handler Retry handler */
    private val retryHandler = Handler(Looper.getMainLooper())
    /** 已发现的配对端口 Discovered pairing port */
    private var discoveredPort: Int = -1

    /**
     * 端口发现观察者
     * Port discovery observer
     * 
     * 当mDNS发现配对服务端口时触发
     */
    private val observer = Observer<Int> { port ->
        Log.i(tag, "配对服务端口: $port")
        if (port <= 0) return@Observer

        discoveredPort = port
        val notification = createInputNotification(port)

        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    /** 是否已启动搜索 Whether search has started */
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
                    .setSmallIcon(R.drawable.ic_system_icon)
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
                    .setSmallIcon(R.drawable.ic_system_icon)
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
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle("正在搜索配对服务")
            .addAction(stopNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification {
        return Notification.Builder(this, notificationChannel)
            .setContentTitle("已找到配对服务")
            .setSmallIcon(R.drawable.ic_system_icon)
            .addAction(replyNotificationAction(port))
            .build()
    }

    private val workingNotification by lazy {
        Notification.Builder(this, notificationChannel)
            .setContentTitle("正在进行配对")
            .setSmallIcon(R.drawable.ic_system_icon)
            .build()
    }

    private fun createManualInputNotification(port: Int): Notification {
        return Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle("已停止搜索")
            .setContentText(if (port > 0) "请输入配对码" else "未找到配对服务，请重试")
            .addAction(if (port > 0) replyNotificationAction(port) else retryNotificationAction)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

