package roro.stellar.manager.ui.features.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.launch
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.KEEP_START_ON_BOOT
import roro.stellar.manager.StellarSettings.KEEP_START_ON_BOOT_WIRELESS
import roro.stellar.manager.StellarSettings.TCPIP_PORT
import roro.stellar.manager.StellarSettings.TCPIP_PORT_ENABLED
import roro.stellar.manager.StellarSettings.DROP_PRIVILEGES
import roro.stellar.manager.ktx.isComponentEnabled
import roro.stellar.manager.ktx.setComponentEnabled
import roro.stellar.manager.receiver.BootCompleteReceiver
import roro.stellar.manager.ui.components.IconContainer
import roro.stellar.manager.ui.components.StellarSegmentedSelector
import roro.stellar.manager.ui.components.SettingsContentCard
import roro.stellar.manager.ui.components.SettingsSwitchCard
import roro.stellar.manager.ui.components.SettingsClickableCard
import roro.stellar.manager.ui.features.settings.update.UpdateUtils
import roro.stellar.manager.ui.features.settings.update.isNewerThan
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.ui.theme.ThemeMode
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.util.AutoStartUtils
import roro.stellar.manager.util.PortBlacklistUtils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import roro.stellar.Stellar

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

    var hasRootPermission by remember { mutableStateOf<Boolean?>(null) }

    var startOnBoot by remember { mutableStateOf(false) }

    var startOnBootWireless by remember {
        mutableStateOf(
            context.packageManager.isComponentEnabled(componentName) &&
            preferences.getBoolean(KEEP_START_ON_BOOT_WIRELESS, false)
        )
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val isRoot = withContext(Dispatchers.IO) {
            try {
                Shell.getShell().isRoot
            } catch (_: Exception) {
                false
            }
        }
        hasRootPermission = isRoot
        if (isRoot) {
            startOnBoot = context.packageManager.isComponentEnabled(componentName) &&
                    !preferences.getBoolean(KEEP_START_ON_BOOT_WIRELESS, false)
        }
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

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    var shizukuCompatEnabled by remember { mutableStateOf(true) }
    var isServiceConnected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isServiceConnected = Stellar.pingBinder()
        if (isServiceConnected) {
            try {
                shizukuCompatEnabled = withContext(Dispatchers.IO) {
                    Stellar.isShizukuCompatEnabled()
                }
            } catch (_: Exception) {
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
            SettingsContentCard(
                icon = Icons.Default.DarkMode,
                title = "主题",
                subtitle = "选择应用的外观主题"
            ) {
                ThemeSelectorWithAnimation(
                    currentMode = currentThemeMode,
                    onModeChange = { mode ->
                        currentThemeMode = mode
                        ThemePreferences.setThemeMode(mode)
                    }
                )
            }

            SettingsSwitchCard(
                icon = Icons.Default.Wifi,
                title = "开机启动（无线调试）",
                subtitle = "Stellar 可以通过无线调试开机启动",
                checked = startOnBootWireless,
                onCheckedChange = { newValue ->
                    if (newValue) {
                        Toast.makeText(context, "请在自启动管理中授权本应用", Toast.LENGTH_SHORT).show()
                        AutoStartUtils.openAutoStartSettings(context)
                        startOnBoot = false
                        savePreference(KEEP_START_ON_BOOT, false)
                    }
                    startOnBootWireless = newValue
                    toggleBootComponent(
                        context,
                        componentName,
                        KEEP_START_ON_BOOT_WIRELESS,
                        newValue || startOnBoot
                    )
                }
            )

            SettingsSwitchCard(
                icon = Icons.Default.PowerSettingsNew,
                title = "开机启动（Root）",
                subtitle = "Stellar 可以通过Root权限开机启动",
                checked = startOnBoot,
                enabled = hasRootPermission == true,
                onCheckedChange = { newValue ->
                    if (newValue) {
                        Toast.makeText(context, "请在自启动管理中授权本应用", Toast.LENGTH_SHORT).show()
                        AutoStartUtils.openAutoStartSettings(context)
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

            SettingsSwitchCard(
                icon = Icons.Default.Share,
                title = "Shizuku 兼容层",
                subtitle = "允许 Shizuku 应用通过 Stellar 服务运行",
                checked = shizukuCompatEnabled,
                enabled = isServiceConnected,
                onCheckedChange = { newValue ->
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Stellar.setShizukuCompatEnabled(newValue)
                            }
                            shizukuCompatEnabled = newValue
                            Toast.makeText(
                                context,
                                if (newValue) "Shizuku 兼容层已启用" else "Shizuku 兼容层已禁用",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            SettingsSwitchCard(
                icon = Icons.Default.Security,
                title = "降权激活",
                subtitle = "Root 启动后降权到 Shell 用户运行",
                checked = dropPrivileges,
                enabled = hasRootPermission == true,
                onCheckedChange = { newValue ->
                    dropPrivileges = newValue
                    savePreference(DROP_PRIVILEGES, newValue)
                }
            )

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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconContainer(
                            icon = Icons.Default.SettingsEthernet
                        )

                        Spacer(modifier = Modifier.width(12.dp))

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

                    AnimatedVisibility(visible = tcpipPortEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
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

            SettingsClickableCard(
                icon = Icons.Default.Subject,
                title = "服务日志",
                subtitle = "查看 Stellar 服务运行日志",
                onClick = onNavigateToLogs
            )

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
                                     } catch (_: Exception) {
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
                             } catch (_: Exception) {
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
    StellarSegmentedSelector(
        items = ThemeMode.entries.toList(),
        selectedItem = currentMode,
        onItemSelected = onModeChange,
        itemLabel = { ThemePreferences.getThemeModeDisplayName(it) }
    )
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
