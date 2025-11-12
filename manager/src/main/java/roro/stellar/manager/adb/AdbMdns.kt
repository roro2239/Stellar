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

/**
 * ADB mDNS服务发现类
 * ADB mDNS Service Discovery Class
 * 
 * 功能说明 Features：
 * - 使用mDNS (Multicast DNS)自动发现本地无线ADB服务 - Auto discovers local wireless ADB services using mDNS
 * - 支持ADB连接和配对两种服务类型 - Supports both ADB connection and pairing service types
 * - 验证服务是否在本地网络 - Validates if service is on local network
 * - 检测端口可用性 - Checks port availability
 * 
 * 服务类型 Service Types：
 * - TLS_CONNECT: _adb-tls-connect._tcp - ADB连接服务
 * - TLS_PAIRING: _adb-tls-pairing._tcp - ADB配对服务
 * 
 * 工作流程 Workflow：
 * - 1. start() 开始mDNS服务发现
 * - 2. 自动监听服务发现和丢失事件
 * - 3. 发现服务后解析服务信息
 * - 4. 验证服务在本地且端口可用
 * - 5. 通过Observer回调端口号
 * - 6. stop() 停止服务发现
 * 
 * 验证机制 Validation Mechanism：
 * - 检查服务主机地址是否在本机网络接口上
 * - 检查端口是否已被占用（通过尝试绑定）
 * - 仅接受本地网络上的服务
 * 
 * 使用场景 Use Cases：
 * - 自动发现无线ADB连接端口
 * - 自动发现无线ADB配对端口
 * - Android 11+无线调试功能
 * 
 * 注意事项 Notes：
 * - 仅限Android 11+
 * - 需要WiFi连接
 * - mDNS在某些网络配置下可能不可用
 * - 服务发现可能需要几秒钟
 */
@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context, 
    /** 服务类型 Service type */
    private val serviceType: String,
    /** 端口号观察者 Port observer */
    private val observer: Observer<Int>
) {

    /** 是否已注册监听器 Whether listener is registered */
    private var registered = false
    /** 是否正在运行 Whether running */
    private var running = false
    /** 当前服务名称 Current service name */
    private var serviceName: String? = null
    /** 服务发现监听器 Service discovery listener */
    private val listener = DiscoveryListener(this)
    /** NSD管理器 NSD manager */
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    
    /** 后台线程执行器，用于耗时的网络检查操作 */
    private val executor = Executors.newSingleThreadExecutor()
    
    /** 主线程Handler，用于回调Observer */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 开始服务发现
     * Start service discovery
     * 
     * 启动mDNS服务发现进程
     */
    fun start() {
        if (running) return
        running = true
        if (!registered) {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    /**
     * 停止服务发现
     * Stop service discovery
     * 
     * 停止mDNS服务发现进程
     */
    fun stop() {
        if (!running) return
        running = false
        if (registered) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "停止服务发现失败", e)
            }
        }
        // 关闭线程池
        executor.shutdown()
    }

    /**
     * 服务发现启动回调
     * Discovery start callback
     */
    private fun onDiscoveryStart() {
        registered = true
    }

    /**
     * 服务发现停止回调
     * Discovery stop callback
     */
    private fun onDiscoveryStop() {
        registered = false
    }

    /**
     * 发现新服务回调
     * Service found callback
     * 
     * 解析发现的服务以获取详细信息
     */
    @Suppress("DEPRECATION")
    private fun onServiceFound(info: NsdServiceInfo) {
        nsdManager.resolveService(info, ResolveListener(this))
    }

    /**
     * 服务丢失回调
     * Service lost callback
     * 
     * 通知观察者端口不可用
     */
    private fun onServiceLost(info: NsdServiceInfo) {
        if (info.serviceName == serviceName) observer.onChanged(-1)
    }

    /**
     * 服务解析完成回调
     * Service resolved callback
     * 
     * 验证服务是否在本地网络且端口可用
     */
    @Suppress("DEPRECATION")
    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        if (!running) return
        
        // 在后台线程执行耗时的网络检查
        executor.execute {
            try {
                // 检查服务是否在本地网络接口上
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
                
                // 检查端口是否可用
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

    /**
     * 检查端口是否可用
     * Check if port is available
     * 
     * 通过尝试绑定端口来检测是否可用
     * @return true表示端口已被占用（可用于连接）
     */
    private fun isPortAvailable(port: Int) = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (e: IOException) {
        true
    }

    /**
     * 服务发现监听器
     * Service Discovery Listener
     * 
     * 处理mDNS服务发现的各种事件
     */
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

    /**
     * 服务解析监听器
     * Service Resolve Listener
     * 
     * 处理服务解析结果
     */
    internal class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {}

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }

    }

    companion object {
        /** ADB TLS连接服务类型 ADB TLS connect service type */
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        /** ADB TLS配对服务类型 ADB TLS pairing service type */
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        /** 日志标签 Log tag */
        const val TAG = "AdbMdns"
    }
}

