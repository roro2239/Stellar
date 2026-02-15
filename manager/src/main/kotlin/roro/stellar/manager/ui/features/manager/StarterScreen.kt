package roro.stellar.manager.ui.features.manager

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.R
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbPairingService
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.AppConstants
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.util.CommandExecutor
import roro.stellar.manager.util.EnvironmentUtils
import java.net.ConnectException
import javax.net.ssl.SSLException

private class NotRootedException : Exception("没有 Root 权限")

enum class StepStatus { PENDING, RUNNING, COMPLETED, ERROR, WARNING }

data class StepData(
    val title: String,
    val icon: ImageVector,
    val status: StepStatus = StepStatus.PENDING,
    val description: String = "",
    val needsUserAction: Boolean = false,
    val isOptional: Boolean = false
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarterScreen(
    isRoot: Boolean,
    host: String?,
    port: Int,
    hasSecureSettings: Boolean = false,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: StarterViewModel = viewModel(
        key = "starter_${isRoot}_${host}_${port}_$hasSecureSettings",
        factory = StarterViewModelFactory(context, isRoot, host, port, hasSecureSettings)
    )
    val steps by viewModel.steps.collectAsState()
    val currentStepIndex by viewModel.currentStepIndex.collectAsState()
    val isCompleted by viewModel.isCompleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val outputLines by viewModel.outputLines.collectAsState()
    val command by viewModel.command.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val scrollState = rememberScrollState()

    val horizontalPadding = if (isLandscape) 48.dp else AppSpacing.screenHorizontalPadding

    LaunchedEffect(currentStepIndex, steps) {
        if (currentStepIndex > 0) {
            val targetScroll = (currentStepIndex * 140).coerceAtMost(scrollState.maxValue)
            scrollState.animateScrollTo(
                targetScroll,
                animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(isCompleted, steps) {
        if (isCompleted && steps.none { it.status == StepStatus.ERROR } && errorMessage == null) {
            delay(3000)
            onClose()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FixedTopAppBar(
                title = if (isRoot) stringResource(R.string.root_start_title) else stringResource(R.string.wireless_debugging_start),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(
                    horizontal = horizontalPadding,
                    vertical = AppSpacing.topBarContentSpacing
                )
                .padding(bottom = AppSpacing.screenBottomPadding)
        ) {
            steps.forEachIndexed { index, step ->
                // 跳过不需要的可选步骤
                if (step.isOptional && step.status == StepStatus.PENDING && index < currentStepIndex) {
                    return@forEachIndexed
                }

                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(index) {
                    delay(index * 100L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -24 }
                ) {
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (step.status == StepStatus.PENDING) 0.7f else 1f,
                        animationSpec = tween(300),
                        label = "alpha"
                    )

                    Box(modifier = Modifier.graphicsLayer { alpha = animatedAlpha }) {
                        TimelineStep(
                            isFirst = index == 0,
                            isLast = index == steps.lastIndex && errorMessage == null,
                            title = step.title,
                            icon = step.icon,
                            status = step.status,
                            description = step.description,
                            action = if (step.needsUserAction && step.status == StepStatus.RUNNING) {
                                { StepActionContent(step, viewModel, context) }
                            } else null
                        )
                    }
                }
            }

            if (errorMessage != null) {
                var logVisible by remember { mutableStateOf(false) }
                LaunchedEffect(errorMessage) {
                    delay(steps.size * 100L + 200L)
                    logVisible = true
                    delay(300)
                    scrollState.animateScrollTo(
                        scrollState.maxValue,
                        animationSpec = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }

                AnimatedVisibility(
                    visible = logVisible,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -24 }
                ) {
                    Column {
                        if (outputLines.isNotEmpty()) {
                            TimelineLogCard(
                                isLast = false,
                                title = stringResource(R.string.error_report),
                                icon = Icons.Filled.Description,
                                command = command,
                                outputLines = outputLines,
                                context = context,
                                isSuccess = false,
                                errorMessage = errorMessage
                            )
                        }

                        TimelineActionStep(
                            isLast = false,
                            title = stringResource(R.string.retry),
                            icon = Icons.Filled.Refresh,
                            onClick = { viewModel.retry() }
                        )

                        TimelineActionStep(
                            isLast = true,
                            title = stringResource(R.string.back),
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onClose
                        )
                    }
                }
            }

            if (isCompleted && outputLines.isNotEmpty()) {
                var logVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(steps.size * 100L + 200L)
                    logVisible = true
                    delay(300)
                    scrollState.animateScrollTo(
                        scrollState.maxValue,
                        animationSpec = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                }

                AnimatedVisibility(
                    visible = logVisible,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -24 }
                ) {
                    TimelineLogCard(
                        isLast = true,
                        title = stringResource(R.string.startup_log),
                        icon = Icons.Filled.Description,
                        command = command,
                        outputLines = outputLines,
                        context = context,
                        isSuccess = true
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun StepActionContent(
    step: StepData,
    viewModel: StarterViewModel,
    context: Context
) {
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationPermission(isGranted)
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.need_notification_permission), Toast.LENGTH_LONG).show()
        }
    }

    Column {
        Spacer(Modifier.height(12.dp))

        when (step.title) {
            stringResource(R.string.enable_wireless_debugging) -> {
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                                }
                            )
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_dev_options), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.cardMedium
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.open_dev_options), Modifier.padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.continueAfterSetup() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.cardMedium
                ) {
                    Text(stringResource(R.string.already_enabled_continue), Modifier.padding(vertical = 4.dp))
                }
            }

            stringResource(R.string.grant_notification_permission) -> {
                if (!hasNotificationPermission) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, context.getString(R.string.cannot_open_notification_settings), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShape.shapes.cardMedium
                    ) {
                        Text(stringResource(R.string.grant_permission), Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            stringResource(R.string.wireless_debugging_pairing) -> {
                Button(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                                }
                            )
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_dev_options), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.cardMedium
                ) {
                    Text(stringResource(R.string.open_wireless_debugging_settings), Modifier.padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.continueAfterSetup() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.cardMedium
                ) {
                    Text(stringResource(R.string.pairing_done_continue), Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineStep(
    isFirst: Boolean,
    isLast: Boolean,
    title: String,
    icon: ImageVector,
    status: StepStatus,
    description: String,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val isCompleted = status == StepStatus.COMPLETED
    val isRunning = status == StepStatus.RUNNING
    val isPending = status == StepStatus.PENDING
    val isError = status == StepStatus.ERROR
    val isWarning = status == StepStatus.WARNING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
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
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            isWarning -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                        },
                        CircleShape
                    )
                    .padding(6.dp)
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isError -> MaterialTheme.colorScheme.error
                            isWarning -> MaterialTheme.colorScheme.tertiary
                            isRunning -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCompleted -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    isError -> Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(16.dp)
                    )
                    isWarning -> Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    isRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            when {
                                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            }
                        )
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = AppShape.shapes.cardLarge,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                isError -> MaterialTheme.colorScheme.errorContainer
                isWarning -> MaterialTheme.colorScheme.tertiaryContainer
                isRunning -> MaterialTheme.colorScheme.surfaceContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
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
                                when {
                                    isCompleted -> MaterialTheme.colorScheme.primaryContainer
                                    isError -> MaterialTheme.colorScheme.errorContainer
                                    isWarning -> MaterialTheme.colorScheme.tertiaryContainer
                                    isRunning -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                AppShape.shapes.iconSmall
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isError -> MaterialTheme.colorScheme.error
                                isWarning -> MaterialTheme.colorScheme.tertiary
                                isRunning -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isPending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isPending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isError -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        isWarning -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                action?.invoke(this)
            }
        }
    }
}

