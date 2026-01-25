package roro.stellar.manager.ui.features.manager

import android.os.Build
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import roro.stellar.manager.ui.features.starter.Starter
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.util.CommandExecutor
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception()

sealed class StarterState {
    data class Loading(val command: String, val isSuccess: Boolean = false) : StarterState()
    data class Error(val error: Throwable) : StarterState()
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
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is StarterState.Loading -> {
                    val loadingState = state as StarterState.Loading
                    LoadingView(
                        command = loadingState.command,
                        outputLines = viewModel.outputLines.collectAsState().value,
                        isSuccess = loadingState.isSuccess
                    )
                }
                is StarterState.Error -> {
                    ErrorView(
                        error = (state as StarterState.Error).error,
                        command = viewModel.lastCommand,
                        outputLines = viewModel.outputLines.collectAsState().value,
                        viewModel = viewModel,
                        onClose = onClose,
                        onNavigateToAdbPairing = onNavigateToAdbPairing
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView(
    command: String,
    outputLines: List<String>,
    isSuccess: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(3) }

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        StatusIcon(isSuccess = isSuccess)
        StatusText(isSuccess = isSuccess)
        CommandCard(
            command = command,
            outputLines = outputLines,
            isExpanded = isExpanded,
            onExpandToggle = { isExpanded = !isExpanded },
            isError = false
        )
        if (isSuccess) {
            CountdownCard(countdown = countdown)
        }
    }
}

@Composable
private fun StatusIcon(isSuccess: Boolean, isError: Boolean = false) {
    Surface(
        shape = AppShape.shapes.iconLarge,
        color = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isSuccess -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        },
        modifier = Modifier.size(140.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                isError -> {
                    Surface(
                        shape = AppShape.shapes.iconLarge,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        modifier = Modifier.size(100.dp)
                    ) {}
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(70.dp)
                    )
                }
                isSuccess -> {
                    Surface(
                        shape = AppShape.shapes.iconLarge,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(100.dp)
                    ) {}
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(70.dp)
                    )
                }
                else -> {
                    CircularProgressIndicator(Modifier.size(100.dp), strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    CircularProgressIndicator(Modifier.size(70.dp), strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StatusText(isSuccess: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (isSuccess) "启动成功" else "正在启动服务",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isSuccess) "Stellar 服务已成功启动" else "请稍候片刻...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CommandCard(
    command: String,
    outputLines: List<String>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    isError: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = AppShape.shapes.cardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).background(
                        color = if (isError) MaterialTheme.colorScheme.errorContainer
                               else MaterialTheme.colorScheme.primaryContainer,
                        shape = AppShape.shapes.iconSmall
                    ), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isError) MaterialTheme.colorScheme.error
                                  else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text("启动命令", style = MaterialTheme.typography.titleSmall,
                         fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onExpandToggle, modifier = Modifier.size(32.dp)) {
                    Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                         if (isExpanded) "收起" else "展开",
                         tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = AppShape.shapes.iconSmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                Text(command, style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp))
            }
            if (isExpanded && outputLines.isNotEmpty()) {
                HorizontalDivider()
                OutputLogSurface(outputLines = outputLines, isError = isError)
            }
        }
    }
}

