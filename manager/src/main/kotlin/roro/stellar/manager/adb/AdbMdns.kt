package roro.stellar.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val observer: Observer<Int>,
    private val onMaxRefresh: (() -> Unit)? = null,
    private val onStatusUpdate: ((String) -> Unit)? = null,
    private val maxRefreshCount: Int = MAX_REFRESH_COUNT
) {

    @Volatile
    private var registered = false
    @Volatile
    private var running = false
    @Volatile
    private var pendingRestart = false
    @Volatile
    private var startFailedRetryCount = 0
    @Volatile
    private var refreshCount = 0
    @Volatile
    private var serviceFound = false
    @Volatile
    private var resolving = false
    private var serviceName: String? = null
    private var listener: DiscoveryListener? = null
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)

    private var executor = Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            if (serviceFound) return

            if (refreshCount >= maxRefreshCount) {
                Log.v(TAG, "搜索次数已达上限，自动停止")
                stop()
                onMaxRefresh?.invoke()
                return
            }

            if (registered && !pendingRestart && !serviceFound && !resolving) {
                refreshCount++
                Log.v(TAG, "刷新服务发现 (第${refreshCount}次刷新)")
                pendingRestart = true
                try {
                    listener?.let { nsdManager.stopServiceDiscovery(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "停止服务发现失败", e)
                    pendingRestart = false
                }
            }

            mainHandler.postDelayed(this, CHECK_INTERVAL_MILLIS)
        }
    }

    fun start() {
        if (running) return
        running = true
        startFailedRetryCount = 0
        refreshCount = 0
        serviceFound = false
        resolving = false

        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }

        onStatusUpdate?.invoke("正在启动服务发现...")
        startDiscoveryInternal()
        mainHandler.postDelayed(refreshRunnable, INITIAL_DELAY_MILLIS)
    }

    private fun startDiscoveryInternal() {
        if (!running || registered) return

        try {
            listener = DiscoveryListener(this)
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            Log.v(TAG, "启动服务发现: $serviceType")
        } catch (e: Exception) {
            Log.e(TAG, "启动服务发现失败", e)
            scheduleRetryStart()
        }
    }

    private fun scheduleRetryStart() {
        if (!running) return
        if (startFailedRetryCount >= MAX_START_RETRY) {
            Log.e(TAG, "启动服务发现重试次数已达上限")
            return
        }
        startFailedRetryCount++
        Log.v(TAG, "将在 ${RETRY_DELAY_MILLIS}ms 后重试启动 (第 $startFailedRetryCount 次)")
        mainHandler.postDelayed({
            if (running && !registered) {
                startDiscoveryInternal()
            }
        }, RETRY_DELAY_MILLIS)
    }

    fun stop() {
        if (!running) return
        running = false
        mainHandler.removeCallbacks(refreshRunnable)
        if (registered) {
            try {
                listener?.let { nsdManager.stopServiceDiscovery(it) }
            } catch (e: Exception) {
                Log.e(TAG, "停止服务发现失败", e)
            }
        }
        listener = null
        registered = false
        pendingRestart = false
    }

    fun destroy() {
        stop()
        executor.shutdown()
    }

    private fun onDiscoveryStart() {
        registered = true
    }

    private fun onDiscoveryStop() {
        registered = false
        if (pendingRestart && running) {
            pendingRestart = false
            mainHandler.postDelayed({
                if (running && !registered) {
                    startDiscoveryInternal()
                }
            }, RESTART_DELAY_MILLIS)
        }
    }

    @Suppress("DEPRECATION")
    private fun onServiceFound(info: NsdServiceInfo) {
        resolving = true
        nsdManager.resolveService(info, ResolveListener(this))
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        if (info.serviceName == serviceName) observer.onChanged(-1)
    }

    @Suppress("DEPRECATION")
    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        if (!running) return
        
        executor.execute {
            try {
                val isLocalService = NetworkInterface.getNetworkInterfaces()
                    .asSequence()
                    .any { networkInterface ->
                        networkInterface.inetAddresses
                            .asSequence()
                            .any { resolvedService.host.hostAddress == it.hostAddress }
                    }

                Log.v(TAG, "isLocalService=$isLocalService, running=$running")

                if (!isLocalService || !running) {
                    return@execute
                }

                val portAvailable = isPortAvailable(resolvedService.port)
                Log.v(TAG, "portAvailable=$portAvailable, port=${resolvedService.port}")

                if (portAvailable && running) {
                    mainHandler.post {
                        if (running && !serviceFound) {
                            serviceFound = true
                            mainHandler.removeCallbacks(refreshRunnable)
                            serviceName = resolvedService.serviceName
                            Log.i(TAG, "已发现服务，停止刷新")
                            observer.onChanged(resolvedService.port)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "服务验证失败", e)
            }
        }
    }

    private fun isPortAvailable(port: Int) = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (e: IOException) {
        true
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "发现已开始: $serviceType")
            adbMdns.startFailedRetryCount = 0
            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "开始发现失败: $serviceType, errorCode=$errorCode")
            adbMdns.registered = false
            adbMdns.scheduleRetryStart()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "发现已停止: $serviceType")
            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "停止发现失败: $serviceType, errorCode=$errorCode")
            adbMdns.registered = false
            adbMdns.pendingRestart = false
            if (adbMdns.running) {
                adbMdns.mainHandler.postDelayed({
                    if (adbMdns.running && !adbMdns.registered) {
                        adbMdns.startDiscoveryInternal()
                    }
                }, RESTART_DELAY_MILLIS)
            }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "发现服务: ${serviceInfo.serviceName}")
            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "服务丢失: ${serviceInfo.serviceName}")
            adbMdns.onServiceLost(serviceInfo)
        }
    }

    internal class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {
            Log.e(TAG, "解析服务失败: ${nsdServiceInfo.serviceName}, errorCode=$i")
            adbMdns.resolving = false
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            Log.v(TAG, "解析服务成功: ${nsdServiceInfo.serviceName}, host=${nsdServiceInfo.host}, port=${nsdServiceInfo.port}")
            adbMdns.resolving = false
            adbMdns.onServiceResolved(nsdServiceInfo)
        }

    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
        const val CHECK_INTERVAL_MILLIS = 500L
        const val INITIAL_DELAY_MILLIS = 2000L
        const val RESTART_DELAY_MILLIS = 100L
        const val RETRY_DELAY_MILLIS = 100L
        const val MAX_START_RETRY = 10
        const val MAX_REFRESH_COUNT = 100
    }
}

