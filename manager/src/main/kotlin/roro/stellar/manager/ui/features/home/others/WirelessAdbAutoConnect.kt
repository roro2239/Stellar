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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.util.EnvironmentUtils

@OptIn(DelicateCoroutinesApi::class)
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

        val tcpipPortEnabled = preferences.getBoolean(StellarSettings.TCPIP_PORT_ENABLED, true)
        val settingsPort = preferences.getString(StellarSettings.TCPIP_PORT, "")?.toIntOrNull()
        val systemPort = EnvironmentUtils.getAdbTcpPort()

        fun enableWirelessAdb() {
            try {
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            } catch (_: Exception) {
            }
        }

        var handled = false

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
                onMaxRefresh = {
                    if (!handled) {
                        handled = true
                        onNeedsPairing()
                        onComplete()
                    }
                },
                maxRefreshCount = 3
            ).apply { start() }
        }

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
                onMaxRefresh = {
                    if (!handled) {
                        handled = true
                        if (systemPort in 1..65535) {
                            GlobalScope.launch(Dispatchers.IO) {
                                val hasPermission = adbWirelessHelper.hasAdbPermission("127.0.0.1", systemPort)
                                withContext(Dispatchers.Main) {
                                    if (hasPermission) {
                                        onStartConnection(systemPort, hasSecureSettings)
                                        onComplete()
                                    } else if (hasSecureSettings) {
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
                maxRefreshCount = 3
            ).apply { start() }
        }

        if (tcpipPortEnabled && settingsPort != null && settingsPort in 1..65535) {
            GlobalScope.launch(Dispatchers.IO) {
                val hasPermission = adbWirelessHelper.hasAdbPermission("127.0.0.1", settingsPort)
                withContext(Dispatchers.Main) {
                    if (hasPermission) {
                        handled = true
                        onStartConnection(settingsPort, hasSecureSettings)
                        onComplete()
                    } else {
                        startMdnsScan()
                    }
                }
            }
            return@LaunchedEffect
        }

        startMdnsScan()
    }
}