package roro.stellar.manager.ui.features.home

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import roro.stellar.Stellar
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.management.AppsViewModel
import roro.stellar.manager.ui.components.ModernActionCard
import roro.stellar.manager.ui.features.starter.Starter
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.util.EnvironmentUtils
import roro.stellar.manager.util.UserHandleCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    topAppBarState: TopAppBarState,
    homeViewModel: HomeViewModel,
    appsViewModel: AppsViewModel
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val serviceStatusResource by homeViewModel.serviceStatus.observeAsState()
    val grantedCountResource by appsViewModel.grantedCount.observeAsState()
    
    val serviceStatus = serviceStatusResource?.data
    grantedCountResource?.data ?: 0
    
    val isRunning = serviceStatus?.isRunning ?: false
    val isRoot = serviceStatus?.uid == 0
    val isPrimaryUser = UserHandleCompat.myUserId() == 0
    val hasRoot = EnvironmentUtils.isRooted()
    
    var showStopDialog by remember { mutableStateOf(false) }
    var showAdbCommandDialog by remember { mutableStateOf(false) }
    var triggerAdbAutoConnect by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StandardLargeTopAppBar(
                title = "Stellar",
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                bottom = AppSpacing.screenBottomPadding,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.itemSpacing)
        ) {
            item {
                ServerStatusCard(
                    isRunning = isRunning,
                    isRoot = isRoot,
                    apiVersion = serviceStatus?.apiVersion ?: 0,
                    patchVersion = serviceStatus?.patchVersion ?: 0,
                    onStopClick = {
                        showStopDialog = true
                    }
                )
            }

            if (isPrimaryUser) {
                if (hasRoot) {
                    item {
                        StartRootCard(isRestart = isRunning && isRoot)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0) {
                    item {
                        StartWirelessAdbCard(
                            onPairClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    context.startActivity(
                                        Intent(context, roro.stellar.manager.ui.features.home.others.AdbPairingTutorialActivity::class.java)
                                    )
                                }
                            },
                            onStartClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    triggerAdbAutoConnect = true
                                }
                            }
                        )
                    }
                }

                item {
                    ModernActionCard(
                        icon = Icons.Default.Cable,
                        title = "有线ADB",
                        subtitle = "通过 ADB 启动 Stellar 服务",
                        buttonText = "查看",
                        onButtonClick = { showAdbCommandDialog = true }
                    )
                }

                if (!hasRoot) {
                    item {
                        StartRootCard(isRestart = isRunning && isRoot)
                    }
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("停止服务") },
            text = { Text("Stellar 服务将被停止。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (Stellar.pingBinder()) {
                            try {
                                Stellar.exit()
                            } catch (_: Throwable) {
                            }
                        }
                        showStopDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAdbCommandDialog) {
        AlertDialog(
            onDismissRequest = { showAdbCommandDialog = false },
            title = { Text("查看指令") },
            text = { 
                Text(Starter.adbCommand)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (ClipboardUtils.put(context, Starter.adbCommand)) {
                            Toast.makeText(
                                context,
                                "${Starter.adbCommand}\n已被复制到剪贴板。",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showAdbCommandDialog = false
                    }
                ) {
                    Text("复制")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdbCommandDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    if (triggerAdbAutoConnect && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        androidx.compose.runtime.key(System.currentTimeMillis()) {
            roro.stellar.manager.ui.features.home.others.AdbAutoConnect(
                onStartConnection = { port ->
                    val helper = roro.stellar.manager.adb.AdbWirelessHelper()
                    helper.launchStarterActivity(context, "127.0.0.1", port)
                },
                onComplete = { triggerAdbAutoConnect = false }
            )
        }
    }
}