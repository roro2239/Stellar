package roro.stellar.manager.ui.features.starter

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.manager.AppConstants
import roro.stellar.manager.adb.AdbKeyException
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.utils.EnvironmentUtils
import roro.stellar.Stellar
import java.net.ConnectException

/**
 * Stellar自启动服务
 * Stellar Self Starter Service
 * 
 * 功能说明 Features：
 * - 在后台自动启动Stellar服务 - Auto starts Stellar service in background
 * - 支持mDNS自动发现ADB端口 - Supports mDNS auto discovery of ADB port
 * - 支持SystemProperties回退方案 - Supports SystemProperties fallback
 * - 无需UI交互自动连接 - Auto connects without UI interaction
 * 
 * 工作流程 Workflow：
 * - 1. 检查Stellar是否已运行
 * - 2. 检查无线ADB是否启用
 * - 3. Android 11+: 使用mDNS发现端口
 * - 4. Android 10-: 使用SystemProperties获取端口
 * - 5. 自动连接并启动Stellar
 * - 6. 成功后自动停止服务
 * 
 * 使用场景 Use Cases：
 * - 开机自动启动（无线ADB模式）
 * - WiFi连接后自动启动
 * - 用户手动触发的后台启动
 * 
 * 注意事项 Notes：
 * - 需要无线ADB已启用
 * - 需要WiFi连接
 * - Android 11+支持mDNS
 */
class SelfStarterService : Service(), LifecycleOwner {

    /** 生命周期注册表 Lifecycle registry */
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    /** ADB端口LiveData ADB port LiveData */
    private val portLive = MutableLiveData<Int>()
    /** mDNS服务发现 mDNS service discovery */
    private var adbMdns: AdbMdns? = null
    /** 无线ADB辅助类 Wireless ADB helper */
    private val adbWirelessHelper = AdbWirelessHelper()

    /**
     * 端口观察者
     * Port observer
     * 
     * 当mDNS发现ADB端口时触发
     */
    private val portObserver = Observer<Int> { p ->
        if (p in 1..65535) {
            Log.i(
                AppConstants.TAG, "Discovered adb port via mDNS: $p, starting Stellar directly"
            )
            // 不启动Activity，直接启动ADB连接
            startStellarViaAdb("127.0.0.1", p)
        } else {
            Log.w(AppConstants.TAG, "mDNS返回无效端口: $p")
        }
    }

    /**
     * 服务创建时调用
     * Called when service is created
     */
    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Log.i(AppConstants.TAG, "自启动服务已创建")
    }

    /**
     * 服务启动时调用
     * Called when service is started
     * 
     * 执行流程：
     * 1. 检查Stellar是否已运行
     * 2. 检查无线ADB是否启用
     * 3. Android 11+: 使用mDNS发现端口
     * 4. Android 10-: 使用SystemProperties获取端口
     * 5. 自动连接并启动Stellar
     * 
     * @return START_NOT_STICKY 不需要重启
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        Log.i(AppConstants.TAG, "自启动服务正在启动")

        if (Stellar.pingBinder()) {
            Log.i(AppConstants.TAG, "Stellar已在运行，停止服务")
            stopSelf()
            return START_NOT_STICKY
        }

        val wirelessEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        Log.d(AppConstants.TAG, "无线调试启用设置: $wirelessEnabled")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wirelessEnabled) {
            Log.i(AppConstants.TAG, "开始mDNS发现无线ADB端口")

            portLive.removeObserver(portObserver)
            portLive.observeForever(portObserver)

            if (adbMdns == null) {
                adbMdns = AdbMdns(context = this, serviceType = AdbMdns.TLS_CONNECT, observer = portObserver)
            }
            adbMdns?.start()
        } else {
            Log.i(
                AppConstants.TAG,
                "Using fallback: SystemProperties for ADB port (or wireless debugging setting off)."
            )
            val port = EnvironmentUtils.getAdbTcpPort()
            if (port > 0) {
                Log.i(
                    AppConstants.TAG,
                    "Found adb port via SystemProperties: $port, starting Stellar directly."
                )
                startStellarViaAdb("127.0.0.1", port)
            } else {
                Log.e(
                    AppConstants.TAG,
                    "Could not determine ADB TCP port via SystemProperties, aborting."
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 通过ADB启动Stellar
     * Start Stellar via ADB
     * 
     * @param host ADB主机地址（通常为127.0.0.1）
     * @param port ADB端口号
     */
    private fun startStellarViaAdb(host: String, port: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@SelfStarterService, "正在启动Stellar服务…", Toast.LENGTH_SHORT)
                .show()
        }

        adbWirelessHelper.startStellarViaAdb(
            host = host,
            port = port,
            coroutineScope = lifecycleScope,
            onOutput = { /* No UI to update in service */ },
            onError = { e ->
                lifecycleScope.launch(Dispatchers.Main) {
                    when (e) {
                        is AdbKeyException -> Toast.makeText(
                            applicationContext,
                            "ADB密钥错误",
                            Toast.LENGTH_LONG
                        ).show()

                        is ConnectException -> Toast.makeText(
                            applicationContext,
                            "ADB连接失败 $host:$port",
                            Toast.LENGTH_LONG
                        ).show()

                        else -> Toast.makeText(
                            applicationContext, "错误: ${e.message}", Toast.LENGTH_LONG
                        ).show()
                    }
                    stopSelf()
                }
            },
            onSuccess = { lifecycleScope.launch(Dispatchers.Main) { stopSelf() } })
    }

    /**
     * 服务销毁时调用
     * Called when service is destroyed
     * 
     * 清理资源：
     * - 停止mDNS发现
     * - 移除观察者
     */
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.i(AppConstants.TAG, "自启动服务正在销毁")
            adbMdns?.stop()
        }

        portLive.removeObserver(portObserver)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    /**
     * 绑定服务
     * Bind service
     * 
     * @return null（不支持绑定）
     */
    override fun onBind(intent: Intent?): IBinder? = null
}

