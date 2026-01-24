package com.stellar.demo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stellar.demo.ui.theme.AppShape

@Composable
fun HomeScreen(
    serviceStatus: ServiceStatus,
    serviceInfo: ServiceInfo?,
    userServiceConnected: Boolean,
    onRefresh: () -> Unit,
    onStatusAction: () -> Unit,
    onRequestFollowPermission: () -> Unit,
    onRequestBootPermission: () -> Unit,
    hasFollowPermission: Boolean,
    hasBootPermission: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(
            serviceStatus = serviceStatus,
            serviceInfo = serviceInfo,
            onRefresh = onRefresh,
            onStatusAction = onStatusAction
        )

        if (serviceStatus == ServiceStatus.READY) {
            UserServiceCard(userServiceConnected)

            if (!hasFollowPermission) {
                PermissionCard(
                    title = "跟随 Stellar 启动",
                    description = "允许应用在 Stellar 启动时自动启动",
                    onClick = onRequestFollowPermission
                )
            }

            if (!hasBootPermission) {
                PermissionCard(
                    title = "开机跟随 Stellar 启动",
                    description = "允许应用在开机时跟随 Stellar 自动启动",
                    onClick = onRequestBootPermission
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    serviceStatus: ServiceStatus,
    serviceInfo: ServiceInfo?,
    onRefresh: () -> Unit,
    onStatusAction: () -> Unit
) {
    val backgroundColor = when (serviceStatus) {
        ServiceStatus.READY -> MaterialTheme.colorScheme.primaryContainer
        ServiceStatus.CHECKING -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when (serviceStatus) {
        ServiceStatus.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        ServiceStatus.CHECKING -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(AppShape.shapes.iconSmall)
                            .background(color = contentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = serviceStatus.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Stellar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            text = serviceStatus.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = contentColor
                    )
                }
            }

            serviceInfo?.let { info ->
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                InfoRow("UID", info.uid.toString(), contentColor)
                InfoRow("API 版本", info.version.toString(), contentColor)
                InfoRow("运行模式", when {
                    info.isRoot -> "Root"
                    info.isAdb -> "ADB"
                    else -> "其他"
                }, contentColor)
                info.seLinuxContext?.let {
                    InfoRow("SELinux", it, contentColor)
                }
            }

            if (serviceStatus != ServiceStatus.READY && serviceStatus != ServiceStatus.CHECKING) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStatusAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.buttonMedium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = contentColor,
                        contentColor = backgroundColor
                    )
                ) {
                    Icon(
                        imageVector = when (serviceStatus) {
                            ServiceStatus.NOT_INSTALLED -> Icons.Default.Download
                            ServiceStatus.NOT_RUNNING -> Icons.Default.PlayArrow
                            ServiceStatus.NO_PERMISSION -> Icons.Default.VpnKey
                            else -> Icons.Default.Settings
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (serviceStatus) {
                            ServiceStatus.NOT_INSTALLED -> "请安装管理器"
                            ServiceStatus.NOT_RUNNING -> "打开 Stellar"
                            ServiceStatus.NO_PERMISSION -> "请求权限"
                            else -> "检查状态"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, contentColor: Color) {
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
private fun UserServiceCard(connected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShape.shapes.iconSmall)
                    .background(
                        color = if (connected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (connected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UserService",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (connected) "已连接" else "未连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = AppShape.shapes.iconSmall,
                color = if (connected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = if (connected) "ON" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (connected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShape.shapes.iconSmall)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

enum class ServiceStatus(
    val title: String,
    val icon: ImageVector
) {
    CHECKING("检查中...", Icons.Default.Refresh),
    READY("已就绪", Icons.Default.CheckCircle),
    NOT_INSTALLED("未安装", Icons.Default.Warning),
    NOT_RUNNING("未运行", Icons.Default.Error),
    NO_PERMISSION("需要权限", Icons.Default.Lock)
}

data class ServiceInfo(
    val uid: Int,
    val version: Int,
    val isRoot: Boolean,
    val isAdb: Boolean,
    val seLinuxContext: String?
)