@SuppressLint("StringFormatInvalid")
@Composable
private fun TimelineLogCard(
    isLast: Boolean,
    title: String,
    icon: ImageVector,
    command: String,
    outputLines: List<String>,
    context: Context,
    isSuccess: Boolean,
    errorMessage: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
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
                        if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .padding(6.dp)
                    .background(
                        if (isSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            if (isSuccess) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        )
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = AppShape.shapes.cardLarge,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isSuccess) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                AppShape.shapes.iconSmall
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            val packageInfo = try {
                                context.packageManager.getPackageInfo(context.packageName, 0)
                            } catch (_: Exception) { null }
                            val unknownStr = context.getString(R.string.unknown)
                            val versionName = packageInfo?.versionName ?: unknownStr
                            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                packageInfo?.longVersionCode?.toString() ?: unknownStr
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo?.versionCode?.toString() ?: unknownStr
                            }
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            val currentTime = dateFormat.format(java.util.Date())

                            val logText = buildString {
                                appendLine("=== Stellar ${if (isSuccess) context.getString(R.string.startup_log) else context.getString(R.string.error_report)} ===")
                                appendLine()
                                appendLine(context.getString(R.string.device_info))
                                appendLine(context.getString(R.string.device_model, "${Build.MANUFACTURER} ${Build.MODEL}"))
                                appendLine(context.getString(R.string.android_version, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
                                appendLine(context.getString(R.string.app_version, "$versionName ($versionCode)"))
                                appendLine(context.getString(R.string.time_label, currentTime))
                                appendLine()
                                appendLine(context.getString(R.string.executed_command))
                                appendLine(command)
                                appendLine()
                                appendLine(context.getString(R.string.command_output))
                                outputLines.forEach { appendLine(it) }
                                if (!isSuccess && !errorMessage.isNullOrBlank()) {
                                    appendLine()
                                    appendLine(context.getString(R.string.error_info))
                                    appendLine(errorMessage)
                                }
                            }
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stellar Log", logText))
                            Toast.makeText(context, context.getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
                        },
                        shape = AppShape.shapes.buttonSmall14
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.copy), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineActionStep(
    isLast: Boolean,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧时间线指示器
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .padding(6.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = AppShape.shapes.cardLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            AppShape.shapes.iconSmall
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

internal class StarterViewModel(
    private val context: Context,
    private val isRoot: Boolean,
    private val host: String?,
    private val port: Int,
    private val hasSecureSettings: Boolean = false
) : ViewModel() {

    // 步骤列表
    private val _steps = MutableStateFlow<List<StepData>>(emptyList())
    val steps: StateFlow<List<StepData>> = _steps.asStateFlow()

    // 当前步骤索引
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    // 是否完成
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 执行命令
    private val _command = MutableStateFlow(if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}")
    val command: StateFlow<String> = _command.asStateFlow()

    // 通知权限状态
    private val _hasNotificationPermission = MutableStateFlow(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }
    )
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _outputLines = MutableStateFlow<List<String>>(emptyList())
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    private val adbWirelessHelper = AdbWirelessHelper()
    private var adbMdns: AdbMdns? = null
    private var detectedPort: Int = 0

    // 配对流程状态
    private enum class PairingPhase { NONE, ENABLE_WIRELESS, PAIRING }
    private var pairingPhase = PairingPhase.NONE

    init {
        initializeSteps()
        startProcess()
    }

    private fun initializeSteps() {
        _steps.value = if (isRoot) {
            listOf(
                StepData(context.getString(R.string.check_root_permission), Icons.Filled.Security, StepStatus.PENDING, context.getString(R.string.waiting_check)),
                StepData(context.getString(R.string.check_existing_service), Icons.Filled.Search, StepStatus.PENDING, context.getString(R.string.waiting_execute)),
                StepData(context.getString(R.string.start_service_process), Icons.Filled.RocketLaunch, StepStatus.PENDING, context.getString(R.string.waiting_execute)),
                StepData(context.getString(R.string.wait_binder_response), Icons.Filled.Sync, StepStatus.PENDING, context.getString(R.string.waiting_execute)),
                StepData(context.getString(R.string.start_complete), Icons.Filled.CheckCircle, StepStatus.PENDING, context.getString(R.string.waiting_complete))
            )
        } else {
            listOf(
                StepData(context.getString(R.string.detect_adb_port), Icons.Filled.Wifi, StepStatus.PENDING, context.getString(R.string.waiting_detect)),
                StepData(context.getString(R.string.detect_pairing_status), Icons.Filled.VpnKey, StepStatus.PENDING, context.getString(R.string.waiting_detect)),
                StepData(context.getString(R.string.connect_adb_service), Icons.Filled.Cable, StepStatus.PENDING, context.getString(R.string.waiting_connect)),
                StepData(context.getString(R.string.verify_connection), Icons.Filled.VerifiedUser, StepStatus.PENDING, context.getString(R.string.waiting_verify)),
                StepData(context.getString(R.string.check_existing_service), Icons.Filled.Search, StepStatus.PENDING, context.getString(R.string.waiting_execute)),
                StepData(context.getString(R.string.start_service_process), Icons.Filled.RocketLaunch, StepStatus.PENDING, context.getString(R.string.waiting_execute)),
                StepData(context.getString(R.string.wait_binder_response), Icons.Filled.Sync, StepStatus.PENDING, context.getString(R.string.waiting_execute)),
                StepData(context.getString(R.string.start_complete), Icons.Filled.CheckCircle, StepStatus.PENDING, context.getString(R.string.waiting_complete))
            )
        }
    }

    private fun updateStep(index: Int, status: StepStatus, description: String, needsUserAction: Boolean = false) {
        viewModelScope.launch {
            // 添加延迟，让步骤更新不要太快
            if (status == StepStatus.RUNNING || status == StepStatus.COMPLETED) {
                delay(300)
            }
            val currentSteps = _steps.value.toMutableList()
            if (index in currentSteps.indices) {
                currentSteps[index] = currentSteps[index].copy(
                    status = status,
                    description = description,
                    needsUserAction = needsUserAction
                )
                _steps.value = currentSteps
                if (status == StepStatus.RUNNING) {
                    _currentStepIndex.value = index
                }
            }
        }
    }

    private fun insertStep(afterIndex: Int, step: StepData) {
        val currentSteps = _steps.value.toMutableList()
        if (afterIndex in -1 until currentSteps.size) {
            currentSteps.add(afterIndex + 1, step)
            _steps.value = currentSteps
        }
    }

    private fun addOutputLine(line: String) {
        viewModelScope.launch { _outputLines.value = _outputLines.value + line }
    }

    fun setNotificationPermission(granted: Boolean) {
        _hasNotificationPermission.value = granted
        if (granted) {
            val currentSteps = _steps.value
            val notificationStepIndex = currentSteps.indexOfFirst { it.title == context.getString(R.string.grant_notification_permission) }
            if (notificationStepIndex >= 0) {
                updateStep(notificationStepIndex, StepStatus.COMPLETED, context.getString(R.string.notification_permission_granted))
                val nextIndex = notificationStepIndex + 1
                if (nextIndex < currentSteps.size) {
                    updateStep(nextIndex, StepStatus.RUNNING, currentSteps[nextIndex].description, true)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startPairingService()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(context)
        try {
            context.startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "启动前台服务失败", e)
        }
    }

    fun continueAfterSetup() {
        viewModelScope.launch {
            when (pairingPhase) {
                PairingPhase.ENABLE_WIRELESS -> {
                    // 用户已开启无线调试，重新检测端口
                    _outputLines.value = emptyList()
                    _errorMessage.value = null
                    pairingPhase = PairingPhase.NONE
                    initializeSteps()
                    delay(300)
                    startProcess()
                }
                PairingPhase.PAIRING -> {
                    // 用户已完成配对，直接尝试连接
                    if (detectedPort > 0) {
                        _outputLines.value = emptyList()
                        _errorMessage.value = null

                        // 更新配对步骤为完成
                        val currentSteps = _steps.value
                        val pairingStepIndex = currentSteps.indexOfFirst { it.title == context.getString(R.string.wireless_debugging_pairing) }
                        if (pairingStepIndex >= 0) {
                            updateStep(pairingStepIndex, StepStatus.COMPLETED, context.getString(R.string.pairing_complete))
                        }

                        pairingPhase = PairingPhase.NONE
                        delay(300)
                        startAdbSteps()
                    } else {
                        // 没有检测到端口，重新开始
                        _outputLines.value = emptyList()
                        _errorMessage.value = null
                        pairingPhase = PairingPhase.NONE
                        initializeSteps()
                        delay(300)
                        startProcess()
                    }
                }
                PairingPhase.NONE -> {
                    // 默认行为：重新开始
                    _outputLines.value = emptyList()
                    _errorMessage.value = null
                    initializeSteps()
                    delay(300)
                    startProcess()
                }
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            _outputLines.value = emptyList()
            _errorMessage.value = null
            _isCompleted.value = false
            initializeSteps()
            delay(500)
            startProcess()
        }
    }

    private fun startProcess() {
        if (isRoot) {
            startRoot()
        } else {
            startDetection()
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSuccess() {
        viewModelScope.launch {
            val steps = _steps.value
            val lastIndex = steps.lastIndex
            // 先将所有未完成的步骤标记为完成
            steps.forEachIndexed { index, step ->
                if (step.status != StepStatus.COMPLETED && step.status != StepStatus.WARNING) {
                    updateStep(index, StepStatus.COMPLETED, if (index == lastIndex) context.getString(R.string.service_started_successfully) else context.getString(R.string.completed))
                }
            }
            _isCompleted.value = true
            launch(Dispatchers.IO) { CommandExecutor.executeFollowServiceCommands() }

            // 非 Root 模式下，检查是否需要切换到用户设置的端口
            if (!isRoot && detectedPort > 0) {
                val (shouldChange, newPort) = adbWirelessHelper.shouldChangePort(detectedPort)
                if (shouldChange && newPort > 0) {
                    addOutputLine("\n${context.getString(R.string.switching_port, newPort)}")
                    adbWirelessHelper.changeTcpipPortAfterStart(
                        host = host ?: "127.0.0.1",
                        port = detectedPort,
                        newPort = newPort,
                        coroutineScope = viewModelScope,
                        onOutput = { output -> addOutputLine(output) },
                        onError = { error ->
                            val errorMsg = error.message ?: context.getString(R.string.unknown_error)
                            addOutputLine(context.getString(R.string.port_switch_failed, errorMsg))
                            Log.w(AppConstants.TAG, "端口切换失败", error)
                        },
                        onSuccess = {
                            addOutputLine(context.getString(R.string.port_switched_to, newPort))
                            Log.i(AppConstants.TAG, "端口已切换到 $newPort")
                        }
                    )
                }
            }
        }
    }

    private fun setError(error: Throwable, stepIndex: Int) {
        viewModelScope.launch {
            updateStep(stepIndex, StepStatus.ERROR, error.message ?: context.getString(R.string.execution_failed))
            _errorMessage.value = error.message ?: context.getString(R.string.unknown_error)
        }
    }

    // ========== ADB 检测流程 ==========

    @SuppressLint("StringFormatInvalid")
    private fun startDetection() {
        viewModelScope.launch(Dispatchers.IO) {
            launch(Dispatchers.Main) {
                updateStep(0, StepStatus.RUNNING, context.getString(R.string.detecting_adb_port))
            }

            val preferences = StellarSettings.getPreferences()
            val tcpipPortEnabled = preferences.getBoolean(StellarSettings.TCPIP_PORT_ENABLED, true)
            val customPort = preferences.getString(StellarSettings.TCPIP_PORT, "")?.toIntOrNull()
            val hasValidCustomPort = tcpipPortEnabled && customPort != null && customPort in 1..65535
            val systemPort = EnvironmentUtils.getAdbTcpPort()

            if (hasValidCustomPort) {
                val canConnect = adbWirelessHelper.hasAdbPermission(host ?: "127.0.0.1", customPort)
                if (canConnect) {
                    detectedPort = customPort
                    launch(Dispatchers.Main) {
                        updateStep(0, StepStatus.COMPLETED, context.getString(R.string.port_available, customPort))
                        updateStep(1, StepStatus.COMPLETED, context.getString(R.string.already_paired))
                        delay(300)
                        startAdbSteps()
                    }
                    return@launch
                }
            }

            if (systemPort in 1..65535) {
                val canConnect = adbWirelessHelper.hasAdbPermission(host ?: "127.0.0.1", systemPort)
                if (canConnect) {
                    detectedPort = systemPort
                    launch(Dispatchers.Main) {
                        updateStep(0, StepStatus.COMPLETED, context.getString(R.string.port_available, systemPort))
                        updateStep(1, StepStatus.COMPLETED, context.getString(R.string.already_paired))
                        delay(300)
                        startAdbSteps()
                    }
                    return@launch
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                launch(Dispatchers.Main) {
                    startMdnsDetection(hasValidCustomPort, customPort ?: -1)
                }
            } else {
                launch(Dispatchers.Main) {
                    updateStep(0, StepStatus.WARNING, context.getString(R.string.no_port_detected))
                    showEnableWirelessAdbStep()
                }
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun startMdnsDetection(hasValidCustomPort: Boolean, customPort: Int) {
        var handled = false
        val portObserver = Observer<Int> { discoveredPort ->
            if (discoveredPort in 1..65535 && !handled) {
                handled = true
                adbMdns?.stop()
                adbMdns = null

                viewModelScope.launch(Dispatchers.IO) {
                    val canConnect = adbWirelessHelper.hasAdbPermission(host ?: "127.0.0.1", discoveredPort)
                    launch(Dispatchers.Main) {
                        updateStep(0, StepStatus.COMPLETED, context.getString(R.string.port_available, discoveredPort))

                        if (canConnect) {
                            detectedPort = discoveredPort
                            updateStep(1, StepStatus.COMPLETED, context.getString(R.string.already_paired))
                            delay(300)
                            startAdbSteps()
                        } else {
                            detectedPort = discoveredPort
                            updateStep(1, StepStatus.WARNING, context.getString(R.string.need_pairing))
                            delay(300)
                            showPairingSteps()
                        }
                    }
                }
            }
        }

        adbMdns = AdbMdns(
            context = context,
            serviceType = AdbMdns.TLS_CONNECT,
            observer = portObserver,
            onMaxRefresh = {
                if (!handled) {
                    handled = true
                    updateStep(0, StepStatus.WARNING, context.getString(R.string.no_port_detected))
                    showEnableWirelessAdbStep()
                }
            },
            maxRefreshCount = 3
        ).apply { start() }
    }

    private fun showEnableWirelessAdbStep() {
        pairingPhase = PairingPhase.ENABLE_WIRELESS
        insertStep(0, StepData(
            title = context.getString(R.string.enable_wireless_debugging),
            icon = Icons.Filled.WifiOff,
            status = StepStatus.RUNNING,
            description = context.getString(R.string.enable_wireless_debugging_hint),
            needsUserAction = true
        ))
        _currentStepIndex.value = 1
    }

    private fun showPairingSteps() {
        pairingPhase = PairingPhase.PAIRING
        val hasPermission = _hasNotificationPermission.value

        insertStep(1, StepData(
            title = context.getString(R.string.grant_notification_permission),
            icon = Icons.Filled.Notifications,
            status = if (hasPermission) StepStatus.COMPLETED else StepStatus.RUNNING,
            description = if (hasPermission) context.getString(R.string.notification_permission_granted) else context.getString(R.string.notification_permission_required),
            needsUserAction = !hasPermission
        ))

        insertStep(2, StepData(
            title = context.getString(R.string.wireless_debugging_pairing),
            icon = Icons.Filled.QrCode,
            status = if (hasPermission) StepStatus.RUNNING else StepStatus.PENDING,
            description = context.getString(R.string.pairing_instruction),
            needsUserAction = hasPermission
        ))

        _currentStepIndex.value = if (hasPermission) 2 else 1

        if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startPairingService()
        }
    }

    private fun startAdbSteps() {
        val connectIndex = _steps.value.indexOfFirst { it.title == context.getString(R.string.connect_adb_service) }
        if (connectIndex >= 0) {
            updateStep(connectIndex, StepStatus.RUNNING, context.getString(R.string.connecting_ellipsis))
        }
        startAdbConnection(host ?: "127.0.0.1", detectedPort)
    }

    private fun startAdbConnection(host: String, port: Int) {
        addOutputLine("Connecting to $host:$port...")
        adbWirelessHelper.startStellarViaAdb(
            host = host, port = port, coroutineScope = viewModelScope,
            onOutput = { output ->
                addOutputLine(output)
                viewModelScope.launch(Dispatchers.Main) {
                    val steps = _steps.value
                    val connectIndex = steps.indexOfFirst { it.title == context.getString(R.string.connect_adb_service) }
                    val verifyIndex = steps.indexOfFirst { it.title == context.getString(R.string.verify_connection) }
                    val checkIndex = steps.indexOfFirst { it.title == context.getString(R.string.check_existing_service) }
                    val startIndex = steps.indexOfFirst { it.title == context.getString(R.string.start_service_process) }
                    val binderIndex = steps.indexOfFirst { it.title == context.getString(R.string.wait_binder_response) }

                    when {
                        output.contains("connected") -> {
                            if (connectIndex >= 0) updateStep(connectIndex, StepStatus.COMPLETED, context.getString(R.string.connection_success))
                            if (verifyIndex >= 0) updateStep(verifyIndex, StepStatus.COMPLETED, context.getString(R.string.verification_passed))
                        }
                        output.contains("检查现有服务") || output.contains("终止现有服务") -> {
                            if (connectIndex >= 0 && steps[connectIndex].status != StepStatus.COMPLETED) {
                                updateStep(connectIndex, StepStatus.COMPLETED, context.getString(R.string.connection_success))
                            }
                            if (verifyIndex >= 0 && steps[verifyIndex].status != StepStatus.COMPLETED) {
                                updateStep(verifyIndex, StepStatus.COMPLETED, context.getString(R.string.verification_passed))
                            }
                            if (checkIndex >= 0) updateStep(checkIndex, StepStatus.RUNNING, context.getString(R.string.checking_ellipsis))
                        }
                        output.contains("启动服务进程") -> {
                            if (checkIndex >= 0) updateStep(checkIndex, StepStatus.COMPLETED, context.getString(R.string.completed))
                            if (startIndex >= 0) updateStep(startIndex, StepStatus.RUNNING, context.getString(R.string.starting_ellipsis))
                            // 启动超时检查，2秒后如果还没开始等待 Binder，就自动开始
                            launch {
                                delay(2000)
                                val currentSteps = _steps.value
                                val currentBinderIndex = currentSteps.indexOfFirst { it.title == context.getString(R.string.wait_binder_response) }
                                if (currentBinderIndex >= 0 &&
                                    currentSteps[currentBinderIndex].status != StepStatus.RUNNING &&
                                    currentSteps[currentBinderIndex].status != StepStatus.COMPLETED) {
                                    val currentStartIndex = currentSteps.indexOfFirst { it.title == context.getString(R.string.start_service_process) }
                                    if (currentStartIndex >= 0) updateStep(currentStartIndex, StepStatus.COMPLETED, context.getString(R.string.completed))
                                    updateStep(currentBinderIndex, StepStatus.RUNNING, context.getString(R.string.waiting_response))
                                    waitForService()
                                }
                            }
                        }
                        // 服务进程已 fork，开始等待 Binder
                        output.contains("stellar_server 进程号为") || output.contains("stellar_starter 正常退出") -> {
                            if (startIndex >= 0) updateStep(startIndex, StepStatus.COMPLETED, context.getString(R.string.completed))
                            if (binderIndex >= 0 && steps[binderIndex].status != StepStatus.RUNNING &&
                                steps[binderIndex].status != StepStatus.COMPLETED) {
                                updateStep(binderIndex, StepStatus.RUNNING, context.getString(R.string.waiting_response))
                                waitForService()
                            }
                        }
                    }

                    output.lines().forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.startsWith("错误：")) {
                            setError(Exception(trimmedLine.substringAfter("错误：").trim()), _currentStepIndex.value)
                        }
                    }
                }
            },
            onError = { error ->
                addOutputLine("错误：${error.message}")
                viewModelScope.launch(Dispatchers.Main) {
                    val needsPairing = error is SSLException || error is ConnectException
                    if (needsPairing) {
                        // 配对失败，回到配对步骤
                        val pairingStepIndex = _steps.value.indexOfFirst { it.title == context.getString(R.string.wireless_debugging_pairing) }
                        if (pairingStepIndex >= 0) {
                            pairingPhase = PairingPhase.PAIRING
                            updateStep(pairingStepIndex, StepStatus.ERROR, context.getString(R.string.pairing_failed_retry), needsUserAction = true)
                            _errorMessage.value = null // 不显示错误卡片，让用户重试配对
                        } else {
                            val connectIndex = _steps.value.indexOfFirst { it.title == context.getString(R.string.connect_adb_service) }
                            setError(Exception(context.getString(R.string.need_pairing_to_connect)), if (connectIndex >= 0) connectIndex else _currentStepIndex.value)
                        }
                    } else {
                        val connectIndex = _steps.value.indexOfFirst { it.title == context.getString(R.string.connect_adb_service) }
                        setError(error, if (connectIndex >= 0) connectIndex else _currentStepIndex.value)
                    }
                }
            },
            onSuccess = {
                // 命令执行完成，确保等待服务
                viewModelScope.launch(Dispatchers.Main) {
                    val steps = _steps.value
                    val binderIndex = steps.indexOfFirst { it.title == context.getString(R.string.wait_binder_response) }

                    // 如果还没有开始等待 Binder，现在开始
                    if (binderIndex >= 0 && steps[binderIndex].status != StepStatus.RUNNING &&
                        steps[binderIndex].status != StepStatus.COMPLETED) {
                        // 先完成之前的步骤
                        val connectIndex = steps.indexOfFirst { it.title == context.getString(R.string.connect_adb_service) }
                        val verifyIndex = steps.indexOfFirst { it.title == context.getString(R.string.verify_connection) }
                        val checkIndex = steps.indexOfFirst { it.title == context.getString(R.string.check_existing_service) }
                        val startIndex = steps.indexOfFirst { it.title == context.getString(R.string.start_service_process) }

                        if (connectIndex >= 0) updateStep(connectIndex, StepStatus.COMPLETED, context.getString(R.string.connection_success))
                        if (verifyIndex >= 0) updateStep(verifyIndex, StepStatus.COMPLETED, context.getString(R.string.verification_passed))
                        if (checkIndex >= 0) updateStep(checkIndex, StepStatus.COMPLETED, context.getString(R.string.completed))
                        if (startIndex >= 0) updateStep(startIndex, StepStatus.COMPLETED, context.getString(R.string.completed))
                        updateStep(binderIndex, StepStatus.RUNNING, context.getString(R.string.waiting_response))
                        waitForService()
                    }
                }
            }
        )
    }

    // ========== Root 启动流程 ==========

    private fun startRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                launch(Dispatchers.Main) {
                    updateStep(0, StepStatus.RUNNING, context.getString(R.string.checking_root_permission))
                }

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    if (!Shell.getShell().isRoot) {
                        launch(Dispatchers.Main) {
                            setError(NotRootedException(), 0)
                        }
                        return@launch
                    }
                }

                launch(Dispatchers.Main) {
                    updateStep(0, StepStatus.COMPLETED, context.getString(R.string.root_permission_acquired))
                    updateStep(1, StepStatus.RUNNING, context.getString(R.string.checking_ellipsis))
                }

                addOutputLine("$ ${Starter.internalCommand}")
                Shell.cmd(Starter.internalCommand).to(object : CallbackList<String?>() {
                    override fun onAddElement(line: String?) {
                        line?.let {
                            addOutputLine(it)
                            viewModelScope.launch(Dispatchers.Main) {
                                when {
                                    it.contains("检查现有服务") || it.contains("终止现有服务") -> {
                                        updateStep(1, StepStatus.RUNNING, context.getString(R.string.checking_ellipsis))
                                    }
                                    it.contains("启动服务进程") -> {
                                        updateStep(1, StepStatus.COMPLETED, context.getString(R.string.completed))
                                        updateStep(2, StepStatus.RUNNING, context.getString(R.string.starting_ellipsis))
                                    }
                                    it.contains("stellar_starter 正常退出") -> {
                                        updateStep(2, StepStatus.COMPLETED, context.getString(R.string.completed))
                                        updateStep(3, StepStatus.RUNNING, context.getString(R.string.waiting_response))
                                        waitForService()
                                    }
                                }
                            }
                        }
                    }
                }).submit { result ->
                    if (result.code != 0) {
                        val errorMsg = getErrorMessage(result.code)
                        addOutputLine("错误：$errorMsg")
                        viewModelScope.launch(Dispatchers.Main) {
                            setError(Exception(errorMsg), 2)
                        }
                    }
                }
            } catch (e: Exception) {
                addOutputLine("Error: ${e.message}")
                launch(Dispatchers.Main) {
                    setError(e, 0)
                }
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun getErrorMessage(code: Int): String = when (code) {
        9 -> context.getString(R.string.error_cannot_kill_process)
        3 -> context.getString(R.string.error_cannot_set_classpath)
        4 -> context.getString(R.string.error_cannot_create_process)
        5 -> context.getString(R.string.error_app_process_failed)
        6 -> context.getString(R.string.error_permission_insufficient)
        7 -> context.getString(R.string.error_cannot_get_app_path)
        10 -> context.getString(R.string.error_selinux_blocked)
        else -> context.getString(R.string.error_start_failed_exit_code, code)
    }

    private fun waitForService() {
        viewModelScope.launch {
            var binderReceived = false
            val listener = object : Stellar.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    if (!binderReceived) {
                        binderReceived = true
                        Stellar.removeBinderReceivedListener(this)
                        setSuccess()
                    }
                }
            }
            Stellar.addBinderReceivedListener(listener)

            if (Stellar.pingBinder()) {
                binderReceived = true
                Stellar.removeBinderReceivedListener(listener)
                setSuccess()
                return@launch
            }

            val maxWaitTime = 15000L
            val checkInterval = 300L
            var elapsed = 0L

            while (elapsed < maxWaitTime && !binderReceived) {
                delay(checkInterval)
                elapsed += checkInterval

                if (!binderReceived && Stellar.pingBinder()) {
                    binderReceived = true
                    Stellar.removeBinderReceivedListener(listener)
                    setSuccess()
                    return@launch
                }

                if (hasErrorInOutput()) {
                    Stellar.removeBinderReceivedListener(listener)
                    setError(Exception(getLastErrorMessage()), if (isRoot) 3 else 6)
                    return@launch
                }
            }

            if (!binderReceived) {
                Stellar.removeBinderReceivedListener(listener)
                setError(Exception(context.getString(R.string.error_wait_service_timeout)), if (isRoot) 3 else 6)
            }
        }
    }

    private fun hasErrorInOutput(): Boolean = _outputLines.value.any {
        it.contains("错误：") || it.contains("Error:") ||
        it.contains("Exception") || it.contains("FATAL")
    }

    private fun getLastErrorMessage(): String {
        val errorLines = _outputLines.value.filter {
            it.contains("错误：") || it.contains("Error:")
        }
        return if (errorLines.isNotEmpty()) {
            errorLines.last().substringAfter("错误：").substringAfter("Error:").trim()
        } else context.getString(R.string.error_start_failed_check_logs)
    }
}

internal class StarterViewModelFactory(
    private val context: Context,
    private val isRoot: Boolean,
    private val host: String?,
    private val port: Int,
    private val hasSecureSettings: Boolean = false
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StarterViewModel(context, isRoot, host, port, hasSecureSettings) as T
    }
}

