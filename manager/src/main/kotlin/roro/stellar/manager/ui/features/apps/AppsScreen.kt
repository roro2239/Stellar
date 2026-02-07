package roro.stellar.manager.ui.features.apps

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import roro.stellar.Stellar
import roro.stellar.manager.authorization.AuthorizationManager
import roro.stellar.manager.compat.Status
import roro.stellar.manager.management.AppsViewModel
import roro.stellar.manager.management.AppInfo
import roro.stellar.manager.management.AppType
import roro.stellar.manager.ui.components.StellarInfoDialog
import roro.stellar.manager.ui.components.StellarSegmentedSelector
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.util.Logger.Companion.LOGGER
import roro.stellar.manager.util.StellarSystemApis
import roro.stellar.manager.util.UserHandleCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    topAppBarState: TopAppBarState,
    appsViewModel: AppsViewModel
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val stellarAppsResource by appsViewModel.stellarApps.observeAsState()
    val shizukuAppsResource by appsViewModel.shizukuApps.observeAsState()
    var showPermissionError by remember { mutableStateOf(false) }
    val isServiceRunning = Stellar.pingBinder()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (Stellar.pingBinder()) {
                appsViewModel.load(true)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StandardLargeTopAppBar(
                title = "授权应用",
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        if (!isServiceRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = AppSpacing.topBarContentSpacing),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(visible = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {

                        Text(
                            text = "服务未运行",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "请先启动 Stellar 服务\n服务运行后可管理授权应用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else when (stellarAppsResource?.status) {
            Status.LOADING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Status.ERROR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(top = AppSpacing.topBarContentSpacing),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(visible = true) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {

                            Text(
                                text = "加载失败",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Text(
                                text = stellarAppsResource?.error?.message ?: "未知错误",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Status.SUCCESS -> {
                val stellarApps = stellarAppsResource?.data ?: emptyList()
                val shizukuApps = shizukuAppsResource?.data ?: emptyList()

                if (stellarApps.isEmpty() && shizukuApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(top = AppSpacing.topBarContentSpacing),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedVisibility(visible = true) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Text(
                                    text = "暂无授权应用",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "当前没有应用请求 Stellar 权限\n应用请求权限后会在这里显示",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                            bottom = AppSpacing.screenBottomPadding,
                            start = AppSpacing.screenHorizontalPadding,
                            end = AppSpacing.screenHorizontalPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Stellar 原生应用分组
                        if (stellarApps.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Stellar 应用",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(
                                count = stellarApps.size,
                                key = { index -> "stellar_${stellarApps[index].packageInfo.packageName}_${stellarApps[index].packageInfo.applicationInfo?.uid}" }
                            ) { index ->
                                val appInfo = stellarApps[index]
                                AppListItem(
                                    appInfo = appInfo,
                                    onUpdateFlag = { _ ->
                                        appsViewModel.load(true)
                                    }
                                )
                            }
                        }

                        // Shizuku 兼容应用分组
                        if (shizukuApps.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Shizuku 兼容应用",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(
                                count = shizukuApps.size,
                                key = { index -> "shizuku_${shizukuApps[index].packageInfo.packageName}_${shizukuApps[index].packageInfo.applicationInfo?.uid}" }
                            ) { index ->
                                val appInfo = shizukuApps[index]
                                AppListItem(
                                    appInfo = appInfo,
                                    onUpdateFlag = { _ ->
                                        appsViewModel.load(true)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            null -> {}
        }
    }

    if (showPermissionError) {
        StellarInfoDialog(
            onDismissRequest = { showPermissionError = false },
            title = "ADB 权限受限",
            message = "您的设备制造商很可能限制了 ADB 的权限。\n\n请在开发者选项中调整相关设置，或尝试使用 Root 模式运行。",
            onConfirm = { showPermissionError = false }
        )
    }
}

@Composable
fun AppListItem(
    appInfo: AppInfo,
    onUpdateFlag: (Int) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val packageInfo = appInfo.packageInfo
    val ai = packageInfo.applicationInfo ?: return

    val uid = ai.uid
    val userId = UserHandleCompat.getUserId(uid)
    val packageName = packageInfo.packageName
    val isShizukuApp = appInfo.appType == AppType.SHIZUKU

    val appName = remember(ai) {
        if (userId != UserHandleCompat.myUserId()) {
            try {
                val userInfo = StellarSystemApis.getUserInfo(userId)
                "${ai.loadLabel(pm)} - ${userInfo.name} ($userId)"
            } catch (_: Exception) {
                "${ai.loadLabel(pm)} - User $userId"
            }
        } else {
            ai.loadLabel(pm).toString()
        }
    }

    val iconPainter = remember(ai) {
        try {
            val drawable = ai.loadIcon(pm)
            val bitmap = drawableToBitmap(drawable)
            bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
        } catch (_: Exception) {
            null
        }
    }

    var stellarFlag by remember {
        mutableIntStateOf(
            try {
                Stellar.getFlagForUid(uid, "stellar")
            } catch (e: Exception) {
                LOGGER.w("获取应用授权状态异常", tr = e)
                AuthorizationManager.FLAG_ASK
            }
        )
    }

    var followStartupFlag by remember {
        mutableIntStateOf(
            try {
                Stellar.getFlagForUid(uid, "follow_stellar_startup")
            } catch (e: Exception) {
                LOGGER.w("获取跟随启动权限状态异常", tr = e)
                AuthorizationManager.FLAG_ASK
            }
        )
    }

    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = AppShape.shapes.cardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        val rotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f, label = "")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .run { this }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AppShape.shapes.iconSmall)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(AppShape.shapes.iconSmall)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = when (stellarFlag) {
                    AuthorizationManager.FLAG_ASK -> "询问"
                    AuthorizationManager.FLAG_GRANTED -> "允许"
                    AuthorizationManager.FLAG_DENIED -> "拒绝"
                    else -> "未知"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation).size(24.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionItem(
                    title = "基础权限",
                    subtitle = "使用 Stellar 功能",
                    currentFlag = stellarFlag,
                    onFlagChange = { newFlag ->
                        try {
                            stellarFlag = newFlag
                            Stellar.updateFlagForUid(uid, "stellar", newFlag)
                            onUpdateFlag(newFlag)
                        } catch (e: Exception) {
                            LOGGER.e("更新权限失败", tr = e)
                        }
                    }
                )

                // 只有 Stellar 原生应用才显示"跟随启动"选项
                if (!isShizukuApp) {
                    PermissionItem(
                        title = "跟随启动",
                        subtitle = "随 Stellar 一起启动",
                        currentFlag = followStartupFlag,
                        onFlagChange = { newFlag ->
                            try {
                                followStartupFlag = newFlag
                                Stellar.updateFlagForUid(uid, "follow_stellar_startup", newFlag)
                            } catch (e: Exception) {
                                LOGGER.e("更新跟随启动权限失败", tr = e)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    subtitle: String,
    currentFlag: Int,
    onFlagChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }

        PermissionSegmentSelector(
            currentFlag = currentFlag,
            onFlagChange = onFlagChange
        )
    }
}

@Composable
private fun PermissionSegmentSelector(
    currentFlag: Int,
    onFlagChange: (Int) -> Unit
) {
    val entries = listOf(
        AuthorizationManager.FLAG_ASK,
        AuthorizationManager.FLAG_GRANTED,
        AuthorizationManager.FLAG_DENIED
    )
    val labels = mapOf(
        AuthorizationManager.FLAG_ASK to "询问",
        AuthorizationManager.FLAG_GRANTED to "允许",
        AuthorizationManager.FLAG_DENIED to "拒绝"
    )

    StellarSegmentedSelector(
        items = entries,
        selectedItem = currentFlag,
        onItemSelected = onFlagChange,
        itemLabel = { labels[it] ?: "" },
        itemHeight = AppSpacing.selectorItemHeightSmall
    )
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }

    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48

    return try {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}