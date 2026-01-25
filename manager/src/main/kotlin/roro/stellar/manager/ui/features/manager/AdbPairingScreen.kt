package roro.stellar.manager.ui.features.manager

import android.Manifest
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import roro.stellar.manager.AppConstants
import roro.stellar.manager.adb.AdbPairingService
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
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
internal fun AdbPairingScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) Toast.makeText(context, "需要通知权限才能继续配对", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(hasNotificationPermission) {
        if (hasNotificationPermission && isNotificationEnabled(context)) {
            startPairingService(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FixedTopAppBar(
                title = "无线调试配对",
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        PairingContent(paddingValues, hasNotificationPermission, permissionLauncher, context)
    }
}

@Composable
private fun PairingContent(
    paddingValues: PaddingValues,
    hasNotificationPermission: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    context: android.content.Context
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(horizontal = AppSpacing.screenHorizontalPadding,
                     vertical = AppSpacing.topBarContentSpacing)
            .padding(bottom = AppSpacing.screenBottomPadding)
    ) {
        TimelineStep(true, false, "授权通知权限", Icons.Default.Notifications, hasNotificationPermission,
            if (hasNotificationPermission) "通知权限已授予" else "必须授予通知权限才能进行配对") {
            if (!hasNotificationPermission) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "无法打开通知设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, Modifier.fillMaxWidth(), shape = AppShape.shapes.cardMedium) {
                    Text("授予权限", Modifier.padding(vertical = 4.dp))
                }
            }
        }

        TimelineStep(false, false, "打开无线调试", Icons.Default.Wifi, false,
            "在开发者选项中启用无线调试功能", hasNotificationPermission) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                 putExtra(":settings:fragment_args_key", "toggle_adb_wireless") })
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
                }
            }, Modifier.fillMaxWidth(), enabled = hasNotificationPermission, shape = AppShape.shapes.cardMedium) {
                Text("打开开发者选项", Modifier.padding(vertical = 4.dp))
            }
        }

        TimelineStep(false, false, "使用配对码配对", Icons.Default.QrCode, false,
            "在无线调试页面点击「使用配对码配对设备」", hasNotificationPermission)

        TimelineStep(false, false, "输入配对信息", Icons.Default.PhoneAndroid, false,
            "在通知中心的 Stellar 通知中输入系统显示的配对码", hasNotificationPermission)

        TimelineStep(false, true, "系统要求", Icons.Default.Info, false,
            "• Android 11 及以上\n• WiFi 网络连接\n• 通知权限", true)
    }
}

@Composable
private fun TimelineStep(
    @Suppress("UNUSED_PARAMETER") isFirst: Boolean,
    isLast: Boolean,
    title: String,
    icon: ImageVector,
    isCompleted: Boolean,
    description: String,
    enabled: Boolean = true,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
            Box(
                modifier = Modifier.size(36.dp)
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        }, CircleShape
                    ).padding(6.dp)
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.primary
                        }, CircleShape
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
                Box(Modifier.width(2.dp).height(48.dp).background(
                    if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                ))
            }
        }
        TimelineStepContent(isLast, title, icon, isCompleted, description, enabled, action)
    }
}

@Composable
private fun RowScope.TimelineStepContent(
    isLast: Boolean,
    title: String,
    icon: ImageVector,
    isCompleted: Boolean,
    description: String,
    enabled: Boolean,
    action: (@Composable ColumnScope.() -> Unit)?
) {
    Surface(
        modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 12.dp),
        shape = AppShape.shapes.cardLarge,
        color = when {
            isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primaryContainer
                            !enabled -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }, AppShape.shapes.iconSmall
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
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
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            action?.invoke(this)
        }
    }
}
