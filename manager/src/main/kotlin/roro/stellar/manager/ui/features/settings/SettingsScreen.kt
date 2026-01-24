package roro.stellar.manager.ui.features.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.KEEP_START_ON_BOOT
import roro.stellar.manager.StellarSettings.KEEP_START_ON_BOOT_WIRELESS
import roro.stellar.manager.StellarSettings.TCPIP_PORT
import roro.stellar.manager.StellarSettings.TCPIP_PORT_ENABLED
import roro.stellar.manager.StellarSettings.DROP_PRIVILEGES
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.ktx.isComponentEnabled
import roro.stellar.manager.ktx.setComponentEnabled
import roro.stellar.manager.receiver.BootCompleteReceiver
import roro.stellar.manager.ui.features.settings.update.UpdateUtils
import roro.stellar.manager.ui.features.settings.update.isNewerThan
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.ui.theme.ThemeMode
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.util.PortBlacklistUtils
import roro.stellar.manager.util.StellarSystemApis
import kotlin.math.roundToInt

private const val TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    topAppBarState: TopAppBarState,
    onNavigateToLogs: () -> Unit = {}
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val componentName = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
    
    val preferences = StellarSettings.getPreferences()
    
    var startOnBoot by remember { 
        mutableStateOf(
            context.packageManager.isComponentEnabled(componentName) && 
            !preferences.getBoolean(KEEP_START_ON_BOOT_WIRELESS, false)
        )
    }
    
    val hasSecurePermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED
    
    var startOnBootWireless by remember { 
        mutableStateOf(
            context.packageManager.isComponentEnabled(componentName) && 
            preferences.getBoolean(KEEP_START_ON_BOOT_WIRELESS, false) &&
            hasSecurePermission
        )
    }
    
    var tcpipPort by remember { 
        mutableStateOf(preferences.getString(TCPIP_PORT, "") ?: "")
    }
    
    var tcpipPortEnabled by remember {
        mutableStateOf(preferences.getBoolean(TCPIP_PORT_ENABLED, true))
    }

    var dropPrivileges by remember {
        mutableStateOf(preferences.getBoolean(DROP_PRIVILEGES, false))
    }
    
    var currentThemeMode by remember { mutableStateOf(ThemePreferences.themeMode.value) }

    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                    start = AppSpacing.screenHorizontalPadding,
                    end = AppSpacing.screenHorizontalPadding,
                    bottom = AppSpacing.screenBottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = AppShape.shapes.cardMedium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "主题",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "选择应用的外观主题",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ThemeSelectorWithAnimation(
                        currentMode = currentThemeMode,
                        onModeChange = { mode ->
                            currentThemeMode = mode
                            ThemePreferences.setThemeMode(mode)
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = AppShape.shapes.cardMedium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = "开机启动（Root）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已 root 设备，Stellar 可以开机启动",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = startOnBoot,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                startOnBootWireless = false
                                savePreference(KEEP_START_ON_BOOT_WIRELESS, false)
                            }
                            startOnBoot = newValue
                            toggleBootComponent(
                                context,
                                componentName,
                                KEEP_START_ON_BOOT,
                                newValue || startOnBootWireless
                            )
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = AppShape.shapes.cardMedium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "降权激活",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Root 启动后降权到 shell 用户运行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = dropPrivileges,
                        onCheckedChange = { newValue ->
                            dropPrivileges = newValue
                            savePreference(DROP_PRIVILEGES, newValue)
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = AppShape.shapes.cardMedium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = "开机启动（无线调试）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Stellar 可以通过无线调试开机启动",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = startOnBootWireless,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                if (!hasSecurePermission) {
                                    showSecureSettingsPermissionDialog(context) { granted ->
                                        if (granted) {
                                            startOnBoot = false
                                            savePreference(KEEP_START_ON_BOOT, false)
                                            startOnBootWireless = true
                                            toggleBootComponent(
                                                context,
                                                componentName,
                                                KEEP_START_ON_BOOT_WIRELESS,
                                                true
                                            )
                                        }
                                    }
                                } else {
                                    startOnBoot = false
                                    savePreference(KEEP_START_ON_BOOT, false)
                                    startOnBootWireless = newValue
                                    toggleBootComponent(
                                        context,
                                        componentName,
                                        KEEP_START_ON_BOOT_WIRELESS,
                                        newValue
                                    )
                                }
                            } else {
                                startOnBootWireless = false
                                toggleBootComponent(
                                    context,
                                    componentName,
                                    KEEP_START_ON_BOOT_WIRELESS,
                                    false
                                )
                            }
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = AppShape.shapes.cardMedium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TCP/IP 端口",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "通过无线调试启动后，将 adbd 的 TCP/IP 端口切换到指定端口",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Switch(
                            checked = tcpipPortEnabled,
                            onCheckedChange = { enabled ->
                                tcpipPortEnabled = enabled

                                if (enabled && tcpipPort.isEmpty()) {
                                    val randomPort = PortBlacklistUtils.generateSafeRandomPort(1000, 9999, 100)
                                    if (randomPort == -1) {
                                        Toast.makeText(context, "无法生成安全端口，请手动设置", Toast.LENGTH_SHORT).show()
                                        tcpipPortEnabled = false
                                    } else {
                                        tcpipPort = randomPort.toString()
                                        preferences.edit {
                                            putBoolean(TCPIP_PORT_ENABLED, enabled)
                                            putString(TCPIP_PORT, tcpipPort)
                                        }
                                        Toast.makeText(context, "已自动生成安全端口 $tcpipPort", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    preferences.edit {
                                        putBoolean(TCPIP_PORT_ENABLED, enabled)
                                    }
                                }
                            }
                        )
                    }
                    
                    if (tcpipPortEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = tcpipPort,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                        tcpipPort = newValue
                                    }
                                },
                                label = { Text("端口号") },
                                placeholder = { Text("例如: 5555") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = AppShape.shapes.inputField
                            )
                            
                            Button(
                                onClick = {
                                    if (tcpipPort.isEmpty()) {
                                        val randomPort = PortBlacklistUtils.generateSafeRandomPort(1000, 9999, 100)
                                        if (randomPort == -1) {
                                            Toast.makeText(context, "无法生成安全端口，请手动输入", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        tcpipPort = randomPort.toString()
                                    }

                                    val port = tcpipPort.toIntOrNull()
                                    if (port == null || port !in 1..65535) {
                                        Toast.makeText(context, "端口号无效，请输入 1-65535 之间的数字", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    if (PortBlacklistUtils.isPortBlacklisted(port)) {
                                        Toast.makeText(context, "警告：端口 $port 可能被恶意扫描，建议使用其他端口", Toast.LENGTH_LONG).show()
                                    }

                                    preferences.edit {
                                        putString(TCPIP_PORT, tcpipPort)
                                    }
                                    Toast.makeText(context, "端口已设置为 $tcpipPort", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .height(56.dp),
                                shape = AppShape.shapes.buttonMedium
                            ) {
                                Text("确定")
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLogs() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = AppShape.shapes.cardMedium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subject,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "服务日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "查看 Stellar 服务运行日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

             Card(
                 modifier = Modifier.fillMaxWidth(),
                 colors = CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surfaceContainer
                 ),
                 shape = AppShape.shapes.cardMedium
             ) {
                 Column(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(16.dp)
                 ) {
                     Row(
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.spacedBy(12.dp)
                     ) {
                         Box(
                             modifier = Modifier
                                 .size(40.dp)
                                 .background(
                                     color = MaterialTheme.colorScheme.primaryContainer,
                                     shape = CircleShape
                                 ),
                             contentAlignment = Alignment.Center
                         ) {
                             Icon(
                                 imageVector = Icons.Default.SystemUpdate,
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(22.dp)
                             )
                         }
                         
                         Column(modifier = Modifier.weight(1f)) {
                             Text(
                                 text = "检查更新",
                                 style = MaterialTheme.typography.titleMedium,
                                 fontWeight = FontWeight.Bold
                             )
                             Spacer(modifier = Modifier.height(4.dp))
                             Text(
                                 text = "当前版本: ${BuildConfig.VERSION_NAME}",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                         }
                     }
                     
                     Spacer(modifier = Modifier.height(12.dp))
                     
                     if (isDownloading) {
                         LinearProgressIndicator(
                             progress = { downloadProgress / 100f },
                             modifier = Modifier.fillMaxWidth()
                         )
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = "下载中... $downloadProgress%",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.primary
                         )
                     } else if (updateAvailable) {
                         Button(
                             onClick = {
                                 if (!UpdateUtils.hasInstallPermission(context)) {
                                     UpdateUtils.requestInstallPermission(context)
                                     return@Button
                                 }

                                 isDownloading = true
                                 scope.launch {
                                     val update = UpdateUtils.checkUpdate()
                                     if (update != null) {
                                         UpdateUtils.downloadAndInstall(
                                             context,
                                             update.url
                                         ) { progress ->
                                             downloadProgress = progress
                                         }
                                     }
                                     isDownloading = false
                                 }
                             },
                             modifier = Modifier.fillMaxWidth(),
                             shape = AppShape.shapes.buttonMedium
                         ) {
                             Icon(
                                 imageVector = Icons.Default.CloudDownload,
                                 contentDescription = null,
                                 modifier = Modifier.size(18.dp)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text("下载更新", modifier = Modifier.padding(vertical = 4.dp))
                         }
                     } else {
                         Button(
                             onClick = {
                                 isCheckingUpdate = true
                                 scope.launch {
                                     try {
                                         val update = UpdateUtils.checkUpdate()
                                         if (update != null && update.isNewerThan(BuildConfig.VERSION_CODE)) {
                                             updateAvailable = true
                                             Toast.makeText(context, "发现新版本！", Toast.LENGTH_SHORT).show()
                                         } else {
                                             Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                         }
                                     } catch (e: Exception) {
                                         Toast.makeText(context, "检查更新失败", Toast.LENGTH_SHORT).show()
                                     } finally {
                                         isCheckingUpdate = false
                                     }
                                 }
                             },
                             modifier = Modifier.fillMaxWidth(),
                             enabled = !isCheckingUpdate,
                             shape = AppShape.shapes.buttonMedium
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Refresh,
                                 contentDescription = null,
                                 modifier = Modifier.size(18.dp)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text(
                                 text = if (isCheckingUpdate) "检查中..." else "检查更新",
                                 modifier = Modifier.padding(vertical = 4.dp)
                             )
                         }
                     }
                 }
             }

             Card(
                 modifier = Modifier.fillMaxWidth(),
                 colors = CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surfaceContainer
                 ),
                 shape = AppShape.shapes.cardMedium
             ) {
                 Column(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(16.dp)
                 ) {
                     Row(
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.spacedBy(12.dp)
                     ) {
                         Box(
                             modifier = Modifier
                                 .size(40.dp)
                                 .background(
                                     color = MaterialTheme.colorScheme.primaryContainer,
                                     shape = CircleShape
                                 ),
                             contentAlignment = Alignment.Center
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Info,
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(22.dp)
                             )
                         }
                         
                         Column(modifier = Modifier.weight(1f)) {
                             Text(
                                 text = "项目声明",
                                 style = MaterialTheme.typography.titleMedium,
                                 fontWeight = FontWeight.Bold
                             )
                         }
                     }
                     
                     Spacer(modifier = Modifier.height(12.dp))
                     
                     Text(
                         text = "本项目基于 Shizuku 开发。Shizuku 是一个优秀的开源项目，提供了通过 ADB 或 Root 调用系统 API 的能力。",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     
                     Spacer(modifier = Modifier.height(12.dp))
                     
                     Button(
                         onClick = {
                             val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku"))
                             try {
                                 context.startActivity(intent)
                             } catch (e: Exception) {
                                 Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                             }
                         },
                         modifier = Modifier.fillMaxWidth(),
                         shape = AppShape.shapes.buttonMedium
                     ) {
                         Icon(
                             imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                             contentDescription = null,
                             modifier = Modifier.size(18.dp)
                         )
                         Spacer(modifier = Modifier.width(8.dp))
                         Text("访问 Shizuku 项目", modifier = Modifier.padding(vertical = 4.dp))
                     }
                 }
             }
         }
     }
 }

@Composable
private fun ThemeSelectorWithAnimation(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    val density = LocalDensity.current
    val themeCount = ThemeMode.entries.size
    val currentIndex = ThemeMode.entries.indexOf(currentMode)

    var innerWidth by remember { mutableStateOf(0) }

    val spacing = 4.dp
    val spacingPx = with(density) { spacing.toPx() }

    val animatedIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "theme_index"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = AppShape.shapes.cardMedium
            )
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    innerWidth = size.width
                },
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            ThemeMode.entries.forEach { _ ->
                Spacer(modifier = Modifier.weight(1f).height(40.dp))
            }
        }
        
        if (innerWidth > 0) {
            val itemWidth = (innerWidth - spacingPx * (themeCount - 1)) / themeCount
            val offsetX = animatedIndex * (itemWidth + spacingPx)
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .width(with(density) { itemWidth.toDp() })
                    .height(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = AppShape.shapes.iconSmall
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            onClick = { onModeChange(mode) },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ThemePreferences.getThemeModeDisplayName(mode),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

private fun savePreference(key: String, value: Boolean) {
    StellarSettings.getPreferences().edit { putBoolean(key, value) }
}

private fun toggleBootComponent(
    context: Context,
    componentName: ComponentName,
    key: String,
    enabled: Boolean
): Boolean {
    savePreference(key, enabled)

    try {
        context.packageManager.setComponentEnabled(componentName, enabled)

        val isEnabled = context.packageManager.isComponentEnabled(componentName) == enabled
        if (!isEnabled) {
            Log.e(TAG, "设置组件状态失败: $componentName 到 $enabled")
            return false
        }

    } catch (e: Exception) {
        Log.e(TAG, "启用启动组件失败", e)
        return false
    }

    return true
}

private fun showSecureSettingsPermissionDialog(context: Context, onResult: (Boolean) -> Unit) {
    val command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
    
    val dialog = MaterialAlertDialogBuilder(context)
        .setMessage("注意\n\n此功能需要 WRITE_SECURE_SETTINGS 权限。\n\n警告\n\nWRITE_SECURE_SETTINGS 是高度敏感的权限，仅在明确操作风险时启用。后续可能产生的任何后果均由用户自行承担。")
    
    val click: (android.content.DialogInterface, Int) -> Unit = { _, _ ->
        MaterialAlertDialogBuilder(context)
            .setTitle("查看指令")
            .setMessage(command)
            .setPositiveButton("复制") { _, _ ->
                if (ClipboardUtils.put(context, command)) {
                    Toast.makeText(
                        context,
                        "$command\n已被复制到剪贴板。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    if (Stellar.pingBinder()) {
        dialog.setNeutralButton("取消") { _, _ -> 
            onResult(false)
        }
        .setNegativeButton("手动", click)
        .setPositiveButton("自动") { _, _ ->
            Thread {
                try {
                    StellarSystemApis.grantRuntimePermission(
                        BuildConfig.APPLICATION_ID,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        0
                    )

                    Thread.sleep(500)

                    val hasPermission = try {
                        context.packageManager.checkPermission(
                            Manifest.permission.WRITE_SECURE_SETTINGS,
                            BuildConfig.APPLICATION_ID
                        ) == PackageManager.PERMISSION_GRANTED
                    } catch (e: Exception) {
                        false
                    }

                    (context as? android.app.Activity)?.runOnUiThread {
                        if (hasPermission) {
                            Toast.makeText(
                                context,
                                "授权成功",
                                Toast.LENGTH_SHORT
                            ).show()
                            onResult(true)
                        } else {
                            Toast.makeText(
                                context,
                                "授权失败：权限未生效\n\n请使用手动方式授权",
                                Toast.LENGTH_LONG
                            ).show()
                            onResult(false)
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message?.let { msg ->
                        when {
                            msg.contains("GRANT_RUNTIME_PERMISSIONS", ignoreCase = true) ->
                                "服务进程没有授权权限"
                            msg.contains("Unknown permission", ignoreCase = true) ->
                                "未知权限"
                            msg.contains("not found", ignoreCase = true) ->
                                "权限未找到"
                            msg.contains("Operation not permitted", ignoreCase = true) ->
                                "操作不被允许"
                            else -> msg
                        }
                    } ?: "未知错误"

                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(
                            context,
                            "自动授权失败：$errorMsg\n\n请使用手动方式授权",
                            Toast.LENGTH_LONG
                        ).show()
                        onResult(false)
                    }
                }
            }.start()
        }
    } else {
        dialog.setNegativeButton("取消") { _, _ -> 
            onResult(false)
        }
        .setPositiveButton("手动", click)
    }
    
    dialog.show()
}
