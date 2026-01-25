package roro.stellar.manager.ui.features.home.others

import android.Manifest
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import roro.stellar.manager.AppConstants
import roro.stellar.manager.adb.AdbPairingService
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing

private fun isNotificationEnabled(context: android.content.Context): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java)
    val channel = nm.getNotificationChannel(AdbPairingService.notificationChannel)
    return nm.areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
}

private fun startPairingService(context: android.content.Context) {
    val intent = AdbPairingService.startIntent(context)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (e: Throwable) {
        Log.e(AppConstants.TAG, "启动前台服务失败", e)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && e is ForegroundServiceStartNotAllowedException
        ) {
            val mode = context.getSystemService(AppOpsManager::class.java)
                .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), context.packageName, null, null)
            if (mode == AppOpsManager.MODE_ERRORED) {
                Toast.makeText(context, "前台服务权限被拒绝，请检查权限设置", Toast.LENGTH_LONG).show()
            }
            context.startService(intent)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbPairingTutorialScreen(
    topAppBarState: TopAppBarState,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.areNotificationsEnabled()
            }
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "需要通知权限才能继续配对", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(hasNotificationPermission) {
        if (hasNotificationPermission && isNotificationEnabled(context)) {
            startPairingService(context)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = "无线调试配对",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(
                    start = AppSpacing.screenHorizontalPadding,
                    end = AppSpacing.screenHorizontalPadding,
                    top = AppSpacing.topBarContentSpacing,
                    bottom = AppSpacing.screenBottomPadding
                )
        ) {
            TimelineStep(
                isFirst = true,
                isLast = false,
                title = "授权通知权限",
                icon = Icons.Default.Notifications,
                isCompleted = hasNotificationPermission,
                description = if (hasNotificationPermission) {
                    "通知权限已授予"
                } else {
                    "必须授予通知权限才能进行配对"
                }
            ) {
                if (!hasNotificationPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "无法打开通知设置", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShape.shapes.cardMedium
                    ) {
                        Text("授予权限", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            TimelineStep(
                isFirst = false,
                isLast = false,
                title = "打开无线调试",
                icon = Icons.Default.Wifi,
                isCompleted = false,
                description = "在开发者选项中启用无线调试功能",
                enabled = hasNotificationPermission
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasNotificationPermission,
                    shape = AppShape.shapes.cardMedium
                ) {
                    Text("打开开发者选项", modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            TimelineStep(
                isFirst = false,
                isLast = false,
                title = "使用配对码配对",
                icon = Icons.Default.QrCode,
                isCompleted = false,
                description = "在无线调试页面点击「使用配对码配对设备」",
                enabled = hasNotificationPermission
            )

            TimelineStep(
                isFirst = false,
                isLast = false,
                title = "输入配对信息",
                icon = Icons.Default.PhoneAndroid,
                isCompleted = false,
                description = "在通知中心的 Stellar 通知中输入系统显示的配对码",
                enabled = hasNotificationPermission
            )

            TimelineStep(
                isFirst = false,
                isLast = true,
                title = "系统要求",
                icon = Icons.Default.Info,
                isCompleted = false,
                description = "• Android 11 及以上\n• WiFi 网络连接\n• 通知权限",
                enabled = true
            )
        }
    }
}

@Composable
fun TimelineStep(
    @Suppress("UNUSED_PARAMETER") isFirst: Boolean,
    isLast: Boolean,
    title: String,
    icon: ImageVector,
    isCompleted: Boolean,
    description: String,
    enabled: Boolean = true,
    isWarning: Boolean = false,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when {
                            isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        },
                        shape = CircleShape
                    )
                    .padding(6.dp)
                    .background(
                        color = when {
                            isWarning -> MaterialTheme.colorScheme.error
                            isCompleted -> MaterialTheme.colorScheme.primary
                            !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(
                            color = when {
                                isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            }
                        )
                )
            }
        }
        
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp),
            shape = AppShape.shapes.cardLarge,
            color = when {
                isWarning -> MaterialTheme.colorScheme.errorContainer
                isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                !enabled -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = when {
                                    isWarning -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    isCompleted -> MaterialTheme.colorScheme.primaryContainer
                                    !enabled -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = AppShape.shapes.iconSmall
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = when {
                                isWarning -> MaterialTheme.colorScheme.error
                                isCompleted -> MaterialTheme.colorScheme.primary
                                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isWarning -> MaterialTheme.colorScheme.onErrorContainer
                            enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isWarning -> MaterialTheme.colorScheme.onErrorContainer
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                
                action?.invoke(this)
            }
        }
    }
}