package roro.stellar.manager.ui.features.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.R
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
import roro.stellar.manager.ui.components.LocalScreenConfig
import roro.stellar.manager.ui.components.StellarSegmentedSelector
import roro.stellar.manager.ui.components.SettingsContentCard
import roro.stellar.manager.ui.components.SettingsSwitchCard
import roro.stellar.manager.ui.components.SettingsClickableCard
import roro.stellar.manager.util.update.AppUpdate
import roro.stellar.manager.util.update.UpdateUtils
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.ui.theme.ThemeMode
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.util.PortBlacklistUtils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import roro.stellar.Stellar

private const val TAG = "SettingsScreen"

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    topAppBarState: TopAppBarState,
    onNavigateToLogs: () -> Unit = {}
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val componentName = ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
    val screenConfig = LocalScreenConfig.current
    val isLandscape = screenConfig.isLandscape
    val gridColumns = screenConfig.gridColumns

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
    var pendingUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var shizukuCompatEnabled by remember { mutableStateOf(true) }
    var isServiceConnected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isServiceConnected = Stellar.pingBinder()
        if (isServiceConnected) {
            try {
                @SuppressLint("RestrictedApi")
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding,
                bottom = AppSpacing.screenBottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing)
        ) {
            item(span = { GridItemSpan(gridColumns) }) {
                SettingsContentCard(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.theme),
                    subtitle = stringResource(R.string.theme_subtitle)
                ) {
                    ThemeSelectorWithAnimation(
                        currentMode = currentThemeMode,
                        onModeChange = { mode ->
                            currentThemeMode = mode
                            ThemePreferences.setThemeMode(mode)
                        }
                    )
                }
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.boot_start_wireless),
                    subtitle = stringResource(R.string.boot_start_wireless_subtitle),
                    checked = startOnBootWireless,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            Toast.makeText(context, context.getString(R.string.auto_enable_accessibility), Toast.LENGTH_SHORT).show()
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
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.boot_start_root),
                    subtitle = stringResource(R.string.boot_start_root_subtitle),
                    checked = startOnBoot,
                    enabled = hasRootPermission == true,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            Toast.makeText(context, context.getString(R.string.auto_enable_accessibility), Toast.LENGTH_SHORT).show()
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

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.shizuku_compat_layer),
                    subtitle = stringResource(R.string.shizuku_compat_layer_subtitle),
                    checked = shizukuCompatEnabled,
                    enabled = isServiceConnected,
                    onCheckedChange = { newValue ->
                        scope.launch {
                            try {
                                @SuppressLint("RestrictedApi")
                                withContext(Dispatchers.IO) {
                                    Stellar.setShizukuCompatEnabled(newValue)
                                }
                                shizukuCompatEnabled = newValue
                                Toast.makeText(
                                    context,
                                    if (newValue) context.getString(R.string.shizuku_compat_enabled) else context.getString(R.string.shizuku_compat_disabled),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.setting_failed, e.message), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.drop_privileges),
                    subtitle = stringResource(R.string.drop_privileges_subtitle),
                    checked = dropPrivileges,
                    enabled = hasRootPermission == true,
                    onCheckedChange = { newValue ->
                        dropPrivileges = newValue
                        savePreference(DROP_PRIVILEGES, newValue)
                    }
                )
            }

            item(span = { GridItemSpan(gridColumns) }) {
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
                                    text = stringResource(R.string.tcpip_port),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.tcpip_port_subtitle),
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
                                            Toast.makeText(context, context.getString(R.string.cannot_generate_safe_port), Toast.LENGTH_SHORT).show()
                                            tcpipPortEnabled = false
                                        } else {
                                            tcpipPort = randomPort.toString()
                                            preferences.edit {
                                                putBoolean(TCPIP_PORT_ENABLED, enabled)
                                                putString(TCPIP_PORT, tcpipPort)
                                            }
                                            Toast.makeText(context, context.getString(R.string.auto_generated_safe_port, tcpipPort), Toast.LENGTH_SHORT).show()
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
                                    label = { Text(stringResource(R.string.port_number)) },
                                    placeholder = { Text(stringResource(R.string.port_example)) },
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
                                                Toast.makeText(context, context.getString(R.string.cannot_generate_safe_port_manual), Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            tcpipPort = randomPort.toString()
                                        }

                                        val port = tcpipPort.toIntOrNull()
                                        if (port == null || port !in 1..65535) {
                                            Toast.makeText(context, context.getString(R.string.port_invalid), Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        if (PortBlacklistUtils.isPortBlacklisted(port)) {
                                            Toast.makeText(context, context.getString(R.string.port_blacklisted_warning, port), Toast.LENGTH_LONG).show()
                                        }

                                        preferences.edit {
                                            putString(TCPIP_PORT, tcpipPort)
                                        }
                                        Toast.makeText(context, context.getString(R.string.port_set_to, tcpipPort), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .height(56.dp),
                                    shape = AppShape.shapes.buttonMedium
                                ) {
                                    Text(stringResource(R.string.confirm))
                                }
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(gridColumns) }) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing)
                    ) {
                        SettingsClickableCard(
                            icon = Icons.AutoMirrored.Filled.Subject,
                            title = stringResource(R.string.service_logs),
                            subtitle = stringResource(R.string.service_logs_subtitle),
                            onClick = onNavigateToLogs,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        UpdateCard(
                            isCheckingUpdate = isCheckingUpdate,
                            onCheckUpdate = {
                                isCheckingUpdate = true
                                scope.launch {
                                    try {
                                        val update = UpdateUtils.checkUpdate()
                                        if (update != null && update.versionCode > BuildConfig.VERSION_CODE) {
                                            pendingUpdate = update
                                            showUpdateDialog = true
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.already_latest_version), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, context.getString(R.string.check_update_failed), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                } else {
                    SettingsClickableCard(
                        icon = Icons.AutoMirrored.Filled.Subject,
                        title = stringResource(R.string.service_logs),
                        subtitle = stringResource(R.string.service_logs_subtitle),
                        onClick = onNavigateToLogs
                    )
                }
            }

            if (!isLandscape) {
                item {
                    UpdateCard(
                        isCheckingUpdate = isCheckingUpdate,
                        onCheckUpdate = {
                            isCheckingUpdate = true
                            scope.launch {
                                try {
                                    val update = UpdateUtils.checkUpdate()
                                    if (update != null && update.versionCode > BuildConfig.VERSION_CODE) {
                                        pendingUpdate = update
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.already_latest_version), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, context.getString(R.string.check_update_failed), Toast.LENGTH_SHORT).show()
                                } finally {
                                    isCheckingUpdate = false
                                }
                            }
                        }
                    )
                }
            }

            item(span = { GridItemSpan(gridColumns) }) {
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
                                 text = stringResource(R.string.project_declaration),
                                 style = MaterialTheme.typography.titleMedium,
                                 fontWeight = FontWeight.Bold
                             )
                         }
                     }
                     
                     Spacer(modifier = Modifier.height(12.dp))
                     
                     Text(
                         text = stringResource(R.string.project_declaration_content),
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     
                     Spacer(modifier = Modifier.height(12.dp))
                     
                     Button(
                         onClick = {
                             val intent = Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku".toUri())
                             try {
                                 context.startActivity(intent)
                             } catch (_: Exception) {
                                 Toast.makeText(context, context.getString(R.string.cannot_open_browser), Toast.LENGTH_SHORT).show()
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
                         Text(stringResource(R.string.visit_shizuku_project), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            }
        }
    }

    if (showUpdateDialog && pendingUpdate != null) {
        NewVersionDialog(
            update = pendingUpdate!!,
            onDismiss = { showUpdateDialog = false },
            onDownload = {
                showUpdateDialog = false
                val url = pendingUpdate!!.downloadUrl
                if (url.isNotEmpty()) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_browser), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.no_download_available), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun ThemeSelectorWithAnimation(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    val labels = ThemeMode.entries.associateWith { stringResource(ThemePreferences.getThemeModeDisplayNameRes(it)) }
    StellarSegmentedSelector(
        items = ThemeMode.entries.toList(),
        selectedItem = currentMode,
        onItemSelected = onModeChange,
        itemLabel = { labels[it] ?: "" }
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

@Composable
private fun UpdateCard(
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        text = stringResource(R.string.check_update),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCheckUpdate,
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
                    text = if (isCheckingUpdate) stringResource(R.string.checking) else stringResource(R.string.check_update),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewVersionDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AppShape.shapes.dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.dialogPadding)
            ) {
                Text(
                    text = stringResource(R.string.new_version_title, update.versionName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(AppSpacing.sectionSpacing))

                if (update.body.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(
                            markdown = update.body,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.dialogPadding))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(AppSpacing.dialogButtonSpacing))
                    Button(
                        onClick = onDownload,
                        enabled = update.downloadUrl.isNotEmpty(),
                        shape = AppShape.shapes.buttonMedium
                    ) {
                        Text(stringResource(R.string.go_to_download))
                    }
                }
            }
        }
    }
}
