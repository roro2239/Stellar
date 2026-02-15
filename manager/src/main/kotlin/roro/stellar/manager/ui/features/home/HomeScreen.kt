package roro.stellar.manager.ui.features.home

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import roro.stellar.manager.domain.apps.AppsViewModel
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.ui.components.LocalScreenConfig
import roro.stellar.manager.ui.components.ModernActionCard
import roro.stellar.manager.ui.components.StellarConfirmDialog
import roro.stellar.manager.ui.components.StellarDialog
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
    appsViewModel: AppsViewModel,
    onNavigateToStarter: (isRoot: Boolean, host: String?, port: Int, hasSecureSettings: Boolean) -> Unit = { _, _, _, _ -> }
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val serviceStatusResource by homeViewModel.serviceStatus.observeAsState()
    val grantedCountResource by appsViewModel.grantedCount.observeAsState()
    val screenConfig = LocalScreenConfig.current

    val serviceStatus = serviceStatusResource?.data
    grantedCountResource?.data ?: 0

    val isRunning = serviceStatus?.isRunning ?: false
    val isRoot = serviceStatus?.uid == 0
    val isPrimaryUser = UserHandleCompat.myUserId() == 0
    val hasRoot = EnvironmentUtils.isRooted()

    var showStopDialog by remember { mutableStateOf(false) }
    var showAdbCommandDialog by remember { mutableStateOf(false) }

    val gridColumns = screenConfig.gridColumns

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
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                bottom = AppSpacing.screenBottomPadding,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.itemSpacing)
        ) {
            item(span = { GridItemSpan(gridColumns) }) {
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
                        StartRootCard(
                            isRestart = isRunning && isRoot,
                            onStartClick = { onNavigateToStarter(true, null, 0, false) }
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || EnvironmentUtils.getAdbTcpPort() > 0) {
                    item {
                        StartWirelessAdbCard(
                            onStartClick = {
                                // 直接导航到 StarterScreen，由 StarterScreen 检测环境状态
                                onNavigateToStarter(false, "127.0.0.1", 0, false)
                            }
                        )
                    }
                }

                item {
                    StartWiredAdbCard(
                        onButtonClick = { showAdbCommandDialog = true }
                    )
                }

                if (!hasRoot) {
                    item {
                        StartRootCard(
                            isRestart = isRunning && isRoot,
                            onStartClick = {
                                Toast.makeText(context, "没有 Root 权限", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showStopDialog) {
        StellarConfirmDialog(
            onDismissRequest = { showStopDialog = false },
            title = "停止服务",
            message = "Stellar 服务将被停止。",
            onConfirm = {
                if (Stellar.pingBinder()) {
                    try {
                        Stellar.exit()
                    } catch (_: Throwable) {
                    }
                }
                showStopDialog = false
            },
            onDismiss = { showStopDialog = false }
        )
    }

    if (showAdbCommandDialog) {
        StellarDialog(
            onDismissRequest = { showAdbCommandDialog = false },
            title = "查看指令",
            confirmText = "复制",
            onConfirm = {
                if (ClipboardUtils.put(context, Starter.adbCommand)) {
                    Toast.makeText(
                        context,
                        "${Starter.adbCommand}\n已被复制到剪贴板。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showAdbCommandDialog = false
            },
            onDismiss = { showAdbCommandDialog = false }
        ) {
            Text(
                text = Starter.adbCommand,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
