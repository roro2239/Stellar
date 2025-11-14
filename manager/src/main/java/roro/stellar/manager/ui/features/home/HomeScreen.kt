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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import rikka.shizuku.Shizuku
import roro.stellar.Stellar
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.management.AppsViewModel
import roro.stellar.manager.ui.components.ModernActionCard
import roro.stellar.manager.ui.components.ModernSettingCard
import roro.stellar.manager.ui.features.starter.Starter
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.utils.EnvironmentUtils
import roro.stellar.manager.utils.UserHandleCompat

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
    val hasPermission = serviceStatus?.permission ?: false
    val isRoot = serviceStatus?.uid == 0
    val isPrimaryUser = UserHandleCompat.myUserId() == 0
    val hasRoot = EnvironmentUtils.isRooted()
    
    var showStopDialog by remember { mutableStateOf(false) }
    var showAdbCommandDialog by remember { mutableStateOf(false) }
    var triggerAdbAutoConnect by remember { mutableStateOf(false) }
    
    // 使用响应式状态跟踪Shizuku可用性
    var isShizukuAvailable by remember { 
        mutableStateOf(roro.stellar.manager.ui.features.starter.ShizukuStarter.isShizukuAvailable()) 
    }
    
    // 监听Shizuku状态变化
    DisposableEffect(Unit) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            isShizukuAvailable = true
        }
        
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            isShizukuAvailable = false
        }
        
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (_: Exception) {
            // Shizuku未安装或不可用
        }
        
        onDispose {
            try {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
            } catch (_: Exception) {
                // 忽略
            }
        }
    }
    
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
            // 服务状态卡片
            item {
                ServerStatusCard(
                    isRunning = isRunning,
                    isRoot = isRoot,
                    apiVersion = serviceStatus?.apiVersion ?: 0,
                    patchVersion = serviceStatus?.patchVersion ?: 0
                )
            }

            // 停止服务按钮
            if (isRunning) {
                item {
                    ModernSettingCard(
                        icon = Icons.Default.Stop,
                        title = "停止服务",
                        subtitle = "Stellar 服务将被停止",
                        onClick = { showStopDialog = true },
                        iconBackgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.error,
                        showArrow = false
                    )
                }
            }

            // 权限受限提示
            if (isRunning && !hasPermission) {
                item {
                    ModernSettingCard(
                        icon = Icons.Default.Lock,
                        title = "权限受限",
                        subtitle = "Stellar 没有足够的权限",
                        onClick = null,
                        showArrow = false,
                        iconBackgroundColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.error,
                        cardBackgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                }
            }

            if (isPrimaryUser) {
                if (hasRoot) {
                    item {
                        StartRootCard(isRestart = isRunning && isRoot)
                    }
                }

                // Shizuku卡片 - 如果服务已运行，放在前面
                if (isShizukuAvailable) {
                    item {
                        StartShizukuCard(isRestart = isRunning && !isRoot)
                    }
                }

                // 无线调试卡片
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
                                    // 触发自动连接（会自动搜索端口并启动）
                                    triggerAdbAutoConnect = true
                                }
                            }
                        )
                    }
                }

                // 有线ADB卡片
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

                // Shizuku卡片 - 如果服务未运行，放在最后
                if (!isShizukuAvailable) {
                    item {
                        StartShizukuCard(isRestart = isRunning && !isRoot)
                    }
                }
            }
        }
    }

    // 停止服务确认对话框
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

    // ADB命令对话框
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
    
    // 无线调试自动连接处理
    if (triggerAdbAutoConnect && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        roro.stellar.manager.ui.features.home.others.AdbAutoConnect(
            onStartConnection = { port ->
                val helper = roro.stellar.manager.adb.AdbWirelessHelper()
                helper.launchStarterActivity(context, "127.0.0.1", port)
            },
            onComplete = { triggerAdbAutoConnect = false }
        )
    }
}