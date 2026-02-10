package roro.stellar.manager.ui.features.manager

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import roro.stellar.manager.adb.AdbKeyException
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.StellarSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.util.CommandExecutor
import roro.stellar.manager.util.EnvironmentUtils
import java.net.ConnectException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception("没有 Root 权限")

// 启动步骤状态
enum class StepStatus { PENDING, RUNNING, COMPLETED, ERROR, WARNING }

data class StartStep(
    val title: String,
    val icon: ImageVector,
    val status: StepStatus = StepStatus.PENDING
)

sealed class StarterState {
    data class Loading(
        val command: String,
        val isSuccess: Boolean = false,
        val warningStepIndex: Int? = null
    ) : StarterState()
    data class Error(
        val error: Throwable,
        val command: String,
        val failedStepIndex: Int
    ) : StarterState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarterScreen(
    isRoot: Boolean,
    host: String?,
    port: Int,
    hasSecureSettings: Boolean = false,
    onClose: () -> Unit,
    onNavigateToAdbPairing: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: StarterViewModel = viewModel(
        key = "starter_${isRoot}_${host}_${port}_$hasSecureSettings",
        factory = StarterViewModelFactory(context, isRoot, host, port, hasSecureSettings)
    )
    val state by viewModel.state.collectAsState()
    val useMdnsDiscovery by viewModel.useMdnsDiscovery.collectAsState()

    LaunchedEffect(state) {
        if (state is StarterState.Loading && (state as StarterState.Loading).isSuccess) {
            delay(3000)
            onClose()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FixedTopAppBar(
                title = "Stellar 启动器",
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (state) {
            is StarterState.Loading -> {
                val loadingState = state as StarterState.Loading
                LoadingContent(
                    paddingValues = paddingValues,
                    command = loadingState.command,
                    outputLines = viewModel.outputLines.collectAsState().value,
                    isSuccess = loadingState.isSuccess,
                    isRoot = isRoot,
                    warningStepIndex = loadingState.warningStepIndex,
                    useMdnsDiscovery = useMdnsDiscovery
                )
            }
            is StarterState.Error -> {
                val errorState = state as StarterState.Error
                ErrorContent(
                    paddingValues = paddingValues,
                    command = errorState.command,
                    outputLines = viewModel.outputLines.collectAsState().value,
                    error = errorState.error,
                    failedStepIndex = errorState.failedStepIndex,
                    isRoot = isRoot,
                    onRetry = { viewModel.retry() },
                    onClose = onClose,
                    onNavigateToAdbPairing = onNavigateToAdbPairing
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    paddingValues: PaddingValues,
    command: String,
    outputLines: List<String>,
    isSuccess: Boolean,
    isRoot: Boolean,
    warningStepIndex: Int? = null,
    useMdnsDiscovery: Boolean = false
) {
    val context = LocalContext.current
    var countdown by remember { mutableIntStateOf(3) }
    val scrollState = rememberScrollState()

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    // 根据输出解析当前步骤
    val steps = remember(isRoot, useMdnsDiscovery) {
        if (isRoot) {
            listOf(
                StartStep("检查 Root 权限", Icons.Filled.Security),
                StartStep("检查现有服务", Icons.Filled.Search),
                StartStep("启动服务进程", Icons.Filled.RocketLaunch),
                StartStep("等待 Binder 响应", Icons.Filled.Sync),
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        } else if (useMdnsDiscovery) {
            // mDNS 模式：不需要切换端口步骤
            listOf(
                StartStep("连接 ADB 服务", Icons.Filled.Cable),
                StartStep("验证连接状态", Icons.Filled.VerifiedUser),
                StartStep("检查现有服务", Icons.Filled.Search),
                StartStep("启动服务进程", Icons.Filled.RocketLaunch),
                StartStep("等待 Binder 响应", Icons.Filled.Sync),
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        } else {
            listOf(
                StartStep("连接 ADB 服务", Icons.Filled.Cable),
                StartStep("验证连接状态", Icons.Filled.VerifiedUser),
                StartStep("检查现有服务", Icons.Filled.Search),
                StartStep("启动服务进程", Icons.Filled.RocketLaunch),
                StartStep("等待 Binder 响应", Icons.Filled.Sync),
                StartStep("切换 ADB 端口", Icons.Filled.SwapHoriz),
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        }
    }

    // 根据输出判断当前步骤
    val totalSteps = steps.size
    val currentStepIndex by remember(outputLines, isSuccess, useMdnsDiscovery) {
        derivedStateOf {
            when {
                isSuccess -> totalSteps - 1
                // 切换端口 (仅非 mDNS 的 ADB 模式有此步骤，index 5)
                outputLines.any { it.contains("切换端口") || it.contains("restarting in TCP mode") } ->
                    if (isRoot || useMdnsDiscovery) totalSteps - 2 else 5
                // 等待 Binder 响应 (Root: 3, ADB mDNS: 4, ADB 普通: 4)
                outputLines.any { it.contains("stellar_starter 正常退出") } ->
                    if (isRoot) 3 else 4
                outputLines.any { it.contains("启动服务进程") } -> if (isRoot) 2 else 3
                outputLines.any { it.contains("检查现有服务") || it.contains("终止现有服务") } ->
                    if (isRoot) 1 else 2
                // ADB 模式: 连接 ADB 服务 (index 0)
                outputLines.any { it.contains("Connecting") } -> 0
                outputLines.any { it.startsWith("$") } -> 0
                outputLines.isNotEmpty() -> 0
                else -> 0
            }
        }
    }

    // 自动滚动到当前步骤
    LaunchedEffect(currentStepIndex) {
        scrollState.animateScrollTo(currentStepIndex * 180)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
    ) {
        // 固定顶部状态卡片
        Column(
            modifier = Modifier.padding(
                top = AppSpacing.topBarContentSpacing,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            )
        ) {
            StarterStatusCard(isSuccess = isSuccess, isError = false, countdown = countdown)
        }

        Spacer(modifier = Modifier.height(AppSpacing.cardSpacing))

        // 可滚动的步骤列表
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing)
        ) {
            steps.forEachIndexed { index, step ->
                val status = when {
                    warningStepIndex == index -> StepStatus.WARNING
                    index < currentStepIndex -> StepStatus.COMPLETED
                    index == currentStepIndex -> if (isSuccess && index == totalSteps - 1) StepStatus.COMPLETED else StepStatus.RUNNING
                    else -> StepStatus.PENDING
                }

                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(index) {
                    delay(index * 50L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { -12 }
                ) {
                    StepCard(step = step.copy(status = status), index = index + 1)
                }
            }

            // 启动完成后显示复制日志卡片
            if (isSuccess && outputLines.isNotEmpty()) {
                var copyLogVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(totalSteps * 50L + 100L)
                    copyLogVisible = true
                    delay(150)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                AnimatedVisibility(
                    visible = copyLogVisible,
                    enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { -12 }
                ) {
                    CopyLogCard(
                        command = command,
                        outputLines = outputLines,
                        context = context
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepCard(step: StartStep, index: Int) {
    val isCompleted = step.status == StepStatus.COMPLETED
    val isRunning = step.status == StepStatus.RUNNING
    val isPending = step.status == StepStatus.PENDING
    val isError = step.status == StepStatus.ERROR
    val isWarning = step.status == StepStatus.WARNING

    val containerColor = when {
        isCompleted -> MaterialTheme.colorScheme.primaryContainer
        isWarning -> MaterialTheme.colorScheme.tertiaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        isRunning -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
        isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconBgColor = when {
        isCompleted -> contentColor.copy(alpha = 0.15f)
        isWarning -> contentColor.copy(alpha = 0.15f)
        isError -> contentColor.copy(alpha = 0.15f)
        isRunning -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val iconTint = when {
        isCompleted -> contentColor
        isWarning -> MaterialTheme.colorScheme.tertiary
        isError -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 1f,
        animationSpec = tween(200),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppShape.shapes.iconSmall)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCompleted -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    isWarning -> Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    isError -> Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    isRunning -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = iconTint
                    )
                    else -> Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                }
            }

            Spacer(modifier = Modifier.width(AppSpacing.iconTextSpacing))

            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRunning || isCompleted || isError) FontWeight.Medium else FontWeight.Normal,
                color = if (isPending) contentColor.copy(alpha = 0.5f) else contentColor
            )
        }
    }
}

@Composable
private fun CopyLogCard(
    command: String,
    outputLines: List<String>,
    context: android.content.Context
) {
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
                .padding(AppSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppShape.shapes.iconSmall)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.iconTextSpacing))

            Text(
                text = "启动日志",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    val logText = buildString {
                        appendLine("=== Stellar 启动日志 ===")
                        appendLine()
                        appendLine("执行命令:")
                        appendLine(command)
                        appendLine()
                        appendLine("命令输出:")
                        outputLines.forEach { appendLine(it) }
                    }
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stellar 启动日志", logText))
                    android.widget.Toast.makeText(context, "日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                },
                shape = AppShape.shapes.buttonSmall14
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StarterStatusCard(
    isSuccess: Boolean,
    isError: Boolean = false,
    countdown: Int = 0
) {
    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isSuccess -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.cardPaddingLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(AppSpacing.iconContainerSizeLarge)
                    .clip(AppShape.shapes.iconSmall)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isError -> Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(AppSpacing.iconSizeLarge)
                    )
                    isSuccess -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(AppSpacing.iconSizeLarge)
                    )
                    else -> CircularProgressIndicator(
                        modifier = Modifier.size(AppSpacing.iconSizeLarge),
                        strokeWidth = 3.dp,
                        color = iconColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(AppSpacing.iconTextSpacing))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isError -> "启动失败"
                        isSuccess -> "启动成功"
                        else -> "正在启动"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(AppSpacing.titleSubtitleSpacing))
                Text(
                    text = when {
                        isError -> "请查看错误信息"
                        isSuccess -> "Stellar 服务已成功启动"
                        else -> "请稍候片刻..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            if (isSuccess && countdown > 0) {
                Surface(
                    shape = AppShape.shapes.iconSmall,
                    color = contentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${countdown}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: String,
    outputLines: List<String>,
    isError: Boolean
) {
    val terminalBgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val commandColor = MaterialTheme.colorScheme.primary
    val outputColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(containerColor = terminalBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.cardPadding)
        ) {
            Text(
                text = "$ $command",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                color = commandColor,
                fontWeight = FontWeight.Medium
            )

            if (outputLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                outputLines.forEach { line ->
                    val lineColor = when {
                        isError -> errorColor
                        line.startsWith("错误") || line.contains("Error") -> errorColor
                        else -> outputColor
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = lineColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    paddingValues: PaddingValues,
    command: String,
    outputLines: List<String>,
    error: Throwable,
    failedStepIndex: Int,
    isRoot: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onNavigateToAdbPairing: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val needsPairing = error is SSLException || error is ConnectException

    val steps = remember(isRoot) {
        if (isRoot) {
            listOf(
                StartStep("检查 Root 权限", Icons.Filled.Security),
                StartStep("检查现有服务", Icons.Filled.Search),
                StartStep("启动服务进程", Icons.Filled.RocketLaunch),
                StartStep("等待 Binder 响应", Icons.Filled.Sync),
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        } else {
            listOf(
                StartStep("连接 ADB 服务", Icons.Filled.Cable),
                StartStep("验证连接状态", Icons.Filled.VerifiedUser),
                StartStep("检查现有服务", Icons.Filled.Search),
                StartStep("启动服务进程", Icons.Filled.RocketLaunch),
                StartStep("等待 Binder 响应", Icons.Filled.Sync),
                StartStep("切换 ADB 端口", Icons.Filled.SwapHoriz),
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        }
    }

    val totalSteps = steps.size

    LaunchedEffect(failedStepIndex) {
        delay(totalSteps * 50L + 350L)
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
    ) {
        Column(
            modifier = Modifier.padding(
                top = AppSpacing.topBarContentSpacing,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            )
        ) {
            StarterStatusCard(isSuccess = false, isError = true, countdown = 0)
        }

        Spacer(modifier = Modifier.height(AppSpacing.cardSpacing))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing)
        ) {
            steps.forEachIndexed { index, step ->
                val status = when {
                    index < failedStepIndex -> StepStatus.COMPLETED
                    index == failedStepIndex -> StepStatus.ERROR
                    else -> StepStatus.PENDING
                }

                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(index) {
                    delay(index * 50L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { -12 }
                ) {
                    StepCard(step = step.copy(status = status), index = index + 1)
                }
            }

            var copyVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(totalSteps * 50L + 100L)
                copyVisible = true
            }

            AnimatedVisibility(
                visible = copyVisible,
                enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { -12 }
            ) {
                CopyErrorReportCard(
                    command = command,
                    outputLines = outputLines,
                    error = error,
                    context = context
                )
            }

            var retryVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(totalSteps * 50L + 150L)
                retryVisible = true
            }

            AnimatedVisibility(
                visible = retryVisible,
                enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { -12 }
            ) {
                ActionCard(
                    icon = Icons.Filled.Refresh,
                    title = if (needsPairing) "前往配对" else "重试",
                    onClick = {
                        if (needsPairing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            onNavigateToAdbPairing?.invoke()
                            onClose()
                        } else {
                            onRetry()
                        }
                    }
                )
            }

            var backVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(totalSteps * 50L + 200L)
                backVisible = true
            }

            AnimatedVisibility(
                visible = backVisible,
                enter = fadeIn(tween(100)) + slideInVertically(tween(100)) { -12 }
            ) {
                ActionCard(
                    icon = Icons.Filled.ArrowBack,
                    title = "返回",
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CopyErrorReportCard(
    command: String,
    outputLines: List<String>,
    error: Throwable,
    context: android.content.Context
) {
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
                .padding(AppSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppShape.shapes.iconSmall)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.iconTextSpacing))

            Text(
                text = "错误报告",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager

                    val errorFromOutput = outputLines
                        .filter { it.contains("错误：") || it.contains("Error:") }
                        .lastOrNull()
                        ?.let { line ->
                            line.substringAfter("错误：", "")
                                .ifEmpty { line.substringAfter("Error:", "") }
                                .trim()
                        }
                    val errorMessage = errorFromOutput?.ifEmpty { null }
                        ?: error.message
                        ?: "未知错误"

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val currentTime = dateFormat.format(Date())

                    val logText = buildString {
                        appendLine("=== Stellar 启动错误报告 ===")
                        appendLine()
                        appendLine("时间: $currentTime")
                        appendLine("错误信息: $errorMessage")
                        appendLine()
                        appendLine("执行命令:")
                        appendLine(command)
                        appendLine()
                        if (outputLines.isNotEmpty()) {
                            appendLine("命令输出:")
                            outputLines.forEach { appendLine(it) }
                            appendLine()
                        }
                        appendLine("软件信息:")
                        appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine()
                        appendLine("设备信息:")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                    }
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stellar 错误报告", logText))
                    android.widget.Toast.makeText(context, "错误报告已复制", android.widget.Toast.LENGTH_SHORT).show()
                },
                shape = AppShape.shapes.buttonSmall14
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("复制", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppShape.shapes.iconSmall)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.iconTextSpacing))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
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

    private val _state = MutableStateFlow<StarterState>(
        StarterState.Loading(command = if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}")
    )
    val state: StateFlow<StarterState> = _state.asStateFlow()

    private val _outputLines = MutableStateFlow<List<String>>(emptyList())
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    // 是否使用 mDNS 发现模式（不需要切换端口步骤）
    private val _useMdnsDiscovery = MutableStateFlow(false)
    val useMdnsDiscovery: StateFlow<Boolean> = _useMdnsDiscovery.asStateFlow()

    private val lastCommand: String = if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}"

    private val adbWirelessHelper = AdbWirelessHelper()
    private var adbMdns: AdbMdns? = null

    init { startService() }

    private fun addOutputLine(line: String) {
        viewModelScope.launch { _outputLines.value = _outputLines.value + line }
    }

    private fun setSuccess() {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState is StarterState.Loading) {
                _state.value = currentState.copy(isSuccess = true)
                launch(Dispatchers.IO) { CommandExecutor.executeFollowServiceCommands() }
            }
        }
    }

    private fun setSuccessWithWarning(warningStepIndex: Int) {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState is StarterState.Loading) {
                _state.value = currentState.copy(isSuccess = true, warningStepIndex = warningStepIndex)
                launch(Dispatchers.IO) { CommandExecutor.executeFollowServiceCommands() }
            }
        }
    }

    private fun setError(error: Throwable, failedStepIndex: Int = 0) {
        viewModelScope.launch {
            _state.value = StarterState.Error(
                error = error,
                command = lastCommand,
                failedStepIndex = failedStepIndex
            )
        }
    }

    fun retry() {
        viewModelScope.launch {
            _state.value = StarterState.Loading(
                command = if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}"
            )
            _outputLines.value = emptyList()
            delay(500)
            startService()
        }
    }

    private fun startService() {
        if (isRoot) startRoot() else startAdb(host!!, port)
    }

    private fun startRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    if (!Shell.getShell().isRoot) {
                        setError(NotRootedException(), 0) // 检查 Root 权限失败
                        return@launch
                    }
                }
                addOutputLine("$ ${Starter.internalCommand}")
                Shell.cmd(Starter.internalCommand).to(object : CallbackList<String?>() {
                    override fun onAddElement(line: String?) {
                        line?.let {
                            addOutputLine(it)
                            if (it.contains("stellar_starter 正常退出")) waitForService()
                        }
                    }
                }).submit { result ->
                    if (result.code != 0) {
                        val errorMsg = getErrorMessage(result.code)
                        addOutputLine("错误：$errorMsg")
                        setError(Exception(errorMsg), 2) // 启动服务进程失败
                    }
                }
            } catch (e: Exception) {
                addOutputLine("Error: ${e.message}")
                setError(e, 2)
            }
        }
    }

    private fun getErrorMessage(code: Int): String = when (code) {
        9 -> "无法终止进程，请先从应用中停止现有服务"
        3 -> "无法设置 CLASSPATH"
        4 -> "无法创建进程"
        5 -> "app_process 执行失败"
        6 -> "权限不足，需要 root 或 adb 权限"
        7 -> "无法获取应用路径"
        10 -> "SELinux 阻止了应用通过 binder 连接"
        else -> "启动失败，退出码: $code"
    }

    private fun startAdb(host: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // 获取自定义端口设置
            val preferences = StellarSettings.getPreferences()
            val tcpipPortEnabled = preferences.getBoolean(StellarSettings.TCPIP_PORT_ENABLED, true)
            val customPort = preferences.getString(StellarSettings.TCPIP_PORT, "")?.toIntOrNull()
            val hasValidCustomPort = tcpipPortEnabled && customPort != null && customPort in 1..65535

            if (hasValidCustomPort) {
                // 1. 先尝试用自定义端口连接
                addOutputLine("尝试连接自定义端口: $customPort")
                val canConnect = adbWirelessHelper.hasAdbPermission(host, customPort!!)
                if (canConnect) {
                    // 自定义端口可用，直接激活服务
                    addOutputLine("自定义端口可用")
                    _useMdnsDiscovery.value = false
                    launch(Dispatchers.Main) {
                        startAdbConnection(host, customPort)
                    }
                    return@launch
                }
                addOutputLine("自定义端口不可用，切换到 mDNS 扫描")
            }

            // 2. 使用 mDNS 发现端口
            if (hasSecureSettings && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                addOutputLine("正在扫描 ADB 端口...")
                launch(Dispatchers.Main) {
                    startWithMdnsDiscovery(host, port, hasValidCustomPort, customPort ?: -1)
                }
            } else {
                // 没有 WRITE_SECURE_SETTINGS 权限，使用传入的端口
                _useMdnsDiscovery.value = false
                launch(Dispatchers.Main) {
                    startAdbConnection(host, port)
                }
            }
        }
    }

    private fun startWithMdnsDiscovery(
        host: String,
        fallbackPort: Int,
        shouldSwitchToCustomPort: Boolean = false,
        customPort: Int = -1
    ) {
        val portObserver = Observer<Int> { discoveredPort ->
            if (discoveredPort in 1..65535) {
                addOutputLine("发现 ADB 端口: $discoveredPort")
                adbMdns?.stop()
                adbMdns = null

                if (shouldSwitchToCustomPort && customPort in 1..65535) {
                    // 需要切换到自定义端口
                    _useMdnsDiscovery.value = false
                    switchPortThenStart(host, discoveredPort, customPort)
                } else {
                    // 直接使用发现的端口
                    _useMdnsDiscovery.value = true
                    startAdbConnection(host, discoveredPort)
                }
            }
        }

        adbMdns = AdbMdns(
            context = context,
            serviceType = AdbMdns.TLS_CONNECT,
            observer = portObserver,
            onMaxRefresh = {
                addOutputLine("mDNS 扫描次数已达上限，尝试使用系统端口")
                val systemPort = EnvironmentUtils.getAdbTcpPort()
                val finalPort = if (systemPort in 1..65535) systemPort else fallbackPort
                addOutputLine("使用端口: $finalPort")

                if (shouldSwitchToCustomPort && customPort in 1..65535) {
                    _useMdnsDiscovery.value = false
                    switchPortThenStart(host, finalPort, customPort)
                } else {
                    _useMdnsDiscovery.value = true
                    startAdbConnection(host, finalPort)
                }
            }
        ).apply { start() }
    }

    private fun switchPortThenStart(host: String, currentPort: Int, newPort: Int) {
        addOutputLine("切换端口: $currentPort -> $newPort")
        adbWirelessHelper.changeTcpipPortAfterStart(
            host = host,
            port = currentPort,
            newPort = newPort,
            coroutineScope = viewModelScope,
            onOutput = { addOutputLine(it) },
            onError = { error ->
                addOutputLine("端口切换失败: ${error.message}，使用当前端口启动")
                startAdbConnection(host, currentPort)
            },
            onSuccess = {
                addOutputLine("端口已切换到 $newPort")
                // 等待端口生效后启动
                viewModelScope.launch {
                    delay(1000)
                    startAdbConnection(host, newPort)
                }
            }
        )
    }

    private fun startAdbConnection(host: String, port: Int) {
        addOutputLine("Connecting to $host:$port...")
        adbWirelessHelper.startStellarViaAdb(
            host = host, port = port, coroutineScope = viewModelScope,
            onOutput = { output ->
                addOutputLine(output)
                output.lines().forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("错误：")) {
                        setError(Exception(trimmedLine.substringAfter("错误：").trim()), 4)
                    }
                }
                if (output.contains("stellar_starter 正常退出")) waitForService()
            },
            onError = { error ->
                addOutputLine("错误：${error.message}")
                val needsPairing = error is SSLException || error is ConnectException
                setError(error, if (needsPairing) 1 else 2)
            }
        )
    }

    private fun hasErrorInOutput(): Boolean = _outputLines.value.any {
        it.contains("错误：") || it.contains("Error:") ||
        it.contains("Exception") || it.contains("FATAL")
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
                    setError(Exception(getLastErrorMessage()), 3)
                    return@launch
                }
            }

            if (!binderReceived) {
                Stellar.removeBinderReceivedListener(listener)
                setError(Exception("等待服务启动超时\n\n服务进程可能已崩溃，请检查设备日志"), 3)
            }
        }
    }

    private fun getLastErrorMessage(): String {
        val errorLines = _outputLines.value.filter {
            it.contains("错误：") || it.contains("Error:")
        }
        return if (errorLines.isNotEmpty()) {
            errorLines.last().substringAfter("错误：").substringAfter("Error:").trim()
        } else "服务启动失败，请查看日志了解详情"
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