@Composable
private fun OutputLogSurface(outputLines: List<String>, isError: Boolean) {
    Surface(
        shape = AppShape.shapes.iconSmall,
        color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
               else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            outputLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = if (isError) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CountdownCard(countdown: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            if (countdown > 0) {
                Text("$countdown", style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("秒后自动关闭", style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface)
            } else {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("正在关闭...", style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ErrorView(
    error: Throwable,
    command: String,
    outputLines: List<String>,
    viewModel: StarterViewModel,
    onClose: () -> Unit,
    onNavigateToAdbPairing: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val needsPairing = error is SSLProtocolException || error is ConnectException
    val isProcessKillError = error.message?.contains("无法终止进程") == true ||
                             error.message?.contains("停止现有服务") == true

    val (errorTitle, errorMessage, errorTip) = getErrorInfo(error, isProcessKillError)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        StatusIcon(isSuccess = false, isError = true)
        ErrorText(errorTitle = errorTitle, errorMessage = errorMessage)
        CommandCard(command, outputLines, isExpanded, { isExpanded = !isExpanded }, true)
        if (errorTip.isNotEmpty()) { ErrorTipCard(errorTip) }
        ErrorActions(isProcessKillError, needsPairing, viewModel, onClose,
                    onNavigateToAdbPairing, context, errorTitle, errorMessage,
                    errorTip, command, outputLines)
    }
}

private fun getErrorInfo(error: Throwable, isProcessKillError: Boolean): Triple<String, String, String> {
    return when {
        isProcessKillError -> Triple("服务已在运行", "无法终止现有进程", "")
        error is AdbKeyException -> Triple("KeyStore 错误", "设备的 KeyStore 机制已损坏",
            "这可能是系统问题，请尝试重启设备或使用 Root 模式")
        error is NotRootedException -> Triple("权限不足", "设备未 Root 或无 Root 权限",
            "请确保设备已 Root 并授予应用超级用户权限")
        error is ConnectException -> Triple("无线调试未启用", "无法连接到 ADB 服务",
            "请在开发者选项中启用无线调试功能")
        error is SSLProtocolException -> Triple("配对未完成", "设备尚未完成配对",
            "使用无线调试前需要先完成配对步骤")
        else -> Triple("启动失败", error.message ?: "未知错误",
            "请展开查看完整日志以了解详细信息")
    }
}

@Composable
private fun ErrorText(errorTitle: String, errorMessage: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(errorTitle, style = MaterialTheme.typography.headlineSmall,
             fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
             textAlign = TextAlign.Center)
        Text(errorMessage, style = MaterialTheme.typography.bodyMedium,
             color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorTipCard(errorTip: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = errorTip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorActions(
    isProcessKillError: Boolean,
    needsPairing: Boolean,
    viewModel: StarterViewModel,
    onClose: () -> Unit,
    onNavigateToAdbPairing: (() -> Unit)?,
    context: android.content.Context,
    errorTitle: String,
    errorMessage: String,
    errorTip: String,
    command: String,
    outputLines: List<String>
) {
    when {
        isProcessKillError -> {
            Button(onClick = {
                if (Stellar.pingBinder()) { try { Stellar.exit() } catch (_: Throwable) {} }
                viewModel.retry()
            }, modifier = Modifier.fillMaxWidth(), shape = AppShape.shapes.buttonMedium,
               colors = ButtonDefaults.buttonColors(
                   containerColor = MaterialTheme.colorScheme.primary,
                   contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("关闭服务并重试", style = MaterialTheme.typography.titleMedium,
                     fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        needsPairing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            PairingButtons(onNavigateToAdbPairing, onClose)
        }
        else -> {
            CopyErrorButton(context, errorTitle, errorMessage, errorTip, command, outputLines)
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth(),
                          shape = AppShape.shapes.buttonMedium) {
                Text("返回", style = MaterialTheme.typography.titleMedium,
                     modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun PairingButtons(onNavigateToAdbPairing: (() -> Unit)?, onClose: () -> Unit) {
    Button(onClick = { onNavigateToAdbPairing?.invoke(); onClose() },
           modifier = Modifier.fillMaxWidth(), shape = AppShape.shapes.buttonMedium,
           colors = ButtonDefaults.buttonColors(
               containerColor = MaterialTheme.colorScheme.primary,
               contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Text("前往配对", style = MaterialTheme.typography.titleMedium,
             fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth(),
                  shape = AppShape.shapes.buttonMedium) {
        Text("返回", style = MaterialTheme.typography.titleMedium,
             modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun CopyErrorButton(
    context: android.content.Context,
    errorTitle: String,
    errorMessage: String,
    errorTip: String,
    command: String,
    outputLines: List<String>
) {
    Button(onClick = {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val logText = buildString {
            appendLine("=== Stellar 启动错误报告 ===")
            appendLine()
            appendLine("错误类型: $errorTitle")
            appendLine("错误信息: $errorMessage")
            if (errorTip.isNotEmpty()) appendLine("提示: $errorTip")
            appendLine()
            appendLine("执行命令:")
            appendLine(command)
            appendLine()
            if (outputLines.isNotEmpty()) {
                appendLine("命令输出:")
                outputLines.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("设备信息:")
            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("应用版本: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stellar 错误日志", logText))
        android.widget.Toast.makeText(context, "错误日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
    }, modifier = Modifier.fillMaxWidth(), shape = AppShape.shapes.buttonMedium,
       colors = ButtonDefaults.buttonColors(
           containerColor = MaterialTheme.colorScheme.primary,
           contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Text("复制错误日志", style = MaterialTheme.typography.titleMedium,
             fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
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

    val lastCommand: String = if (isRoot) Starter.internalCommand else "adb shell ${Starter.userCommand}"

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

    private fun setError(error: Throwable) {
        viewModelScope.launch { _state.value = StarterState.Error(error) }
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
                        setError(NotRootedException())
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
                        setError(Exception(errorMsg))
                    }
                }
            } catch (e: Exception) {
                addOutputLine("Error: ${e.message}")
                setError(e)
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
                        setError(Exception(trimmedLine.substringAfter("错误：").trim()))
                    }
                }
                if (output.contains("stellar_starter 正常退出")) waitForService()
            },
            onError = { error ->
                addOutputLine("错误：${error.message}")
                setError(error)
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
                    setError(Exception(getLastErrorMessage()))
                    return@launch
                }
            }

            if (!binderReceived) {
                Stellar.removeBinderReceivedListener(listener)
                setError(Exception("等待服务启动超时\n\n服务进程可能已崩溃，请检查设备日志"))
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
