package roro.stellar.manager.ui.features.home.others

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.util.EnvironmentUtils

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun AdbAutoConnect(
    onStartConnection: (port: Int, hasSecureSettings: Boolean) -> Unit,
    onNeedsPairing: () -> Unit,
    onAskEnableWirelessAdb: (onConfirm: () -> Unit) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val adbWirelessHelper = remember { AdbWirelessHelper() }
    var adbMdns by remember { mutableStateOf<AdbMdns?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            adbMdns?.stop()
        }
    }

    LaunchedEffect(key1 = Unit) {
        val cr = context.contentResolver
        val preferences = StellarSettings.getPreferences()
        val hasSecureSettings = context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        // 获取端口
        val tcpipPortEnabled = preferences.getBoolean(StellarSettings.TCPIP_PORT_ENABLED, true)
        val settingsPort = preferences.getString(StellarSettings.TCPIP_PORT, "")?.toIntOrNull()
        val systemPort = EnvironmentUtils.getAdbTcpPort()

        // 定义启用无线调试的函数
        fun enableWirelessAdb() {
            try {
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            } catch (_: Exception) {
            }
        }

        // 用于标记是否已处理
        var handled = false

        // 第二轮扫描（开启无线调试后）
        fun startSecondRoundScan() {
            handled = false
            val secondObserver = Observer<Int> { discoveredPort ->
                if (discoveredPort in 1..65535 && !handled) {
                    handled = true
                    adbMdns?.stop()
                    adbMdns = null
                    GlobalScope.launch(Dispatchers.IO) {
                        val hasPermission = adbWirelessHelper.hasAdbPermission("127.0.0.1", discoveredPort)
                        withContext(Dispatchers.Main) {
                            if (hasPermission) {
                                onStartConnection(discoveredPort, hasSecureSettings)
                            } else {
                                onNeedsPairing()
                            }
                            onComplete()
                        }
                    }
                }
            }

            adbMdns = AdbMdns(
                context = context,
                serviceType = AdbMdns.TLS_CONNECT,
                observer = secondObserver,
                onTimeout = {
                    if (!handled) {
                        handled = true
                        // 第二轮超时，进入配对页面
                        onNeedsPairing()
                        onComplete()
                    }
                },
                timeoutMillis = 1000L  // 第二轮给更长时间
            ).apply { start() }
        }

        // 定义 mDNS 扫描函数
        fun startMdnsScan() {
            val portObserver = Observer<Int> { discoveredPort ->
                if (discoveredPort in 1..65535 && !handled) {
                    handled = true
                    adbMdns?.stop()
                    adbMdns = null
                    GlobalScope.launch(Dispatchers.IO) {
                        val hasPermission = adbWirelessHelper.hasAdbPermission("127.0.0.1", discoveredPort)
                        withContext(Dispatchers.Main) {
                            if (hasPermission) {
                                onStartConnection(discoveredPort, hasSecureSettings)
                            } else {
                                onNeedsPairing()
                            }
                            onComplete()
                        }
                    }
                }
            }

            adbMdns = AdbMdns(
                context = context,
                serviceType = AdbMdns.TLS_CONNECT,
                observer = portObserver,
                onTimeout = {
                    if (!handled) {
                        handled = true
                        // 超时后尝试使用系统端口
                        if (systemPort in 1..65535) {
                            GlobalScope.launch(Dispatchers.IO) {
                                val hasPermission = adbWirelessHelper.hasAdbPermission("127.0.0.1", systemPort)
                                withContext(Dispatchers.Main) {
                                    if (hasPermission) {
                                        onStartConnection(systemPort, hasSecureSettings)
                                        onComplete()
                                    } else if (hasSecureSettings) {
                                        // 有权限但连接失败，询问是否开启无线调试
                                        onAskEnableWirelessAdb {
                                            enableWirelessAdb()
                                            startSecondRoundScan()
                                        }
                                    } else {
                                        onNeedsPairing()
                                        onComplete()
                                    }
                                }
                            }
                        } else if (hasSecureSettings) {
                            // 没有端口但有权限，询问是否开启无线调试
                            onAskEnableWirelessAdb {
                                enableWirelessAdb()
                                startSecondRoundScan()
                            }
                        } else {
                            onNeedsPairing()
                            onComplete()
                        }
                    }
                },
                timeoutMillis = 500L
            ).apply { start() }
        }

        // 如果设置了自定义端口，优先使用自定义端口检测
        if (tcpipPortEnabled && settingsPort != null && settingsPort in 1..65535) {
            GlobalScope.launch(Dispatchers.IO) {
                val hasPermission = adbWirelessHelper.hasAdbPermission("127.0.0.1", settingsPort)
                withContext(Dispatchers.Main) {
                    if (hasPermission) {
                        handled = true
                        onStartConnection(settingsPort, hasSecureSettings)
                        onComplete()
                    } else {
                        // 自定义端口不可用，继续 mDNS 扫描
                        startMdnsScan()
                    }
                }
            }
            return@LaunchedEffect
        }

        // 没有自定义端口，直接使用 mDNS 扫描
        startMdnsScan()
    }
}