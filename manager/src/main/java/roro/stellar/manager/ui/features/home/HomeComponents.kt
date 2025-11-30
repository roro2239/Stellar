package roro.stellar.manager.ui.features.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku
import roro.stellar.Stellar
import roro.stellar.StellarApiConstants
import roro.stellar.manager.ui.components.ModernActionCard
import roro.stellar.manager.ui.components.ModernStatusCard
import roro.stellar.manager.ui.features.starter.StarterActivity
import roro.stellar.manager.ui.theme.AppShape

@Composable
fun ServerStatusCard(
    isRunning: Boolean,
    isRoot: Boolean,
    apiVersion: Int,
    patchVersion: Int,
    onStopClick: () -> Unit
) {
    val user = if (isRoot) "Root" else "ADB"
    val needsUpdate = isRunning && (apiVersion != Stellar.latestServiceVersion ||
            patchVersion != StellarApiConstants.SERVER_PATCH_VERSION)

    ModernStatusCard(
        icon = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Error,
        title = "服务状态",
        subtitle = "",
        statusText = if (isRunning) "正在运行" else "未运行",
        isPositive = isRunning
    ) {
        if (isRunning) {
            val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow("版本", "$apiVersion.$patchVersion", contentColor)
            InfoRow("运行模式", user, contentColor)

            if (needsUpdate) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = contentColor.copy(alpha = 0.15f),
                    shape = AppShape.shapes.iconSmall
                ) {
                    Text(
                        text = "💡 可升级到版本 ${Stellar.latestServiceVersion}.${StellarApiConstants.SERVER_PATCH_VERSION}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStopClick,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.shapes.buttonMedium,
                colors = ButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = Color.Unspecified,
                    disabledContentColor = Color.Unspecified,
                )
            ) {
                Text(
                    text = "停止服务",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
fun StartRootCard(isRestart: Boolean) {
    val context = LocalContext.current

    ModernActionCard(
        icon = Icons.Default.Security,
        title = if (isRestart) "Root 重启" else "Root 启动",
        subtitle = "通过 Root 权限启动 Stellar 服务",
        buttonText = if (isRestart) "重启" else "启动",
        onButtonClick = {
            val intent = Intent(context, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, true)
            }
            context.startActivity(intent)
        }
    )
}

@Composable
fun StartWirelessAdbCard(
    onPairClick: () -> Unit,
    onStartClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = AppShape.shapes.iconSmall
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "无线调试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "仅限 Android 11 以上设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "首次使用需要先配对，配对成功后可直接启动",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onPairClick,
                    modifier = Modifier.weight(1f),
                    shape = AppShape.shapes.buttonMedium
                ) {
                    Text(
                        text = "配对",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Button(
                    onClick = onStartClick,
                    modifier = Modifier.weight(1f),
                    shape = AppShape.shapes.buttonMedium
                ) {
                    Text(
                        text = "启动",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StartShizukuCard(isRestart: Boolean) {
    val context = LocalContext.current

    var isShizukuAvailable by remember { mutableStateOf(roro.stellar.manager.ui.features.starter.ShizukuStarter.isShizukuAvailable()) }
    var hasPermission by remember { mutableStateOf(roro.stellar.manager.ui.features.starter.ShizukuStarter.checkPermission()) }

    DisposableEffect(Unit) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            isShizukuAvailable =
                roro.stellar.manager.ui.features.starter.ShizukuStarter.isShizukuAvailable()
            hasPermission =
                roro.stellar.manager.ui.features.starter.ShizukuStarter.checkPermission()
        }

        val binderDeadListener = Shizuku.OnBinderDeadListener {
            isShizukuAvailable = false
            hasPermission = false
        }

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (e: Exception) {
        }

        onDispose {
            try {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
            } catch (e: Exception) {
            }
        }
    }

    val title = if (isRestart) "Shizuku 重启" else "Shizuku 启动"
    val subtitle = when {
        !isShizukuAvailable -> "Shizuku 服务未运行"
        !hasPermission -> "需要授予 Shizuku 权限"
        else -> "通过 Shizuku 服务启动 Stellar"
    }

    val buttonText = when {
        !isShizukuAvailable -> "查看"
        !hasPermission -> "授权"
        isRestart -> "重启"
        else -> "启动"
    }

    ModernActionCard(
        icon = Icons.Default.Star,
        title = title,
        subtitle = subtitle,
        buttonText = buttonText,
        onButtonClick = {
            // 立即刷新状态
            isShizukuAvailable =
                roro.stellar.manager.ui.features.starter.ShizukuStarter.isShizukuAvailable()
            hasPermission =
                roro.stellar.manager.ui.features.starter.ShizukuStarter.checkPermission()

            // 如果Shizuku未运行，提示用户
            if (!isShizukuAvailable) {
                Toast.makeText(context, "请先安装并启动 Shizuku 应用", Toast.LENGTH_LONG).show()
                return@ModernActionCard
            }

            // 如果没有权限，请求权限
            if (!hasPermission) {
                Toast.makeText(context, "正在请求 Shizuku 权限...", Toast.LENGTH_SHORT).show()
                roro.stellar.manager.ui.features.starter.ShizukuStarter.requestPermission()
                return@ModernActionCard
            }

            // 有权限后启动
            val intent = Intent(context, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_IS_ROOT, false)
                putExtra(StarterActivity.EXTRA_IS_SHIZUKU, true)
            }
            context.startActivity(intent)
        }
    )
}