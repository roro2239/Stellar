package roro.stellar.manager.ui.features.manager

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
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.ui.features.starter.Starter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import roro.stellar.manager.util.CommandExecutor
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception("没有 Root 权限")

// 启动步骤状态
enum class StepStatus { PENDING, RUNNING, COMPLETED, ERROR }

data class StartStep(
    val title: String,
    val icon: ImageVector,
    val status: StepStatus = StepStatus.PENDING
)

sealed class StarterState {
    data class Loading(val command: String, val isSuccess: Boolean = false) : StarterState()
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
    onClose: () -> Unit,
    onNavigateToAdbPairing: (() -> Unit)? = null
) {
    val viewModel: StarterViewModel = viewModel(
        key = "starter_${isRoot}_${host}_$port",
        factory = StarterViewModelFactory(isRoot, host, port)
    )
    val state by viewModel.state.collectAsState()

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
                    isRoot = isRoot
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
    isRoot: Boolean
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
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        }
    }

    // 根据输出判断当前步骤
    val totalSteps = steps.size
    val currentStepIndex by remember(outputLines, isSuccess) {
        derivedStateOf {
            when {
                isSuccess -> totalSteps - 1
                outputLines.any { it.contains("stellar_starter 正常退出") } -> totalSteps - 2
                outputLines.any { it.contains("启动服务进程") } -> if (isRoot) 2 else 3
                outputLines.any { it.contains("检查现有服务") || it.contains("终止现有服务") } ->
                    if (isRoot) 1 else 2
                outputLines.any { it.startsWith("$") || it.contains("Connecting") } ->
                    if (isRoot) 0 else 1
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

    val containerColor = when {
        isCompleted -> MaterialTheme.colorScheme.primaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        isRunning -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconBgColor = when {
        isCompleted -> contentColor.copy(alpha = 0.15f)
        isError -> contentColor.copy(alpha = 0.15f)
        isRunning -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val iconTint = when {
        isCompleted -> contentColor
        isError -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    // 完成时的缩放动画
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
            // 步骤图标
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

            // 步骤标题
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
            // 左侧图标
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

            // 中间标题和副标题
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

            // 右侧倒计时（仅成功时显示）
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
            // 命令行（带 $ 前缀）
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

            // 日志输出
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
    val needsPairing = error is SSLProtocolException || error is ConnectException

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
                StartStep("启动完成", Icons.Filled.CheckCircle)
            )
        }
    }

    val totalSteps = steps.size

    // 自动滚动到底部（等待所有卡片显示后）
    LaunchedEffect(failedStepIndex) {
        delay(totalSteps * 50L + 350L)
        scrollState.animateScrollTo(scrollState.maxValue)
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
            StarterStatusCard(isSuccess = false, isError = true, countdown = 0)
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

            // 复制报告卡片
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

            // 重试卡片
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

            // 返回卡片
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

                    // 从输出日志中提取错误信息
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
    private val isRoot: Boolean,
    private val host: String?,
    private val port: Int
) : ViewModel() {

    private val _state = MutableStateFlow<StarterState>(
        StarterState.Loading(command = if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}")
    )
    val state: StateFlow<StarterState> = _state.asStateFlow()

    private val _outputLines = MutableStateFlow<List<String>>(emptyList())
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    private val lastCommand: String = if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}"

    private val adbWirelessHelper = AdbWirelessHelper()

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
                setError(e, 2) // 启动服务进程失败
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
        addOutputLine("Connecting to $host:$port...")
        adbWirelessHelper.startStellarViaAdb(
            host = host, port = port, coroutineScope = viewModelScope,
            onOutput = { output ->
                addOutputLine(output)
                output.lines().forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("错误：")) {
                        setError(Exception(trimmedLine.substringAfter("错误：").trim()), 3) // 启动服务进程失败
                    }
                }
                if (output.contains("stellar_starter 正常退出")) waitForService()
            },
            onError = { error ->
                addOutputLine("错误：${error.message}")
                // ADB 连接或验证失败
                val stepIndex = if (error is SSLProtocolException || error is ConnectException) 0 else 1
                setError(error, stepIndex)
            }
        )
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

            val maxWaitTime = 10000L
            val checkInterval = 500L
            var elapsed = 0L

            while (elapsed < maxWaitTime && !binderReceived) {
                delay(checkInterval)
                elapsed += checkInterval
                if (hasErrorInOutput()) {
                    Stellar.removeBinderReceivedListener(listener)
                    // 等待 Binder 响应时出错 (Root: 3, ADB: 4)
                    setError(Exception(getLastErrorMessage()), if (isRoot) 3 else 4)
                    return@launch
                }
            }

            if (!binderReceived) {
                Stellar.removeBinderReceivedListener(listener)
                // 等待 Binder 响应超时 (Root: 3, ADB: 4)
                setError(Exception("等待服务启动超时\n\n服务进程可能已崩溃，请检查设备日志"), if (isRoot) 3 else 4)
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
        } else "服务启动失败，请查看日志了解详情"
    }
}

internal class StarterViewModelFactory(
    private val isRoot: Boolean,
    private val host: String?,
    private val port: Int
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StarterViewModel(isRoot, host, port) as T
    }
}
