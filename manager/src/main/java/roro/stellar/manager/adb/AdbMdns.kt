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
    private val onTimeout: (() -> Unit)? = null
) {

    private var registered = false
    private var running = false
    private var serviceName: String? = null
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    
    private val executor = Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var startTime: Long = 0

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (running) {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime >= TIMEOUT_MILLIS) {
                    Log.v(TAG, "搜索超时，自动停止")
                    stop()
                    onTimeout?.invoke()
                    return
                }

                if (registered) {
                    Log.v(TAG, "定期刷新服务发现")
                    try {
                        nsdManager.stopServiceDiscovery(listener)
                    } catch (e: Exception) {
                        Log.e(TAG, "停止服务发现失败", e)
                    }
                    registered = false
                }

                mainHandler.postDelayed({
                    if (running && !registered) {
                        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                    }
                }, 50)
                mainHandler.postDelayed(this, 50)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        startTime = System.currentTimeMillis()
        if (!registered) {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            mainHandler.postDelayed(refreshRunnable, 50)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        mainHandler.removeCallbacks(refreshRunnable)
        if (registered) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "停止服务发现失败", e)
            }
        }
        executor.shutdown()
    }

    private fun onDiscoveryStart() {
        registered = true
    }

    private fun onDiscoveryStop() {
        registered = false
    }

    @Suppress("DEPRECATION")
    private fun onServiceFound(info: NsdServiceInfo) {
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
                
                if (!isLocalService || !running) {
                    return@execute
                }
                
                val portAvailable = isPortAvailable(resolvedService.port)
                
                if (portAvailable && running) {
                    mainHandler.post {
                        if (running) {
                            serviceName = resolvedService.serviceName
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

            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "开始发现失败: $serviceType, $errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "发现已停止: $serviceType")

            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "停止发现失败: $serviceType, $errorCode")
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
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {}

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }

    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
        const val REFRESH_INTERVAL_MILLIS = 500L
        const val TIMEOUT_MILLIS = 3 * 60 * 1000L // 3分钟
    }
}

