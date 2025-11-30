package roro.stellar.manager.ui.features.home.others

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.utils.EnvironmentUtils

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun AdbAutoConnect(
    onStartConnection: (Int) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: WirelessAdbViewModel = viewModel(
        factory = WirelessAdbViewModelFactory(context)
    )
    
    val discoveredPort by viewModel.port.collectAsState()
    val systemPort = remember { EnvironmentUtils.getAdbTcpPort() }
    
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cr = context.contentResolver
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            } catch (e: Exception) {
            }
        }
        
        if (systemPort in 1..65535) {
            onStartConnection(systemPort)
            onComplete()
        } else {
            viewModel.startDiscovery()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }
    
    LaunchedEffect(discoveredPort) {
        if (discoveredPort > 0 && discoveredPort <= 65535) {
            onStartConnection(discoveredPort)
            onComplete()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
class WirelessAdbViewModel(private val context: Context) : ViewModel() {
    private val _port = MutableStateFlow(-1)
    val port: StateFlow<Int> = _port
    
    private var adbMdns: AdbMdns? = null
    
    fun startDiscovery() {
        adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
            _port.value = port
        }
        adbMdns?.start()
    }
    
    fun stopDiscovery() {
        adbMdns?.stop()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}

class WirelessAdbViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @RequiresApi(Build.VERSION_CODES.R)
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WirelessAdbViewModel(context) as T
    }
}