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

class SelfStarterService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val portLive = MutableLiveData<Int>()
    private var adbMdns: AdbMdns? = null
    private val adbWirelessHelper = AdbWirelessHelper()

    private val portObserver = Observer<Int> { p ->
        if (p in 1..65535) {
            Log.i(
                AppConstants.TAG, "Discovered adb port via mDNS: $p, starting Stellar directly"
            )
            startStellarViaAdb("127.0.0.1", p)
        } else {
            Log.w(AppConstants.TAG, "mDNS返回无效端口: $p")
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        Log.i(AppConstants.TAG, "自启动服务已创建")
    }

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

    private fun startStellarViaAdb(host: String, port: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@SelfStarterService, "正在启动Stellar服务…", Toast.LENGTH_SHORT)
                .show()
        }

        adbWirelessHelper.startStellarViaAdb(
            host = host,
            port = port,
            coroutineScope = lifecycleScope,
            onOutput = { },
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

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.i(AppConstants.TAG, "自启动服务正在销毁")
            adbMdns?.stop()
        }

        portLive.removeObserver(portObserver)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

