package roro.stellar.manager.startup.service

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbKeyException
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.util.EnvironmentUtils
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
                AppConstants.TAG, "通过 mDNS 发现 ADB 端口: $p"
            )
            changeTcpipPortThenStart("127.0.0.1", p)
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

        discoverAndStart()
        return START_NOT_STICKY
    }

    private fun discoverAndStart() {
        val wirelessEnabled = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        Log.d(AppConstants.TAG, "无线调试启用设置: $wirelessEnabled")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wirelessEnabled) {
            Log.i(AppConstants.TAG, "开始mDNS发现无线ADB端口")

            portLive.removeObserver(portObserver)
            portLive.observeForever(portObserver)

            if (adbMdns == null) {
                adbMdns = AdbMdns(context = this, serviceType = AdbMdns.TLS_CONNECT, observer = portObserver, onMaxRefresh = null)
            }
            adbMdns?.start()
        } else {
            Log.i(
                AppConstants.TAG,
                "使用备用方案: 通过 SystemProperties 获取 ADB 端口（或无线调试设置已关闭）。"
            )
            val port = EnvironmentUtils.getAdbTcpPort()
            if (port > 0) {
                Log.i(
                    AppConstants.TAG,
                    "通过 SystemProperties 发现 ADB 端口: $port"
                )
                changeTcpipPortThenStart("127.0.0.1", port)
            } else {
                Log.e(
                    AppConstants.TAG,
                    "无法通过 SystemProperties 确定 ADB TCP 端口，中止。"
                )
                stopSelf()
            }
        }
    }

    private fun changeTcpipPortThenStart(host: String, currentPort: Int) {
        val (shouldChange, newPort) = adbWirelessHelper.shouldChangePort(currentPort)
        if (!shouldChange) {
            startStellarViaAdb(host, currentPort)
            return
        }

        Log.i(AppConstants.TAG, "切换 TCP/IP 端口: $currentPort -> $newPort")
        adbWirelessHelper.changeTcpipPortAfterStart(
            host = host,
            port = currentPort,
            newPort = newPort,
            coroutineScope = lifecycleScope,
            onOutput = { },
            onError = {
                Log.w(AppConstants.TAG, "切换端口失败，使用原端口 $currentPort 启动", it)
                startStellarViaAdb(host, currentPort)
            },
            onSuccess = {
                Log.i(AppConstants.TAG, "端口切换成功，使用新端口 $newPort 启动")
                startStellarViaAdb(host, newPort)
            }
        )
    }

    private fun startStellarViaAdb(host: String, port: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@SelfStarterService, getString(R.string.starting_stellar_service), Toast.LENGTH_SHORT)
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
                            getString(R.string.adb_key_error),
                            Toast.LENGTH_LONG
                        ).show()

                        is ConnectException -> Toast.makeText(
                            applicationContext,
                            getString(R.string.adb_connection_failed, host, port),
                            Toast.LENGTH_LONG
                        ).show()

                        else -> Toast.makeText(
                            applicationContext, getString(R.string.error_prefix, e.message ?: ""), Toast.LENGTH_LONG
                        ).show()
                    }
                    stopSelf()
                }
            },
            onSuccess = {
                Log.i(AppConstants.TAG, "服务启动成功")
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, getString(R.string.boot_start_success), Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            })
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
