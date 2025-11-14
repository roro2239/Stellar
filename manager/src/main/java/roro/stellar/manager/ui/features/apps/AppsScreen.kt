package roro.stellar.manager.ui.features.apps

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.utils.StellarSystemApis
import roro.stellar.manager.utils.UserHandleCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    topAppBarState: TopAppBarState,
    appsViewModel: AppsViewModel
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val packagesResource by appsViewModel.packages.observeAsState()
    var showPermissionError by remember { mutableStateOf(false) }
    val isServiceRunning = Stellar.pingBinder()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // 每次页面可见时刷新列表
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
                title = "Stellar",
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
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + scaleIn()
                ) {
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
        } else when (packagesResource?.status) {
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
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn()
                    ) {
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
                                text = packagesResource?.error?.message ?: "未知错误",
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
                val packages = packagesResource?.data ?: emptyList()
                
                if (packages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(top = AppSpacing.topBarContentSpacing),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + scaleIn()
                        ) {
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
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.itemSpacing)
                    ) {
                        items(
                            count = packages.size,
                            key = { index -> "${packages[index].packageName}_${packages[index].applicationInfo?.uid}" }
                        ) { index ->
                            val packageInfo = packages[index]
                            AppListItem(
                                packageInfo = packageInfo,
                                onToggle = { granted ->
                                    try {
                                        val packageName = packageInfo.packageName
                                        val uid = packageInfo.applicationInfo!!.uid
                                        
                                        if (granted) {
                                            AuthorizationManager.grant(packageName, uid)
                                        } else {
                                            AuthorizationManager.revoke(packageName, uid)
                                        }
                                        
                                        appsViewModel.load(true)
                                    } catch (e: SecurityException) {
                                        showPermissionError = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            null -> {}
        }
    }

    if (showPermissionError) {
        AlertDialog(
            onDismissRequest = { showPermissionError = false },
            title = { Text("ADB 权限受限") },
            text = { Text("您的设备制造商很可能限制了 ADB 的权限。\n\n请在开发者选项中调整相关设置，或尝试使用 Root 模式运行。") },
            confirmButton = {
                TextButton(onClick = { showPermissionError = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun AppListItem(
    packageInfo: PackageInfo,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val ai = packageInfo.applicationInfo ?: return
    
    val uid = ai.uid
    val userId = UserHandleCompat.getUserId(uid)
    val packageName = packageInfo.packageName
    
    val isGranted = remember(packageName, uid) {
        try {
            AuthorizationManager.granted(packageName, uid)
        } catch (e: Exception) {
            false
        }
    }
    
    var checked by remember(packageName, uid) { mutableStateOf(isGranted) }
    
    LaunchedEffect(packageName, uid) {
        checked = try {
            AuthorizationManager.granted(packageName, uid)
        } catch (e: Exception) {
            false
        }
    }
    
    val appName = remember(ai) {
        if (userId != UserHandleCompat.myUserId()) {
            try {
                val userInfo = StellarSystemApis.getUserInfo(userId)
                "${ai.loadLabel(pm)} - ${userInfo.name} ($userId)"
            } catch (e: Exception) {
                "${ai.loadLabel(pm)} - User $userId"
            }
        } else {
            ai.loadLabel(pm).toString()
        }
    }
    
    val requiresRoot = remember(ai) {
        try {
            ai.metaData?.getBoolean("com.stellar.client.V3_REQUIRES_ROOT") == true
        } catch (e: Exception) {
            false
        }
    }
    
    val iconPainter = remember(ai) {
        try {
            val drawable = ai.loadIcon(pm)
            val bitmap = drawableToBitmap(drawable)
            bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                onToggle(checked)
            },
        shape = AppShape.shapes.cardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(AppShape.shapes.iconSmall)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(AppShape.shapes.iconSmall)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
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
                if (requiresRoot) {
                    Text(
                        text = "需要 Root",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    onToggle(it)
                }
            )
        }
    }
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
    } catch (e: Exception) {
        null
    }
}