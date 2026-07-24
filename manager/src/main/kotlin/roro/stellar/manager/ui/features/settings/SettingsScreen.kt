package roro.stellar.manager.ui.features.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import roro.stellar.manager.compat.BuildUtils.atLeast30
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.topjohnwu.superuser.Shell
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.R
import roro.stellar.manager.StellarManagerProvider.Companion.KEY_DAEMON_ENABLED
import roro.stellar.manager.StellarManagerProvider.Companion.KEY_SHIZUKU_COMPAT
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.DROP_PRIVILEGES
import roro.stellar.manager.StellarSettings.SHIZUKU_COMPAT_ENABLED
import roro.stellar.manager.StellarSettings.TCPIP_PORT
import roro.stellar.manager.StellarSettings.TCPIP_PORT_ENABLED
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.db.AppDatabase
import roro.stellar.manager.db.ConfigEntity
import roro.stellar.manager.ktx.setComponentEnabled
import roro.stellar.manager.receiver.BootCompleteReceiver
import roro.stellar.manager.startup.boot.BootScriptManager
import roro.stellar.manager.ui.components.IconContainer
import roro.stellar.manager.ui.components.LocalScreenConfig
import roro.stellar.manager.ui.components.SettingsClickableCard
import roro.stellar.manager.ui.components.SettingsExpandableCard
import roro.stellar.manager.ui.components.SettingsInnerSwitchRow
import roro.stellar.manager.ui.components.SettingsSwitchCard
import roro.stellar.manager.ui.components.StellarSegmentedSelector
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.ui.theme.StartPage
import roro.stellar.manager.ui.theme.ThemeMode
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.util.EnvironmentUtils
import roro.stellar.manager.util.BackgroundVisibilityUtils
import roro.stellar.manager.util.PortBlacklistUtils
import roro.stellar.manager.util.UserHandleCompat
import roro.stellar.manager.util.update.ApkDownloader
import roro.stellar.manager.util.update.AppUpdate
import roro.stellar.manager.util.update.DownloadState
import roro.stellar.manager.util.update.UpdateSource
import roro.stellar.manager.util.update.UpdateUtils
import java.util.concurrent.TimeUnit

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
    var bootMode by remember { mutableStateOf(StellarSettings.getBootMode()) }
    var bootBroadcastAccessibilityEnabled by remember {
        mutableStateOf(preferences.getBoolean(StellarSettings.BOOT_BROADCAST_ACCESSIBILITY_ENABLED, false))
    }
    var scriptActionInProgress by remember { mutableStateOf(false) }
    var showScriptInstallDialog by remember { mutableStateOf(false) }
    var showScriptRemoveDialog by remember { mutableStateOf(false) }
    var pendingBootModeAfterScriptRemoval by remember { mutableStateOf<StellarSettings.BootMode?>(null) }
    var showBootGuideDialog by remember { mutableStateOf(false) }
    var showAccessibilityHintDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var currentSource by remember { mutableStateOf<UpdateSource?>(null) }
    var isServiceRunning by remember { mutableStateOf(Stellar.pingBinder()) }
    var bootAdbStartAvailable by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        isServiceRunning = withContext(Dispatchers.IO) { Stellar.pingBinder() }
        bootAdbStartAvailable = withContext(Dispatchers.IO) { isBootAdbStartAvailable() }
        if (bootAdbStartAvailable == false &&
            (bootMode == StellarSettings.BootMode.BROADCAST ||
                bootMode == StellarSettings.BootMode.TCPIP_PREWARM)
        ) {
            applyBootMode(
                context,
                componentName,
                StellarSettings.BootMode.NONE,
                bootMode,
                scope
            ) {
                bootMode = StellarSettings.BootMode.NONE
            }
        }
        val isRoot = withContext(Dispatchers.IO) {
            try {
                Shell.getShell().isRoot
            } catch (_: Exception) {
                false
            }
        }
        hasRootPermission = isRoot
        if (isRoot) {
            val scriptInstalled = withContext(Dispatchers.IO) { BootScriptManager.isScriptInstalled() }
            if (bootMode == StellarSettings.BootMode.SCRIPT && !scriptInstalled) {
                bootMode = StellarSettings.BootMode.NONE
                StellarSettings.setBootMode(StellarSettings.BootMode.NONE)
            }
        }
        currentSource = UpdateUtils.getPreferredSource()
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

    var daemonEnabled by remember {
        mutableStateOf(preferences.getBoolean(StellarSettings.DAEMON_ENABLED, false))
    }

    var hideBackground by remember {
        mutableStateOf(preferences.getBoolean(StellarSettings.HIDE_BACKGROUND, false))
    }

    var currentThemeMode by remember { mutableStateOf(ThemePreferences.themeMode.value) }
    var currentStartPage by remember { mutableStateOf(ThemePreferences.startPage.value) }

    var bootOptionsExpanded by remember { mutableStateOf(false) }
    var themeOptionsExpanded by remember { mutableStateOf(false) }

    fun selectBootMode(newMode: StellarSettings.BootMode) {
        if (newMode == bootMode) return

        if (newMode == StellarSettings.BootMode.SCRIPT) {
            showScriptInstallDialog = true
            return
        }

        if (bootMode == StellarSettings.BootMode.SCRIPT) {
            pendingBootModeAfterScriptRemoval = newMode
            showScriptRemoveDialog = true
            return
        }

        val previousMode = bootMode
        bootMode = newMode
        applyBootMode(context, componentName, newMode, previousMode, scope) {
            if (newMode == StellarSettings.BootMode.BROADCAST) {
                showBootGuideDialog = true
            }
        }
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val performCheckUpdate: (UpdateSource?) -> Unit = { source ->
        isCheckingUpdate = true
        scope.launch {
            try {
                val update = UpdateUtils.checkUpdate(source)
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

    var shizukuCompatEnabled by remember { mutableStateOf(preferences.getBoolean(SHIZUKU_COMPAT_ENABLED, true)) }

    LaunchedEffect(Unit) {
        try {
            @SuppressLint("RestrictedApi")
            val remote = withContext(Dispatchers.IO) { Stellar.isShizukuCompatEnabled() }
            shizukuCompatEnabled = remote
            savePreference(SHIZUKU_COMPAT_ENABLED, remote)
        } catch (_: Exception) {
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
                SettingsExpandableCard(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.personalization),
                    subtitle = stringResource(R.string.personalization_subtitle),
                    expanded = themeOptionsExpanded,
                    onExpandChange = { themeOptionsExpanded = it }
                ) {
                    Text(
                        text = stringResource(R.string.app_theme),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val themeLabels = ThemeMode.entries.associateWith { stringResource(ThemePreferences.getThemeModeDisplayNameRes(it)) }
                    StellarSegmentedSelector(
                        items = ThemeMode.entries.toList(),
                        selectedItem = currentThemeMode,
                        onItemSelected = { mode ->
                            currentThemeMode = mode
                            ThemePreferences.setThemeMode(mode)
                        },
                        itemLabel = { themeLabels[it] ?: "" }
                    )

                    Text(
                        text = stringResource(R.string.default_start_page),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val pageLabels = StartPage.entries.associateWith { stringResource(ThemePreferences.getStartPageDisplayNameRes(it)) }
                    StellarSegmentedSelector(
                        items = StartPage.entries.toList(),
                        selectedItem = currentStartPage,
                        onItemSelected = { page ->
                            currentStartPage = page
                            ThemePreferences.setStartPage(page)
                        },
                        itemLabel = { pageLabels[it] ?: "" }
                    )
                }
            }

            item(span = { GridItemSpan(gridColumns) }) {
                SettingsExpandableCard(
                    icon = Icons.Default.FlashOn,
                    title = stringResource(R.string.boot_startup_options),
                    subtitle = stringResource(R.string.boot_startup_options_subtitle),
                    expanded = bootOptionsExpanded,
                    onExpandChange = { bootOptionsExpanded = it }
                ) {
                    Column(
                        modifier = Modifier.animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing)
                    ) {
                        Text(
                            text = stringResource(R.string.boot_start_mode),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val bootModes = listOf(
                            StellarSettings.BootMode.NONE,
                            StellarSettings.BootMode.BROADCAST,
                            StellarSettings.BootMode.TCPIP_PREWARM,
                            StellarSettings.BootMode.SCRIPT
                        )
                        val bootModeLabels = mapOf(
                            StellarSettings.BootMode.NONE to stringResource(R.string.boot_start_mode_off_label),
                            StellarSettings.BootMode.BROADCAST to stringResource(R.string.boot_start_mode_broadcast_label),
                            StellarSettings.BootMode.TCPIP_PREWARM to stringResource(R.string.boot_start_mode_prewarm_label),
                            StellarSettings.BootMode.SCRIPT to stringResource(R.string.boot_start_mode_script_label)
                        )
                        val isBootModeEnabled: (StellarSettings.BootMode) -> Boolean = { mode ->
                            when (mode) {
                                StellarSettings.BootMode.NONE -> true
                                StellarSettings.BootMode.BROADCAST,
                                StellarSettings.BootMode.TCPIP_PREWARM -> bootAdbStartAvailable != false
                                StellarSettings.BootMode.SCRIPT -> hasRootPermission == true && !scriptActionInProgress
                            }
                        }

                        StellarSegmentedSelector(
                            items = bootModes,
                            selectedItem = bootMode,
                            onItemSelected = { mode -> selectBootMode(mode) },
                            itemLabel = { bootModeLabels[it] ?: "" },
                            itemEnabled = isBootModeEnabled
                        )

                        val bootModeDescription = when (bootMode) {
                            StellarSettings.BootMode.NONE -> stringResource(R.string.boot_start_none_subtitle)
                            StellarSettings.BootMode.BROADCAST -> stringResource(R.string.boot_start_callback_mode_subtitle)
                            StellarSettings.BootMode.TCPIP_PREWARM -> stringResource(R.string.boot_start_tcpip_prewarm_subtitle)
                            StellarSettings.BootMode.SCRIPT -> if (hasRootPermission == true) {
                                stringResource(R.string.boot_start_script_mode_subtitle)
                            } else {
                                stringResource(R.string.boot_start_script_mode_subtitle_no_root)
                            }
                        }
                        Text(
                            text = bootModeDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val unavailableMessage = when {
                            (bootMode == StellarSettings.BootMode.BROADCAST ||
                                bootMode == StellarSettings.BootMode.TCPIP_PREWARM) &&
                                bootAdbStartAvailable == false -> stringResource(R.string.boot_start_adb_unavailable)
                            bootMode == StellarSettings.BootMode.SCRIPT &&
                                hasRootPermission != true -> stringResource(R.string.boot_start_script_mode_subtitle_no_root)
                            else -> null
                        }
                        if (unavailableMessage != null) {
                            Text(
                                text = unavailableMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        AnimatedVisibility(
                            visible = bootMode == StellarSettings.BootMode.BROADCAST,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                        ) {
                            SettingsInnerSwitchRow(
                                title = stringResource(R.string.accessibility_auto_start),
                                subtitle = stringResource(R.string.accessibility_auto_start_subtitle),
                                checked = bootBroadcastAccessibilityEnabled,
                                enabled = bootAdbStartAvailable != false,
                                onCheckedChange = { newValue ->
                                    if (newValue &&
                                        !preferences.getBoolean(StellarSettings.ACCESSIBILITY_AUTO_START_PROMPTED, false)
                                    ) {
                                        showAccessibilityHintDialog = true
                                    } else {
                                        bootBroadcastAccessibilityEnabled = newValue
                                        savePreference(StellarSettings.BOOT_BROADCAST_ACCESSIBILITY_ENABLED, newValue)
                                        scope.launch(Dispatchers.IO) {
                                            AppDatabase.get(context).configDao().set(
                                                ConfigEntity("accessibilityAutoStart", newValue.toString())
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.shizuku_compat_layer),
                    subtitle = stringResource(R.string.shizuku_compat_layer_subtitle),
                    checked = shizukuCompatEnabled,
                    onCheckedChange = { newValue ->
                        shizukuCompatEnabled = newValue
                        savePreference(SHIZUKU_COMPAT_ENABLED, newValue)
                        scope.launch {
                            try {
                                val db = AppDatabase.get(context)
                                withContext(Dispatchers.IO) {
                                    db.configDao().set(ConfigEntity(KEY_SHIZUKU_COMPAT, newValue.toString()))
                                }
                            } catch (_: Exception) {}
                            try {
                                @SuppressLint("RestrictedApi")
                                withContext(Dispatchers.IO) {
                                    Stellar.setShizukuCompatEnabled(newValue)
                                }
                            } catch (_: Exception) {
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

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.Replay,
                    title = stringResource(R.string.daemon_enabled),
                    subtitle = stringResource(R.string.daemon_enabled_subtitle),
                    checked = daemonEnabled,
                    onCheckedChange = { newValue ->
                        daemonEnabled = newValue
                        savePreference(StellarSettings.DAEMON_ENABLED, newValue)
                        scope.launch {
                            try {
                                val db = AppDatabase.get(context)
                                withContext(Dispatchers.IO) {
                                    db.configDao().set(ConfigEntity(KEY_DAEMON_ENABLED, newValue.toString()))
                                }
                            } catch (_: Exception) {}
                            try {
                                @SuppressLint("RestrictedApi")
                                withContext(Dispatchers.IO) {
                                    Stellar.setDaemonEnabled(newValue)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                )
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.hide_background),
                    subtitle = stringResource(R.string.hide_background_subtitle),
                    checked = hideBackground,
                    onCheckedChange = { newValue ->
                        hideBackground = newValue
                        savePreference(StellarSettings.HIDE_BACKGROUND, newValue)
                        BackgroundVisibilityUtils.setHidden(context, newValue)
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
                                icon = Icons.Default.SettingsEthernet,
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        val ip = EnvironmentUtils.getWifiIpAddress()
                                        val port = tcpipPort.toIntOrNull()?.takeIf { tcpipPortEnabled && it in 1..65535 }
                                        when {
                                            ip == null -> Toast.makeText(context, context.getString(R.string.no_ip_available), Toast.LENGTH_SHORT).show()
                                            port == null -> Toast.makeText(context, context.getString(R.string.tcpip_port_not_configured), Toast.LENGTH_SHORT).show()
                                            else -> {
                                                val text = "adb connect $ip:$port"
                                                ClipboardUtils.put(context, text)
                                                Toast.makeText(context, context.getString(R.string.ip_port_copied, text), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
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
                            onCheckUpdate = { performCheckUpdate(null) },
                            onLongClick = { showSourceDialog = true },
                            currentSource = currentSource,
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
                        onCheckUpdate = { performCheckUpdate(null) },
                        onLongClick = { showSourceDialog = true },
                        currentSource = currentSource
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
                                     shape = AppShape.shapes.iconSmall
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
                     
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.spacedBy(8.dp)
                     ) {
                         Button(
                             onClick = {
                                 val intent = Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku".toUri())
                                 try {
                                     context.startActivity(intent)
                                 } catch (_: Exception) {
                                     Toast.makeText(context, context.getString(R.string.cannot_open_browser), Toast.LENGTH_SHORT).show()
                                 }
                             },
                             modifier = Modifier.weight(1f),
                             shape = AppShape.shapes.buttonMedium
                         ) {
                             Icon(
                                 painter = painterResource(R.drawable.ic_github),
                                 contentDescription = null,
                                 modifier = Modifier.size(18.dp)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text("Shizuku", modifier = Modifier.padding(vertical = 4.dp))
                         }

                         Button(
                             onClick = {
                                 val intent = Intent(Intent.ACTION_VIEW, "https://github.com/roro2239/Stellar".toUri())
                                 try {
                                     context.startActivity(intent)
                                 } catch (_: Exception) {
                                     Toast.makeText(context, context.getString(R.string.cannot_open_browser), Toast.LENGTH_SHORT).show()
                                 }
                             },
                             modifier = Modifier.weight(1f),
                             shape = AppShape.shapes.buttonMedium
                         ) {
                             Icon(
                                 painter = painterResource(R.drawable.ic_github),
                                 contentDescription = null,
                                 modifier = Modifier.size(18.dp)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text("Stellar", modifier = Modifier.padding(vertical = 4.dp))
                         }
                     }
                }
            }
            }
        }
    }

    if (showUpdateDialog && pendingUpdate != null) {
        NewVersionDialog(
            update = pendingUpdate!!,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            onDismiss = {
                if (!isDownloading) {
                    showUpdateDialog = false
                    downloadError = null
                }
            },
            onDownload = {
                val url = pendingUpdate!!.downloadUrl
                if (url.isNotEmpty()) {
                    isDownloading = true
                    downloadProgress = 0
                    downloadError = null
                    scope.launch {
                        ApkDownloader.download(context, url, "stellar_${pendingUpdate!!.versionName}.apk").collect { state ->
                            when (state) {
                                is DownloadState.Progress -> downloadProgress = state.progress
                                is DownloadState.Success -> {
                                    isDownloading = false
                                    showUpdateDialog = false
                                    ApkDownloader.installApk(context, state.file)
                                }
                                is DownloadState.Error -> {
                                    isDownloading = false
                                    downloadError = state.message
                                }
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.no_download_available), Toast.LENGTH_SHORT).show()
                }
            },
            onOpenBrowser = {
                showUpdateDialog = false
                downloadError = null
                val url = pendingUpdate!!.downloadUrl
                if (url.isNotEmpty()) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_browser), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showSourceDialog) {
        UpdateSourceDialog(
            onDismiss = { showSourceDialog = false },
            onSourceSelected = { source ->
                showSourceDialog = false
                currentSource = source
                performCheckUpdate(source)
            }
        )
    }

    if (showAccessibilityHintDialog) {
        BasicAlertDialog(onDismissRequest = { showAccessibilityHintDialog = false }) {
            Surface(
                shape = AppShape.shapes.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.accessibility_hint_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.accessibility_hint_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAccessibilityHintDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(onClick = {
                            showAccessibilityHintDialog = false
                            savePreference(StellarSettings.ACCESSIBILITY_AUTO_START_PROMPTED, true)
                            bootBroadcastAccessibilityEnabled = true
                            savePreference(StellarSettings.BOOT_BROADCAST_ACCESSIBILITY_ENABLED, true)
                            scope.launch(Dispatchers.IO) {
                                AppDatabase.get(context).configDao().set(
                                    ConfigEntity("accessibilityAutoStart", true.toString())
                                )
                            }
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }

    if (showBootGuideDialog) {
        BasicAlertDialog(onDismissRequest = { showBootGuideDialog = false }) {
            Surface(
                shape = AppShape.shapes.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.boot_start_guide_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.boot_start_guide_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showBootGuideDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(onClick = {
                            showBootGuideDialog = false
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }) {
                            Text(stringResource(R.string.boot_start_guide_go_settings))
                        }
                    }
                }
            }
        }
    }

    if (showScriptInstallDialog) {
        BasicAlertDialog(onDismissRequest = { showScriptInstallDialog = false }) {
            Surface(
                shape = AppShape.shapes.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.boot_start_script_install_confirm_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.boot_start_script_install_confirm_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showScriptInstallDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(onClick = {
                            showScriptInstallDialog = false
                            scriptActionInProgress = true
                            scope.launch(Dispatchers.IO) {
                                val result = BootScriptManager.installScript()
                                withContext(Dispatchers.Main) {
                                    scriptActionInProgress = false
                                    if (result.success) {
                                        applyBootMode(
                                            context, componentName, StellarSettings.BootMode.SCRIPT,
                                            bootMode, scope
                                        ) { bootMode = StellarSettings.BootMode.SCRIPT }
                                    }
                                    Toast.makeText(
                                        context,
                                        if (result.success) context.getString(R.string.boot_script_install_success)
                                        else context.getString(R.string.boot_script_install_failed, result.message),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }

    if (showScriptRemoveDialog) {
        BasicAlertDialog(onDismissRequest = {
            showScriptRemoveDialog = false
            pendingBootModeAfterScriptRemoval = null
        }) {
            Surface(
                shape = AppShape.shapes.dialog,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.boot_start_script_remove_confirm_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.boot_start_script_remove_confirm_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showScriptRemoveDialog = false
                            pendingBootModeAfterScriptRemoval = null
                        }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(onClick = {
                            showScriptRemoveDialog = false
                            scriptActionInProgress = true
                            scope.launch(Dispatchers.IO) {
                                val result = BootScriptManager.removeScript()
                                withContext(Dispatchers.Main) {
                                    scriptActionInProgress = false
                                    if (result.success) {
                                        val nextMode = pendingBootModeAfterScriptRemoval
                                            ?: StellarSettings.BootMode.NONE
                                        pendingBootModeAfterScriptRemoval = null
                                        if (nextMode == StellarSettings.BootMode.NONE) {
                                            applyBootMode(
                                                context,
                                                componentName,
                                                StellarSettings.BootMode.NONE,
                                                bootMode,
                                                scope
                                            ) { bootMode = StellarSettings.BootMode.NONE }
                                        } else {
                                            applyBootMode(
                                                context,
                                                componentName,
                                                nextMode,
                                                bootMode,
                                                scope
                                            ) {
                                                bootMode = nextMode
                                                if (nextMode == StellarSettings.BootMode.BROADCAST) {
                                                    showBootGuideDialog = true
                                                }
                                            }
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        if (result.success) context.getString(R.string.boot_script_remove_success)
                                        else context.getString(R.string.boot_script_remove_failed, result.message),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

private fun savePreference(key: String, value: Boolean) {
    StellarSettings.getPreferences().edit { putBoolean(key, value) }
}

private fun isBootAdbStartAvailable(): Boolean {
    if (!Stellar.pingBinder()) return true
    val writeSecureSettings = hasRemotePermission("android.permission.WRITE_SECURE_SETTINGS")
    val grantRuntimePermission = hasRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS")
    val bootAdbPortAvailable = atLeast30 ||
        EnvironmentUtils.getAdbTcpPort() > 0
    val commandAvailable = canExecuteCommand("id") &&
        canExecuteCommand("getprop ro.build.version.sdk")
    return writeSecureSettings &&
        grantRuntimePermission &&
        UserHandleCompat.myUserId() == 0 &&
        bootAdbPortAvailable &&
        commandAvailable
}

private fun hasRemotePermission(permission: String): Boolean =
    Stellar.checkRemotePermission(permission) == PackageManager.PERMISSION_GRANTED

private fun canExecuteCommand(command: String): Boolean {
    val process = try {
        Stellar.newProcess(arrayOf("sh", "-c", command), null, null)
    } catch (_: Throwable) {
        return false
    }

    return try {
        if (!process.waitForTimeout(1500, TimeUnit.MILLISECONDS)) {
            process.destroy()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Throwable) {
        runCatching { process.destroy() }
        false
    }
}

private fun applyBootMode(
    context: Context,
    componentName: ComponentName,
    newMode: StellarSettings.BootMode,
    currentMode: StellarSettings.BootMode,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            when (currentMode) {
                StellarSettings.BootMode.BROADCAST,
                StellarSettings.BootMode.TCPIP_PREWARM -> {
                    context.packageManager.setComponentEnabled(componentName, false)
                }
                StellarSettings.BootMode.SCRIPT, StellarSettings.BootMode.NONE -> Unit
            }
            when (newMode) {
                StellarSettings.BootMode.BROADCAST,
                StellarSettings.BootMode.TCPIP_PREWARM -> {
                    context.packageManager.setComponentEnabled(componentName, true)
                }
                StellarSettings.BootMode.SCRIPT, StellarSettings.BootMode.NONE -> Unit
            }
            val accessibilityEnabled = newMode == StellarSettings.BootMode.BROADCAST &&
                StellarSettings.getPreferences().getBoolean(
                    StellarSettings.BOOT_BROADCAST_ACCESSIBILITY_ENABLED,
                    false
                )
            AppDatabase.get(context).configDao().set(
                ConfigEntity("accessibilityAutoStart", accessibilityEnabled.toString())
            )
            StellarSettings.setBootMode(newMode)
            withContext(Dispatchers.Main) { onSuccess() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply boot mode $newMode", e)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateCard(
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onLongClick: () -> Unit,
    currentSource: UpdateSource?,
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
                .clip(AppShape.shapes.cardMedium)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
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
                            shape = AppShape.shapes.iconSmall
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
                    FlowRow(
                        verticalArrangement = Arrangement.Center,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.check_update),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentSource != null) {
                                Text(
                                    text = currentSource.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = AppShape.shapes.tag
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.long_press_switch_source),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadError: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
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

                AnimatedVisibility(visible = isDownloading || downloadError != null) {
                    Column(modifier = Modifier.padding(top = AppSpacing.sectionSpacing)) {
                        if (isDownloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.downloading) + " $downloadProgress%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (downloadError != null) {
                            Text(
                                text = stringResource(R.string.download_failed, downloadError),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.dialogPadding))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = if (downloadError != null) onOpenBrowser else onDownload,
                        enabled = update.downloadUrl.isNotEmpty() && !isDownloading,
                        modifier = Modifier.weight(1f),
                        shape = AppShape.shapes.buttonMedium
                    ) {
                        Text(stringResource(if (downloadError != null) R.string.go_to_download else R.string.install))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSourceDialog(
    onDismiss: () -> Unit,
    onSourceSelected: (UpdateSource) -> Unit
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
                    text = stringResource(R.string.select_update_source),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(AppSpacing.sectionSpacing))

                UpdateSource.entries.forEach { source ->
                    Button(
                        onClick = { onSourceSelected(source) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = AppShape.shapes.buttonMedium
                    ) {
                        Text(source.displayName)
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.dialogPadding))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
