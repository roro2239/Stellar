package roro.stellar.manager.ui.features.starter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants.EXTRA
import roro.stellar.manager.adb.AdbKeyException
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.ui.features.home.others.AdbPairingTutorialActivity
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.StellarTheme
import java.net.ConnectException
import javax.net.ssl.SSLProtocolException

private class NotRootedException : Exception()

sealed class StarterState {
    data class Loading(val command: String, val isSuccess: Boolean = false) : StarterState()
    data class Error(val error: Throwable) : StarterState()
}

class StarterActivity : ComponentActivity() {

    private val viewModel by viewModels<StarterViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StarterViewModel(
                    isRoot = intent.getBooleanExtra(EXTRA_IS_ROOT, true),
                    host = intent.getStringExtra(EXTRA_HOST),
                    port = intent.getIntExtra(EXTRA_PORT, 0)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StellarTheme {
                StarterScreen(
                    viewModel = viewModel,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_HOST = "$EXTRA.HOST"
        const val EXTRA_PORT = "$EXTRA.PORT"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterScreen(
    viewModel: StarterViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is StarterState.Loading && (state as StarterState.Loading).isSuccess) {
            delay(3000)
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stellar 启动器") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                        onClose = onClose
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingView(
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
            .padding(horizontal = 24.dp)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Surface(
            shape = AppShape.shapes.iconLarge,
            color = if (isSuccess) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            },
            modifier = Modifier.size(140.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isSuccess) {
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
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(100.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(70.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

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

            if (isSuccess) {
                Text(
                    text = "Stellar 服务已成功启动",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "请稍候片刻...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = AppShape.shapes.cardMedium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = AppShape.shapes.iconSmall
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "启动命令",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = AppShape.shapes.iconSmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (isExpanded && outputLines.isNotEmpty()) {
                    HorizontalDivider()

                    Surface(
                        shape = AppShape.shapes.iconSmall,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            outputLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isSuccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.shapes.cardMedium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (countdown > 0) {
                        Text(
                            text = "$countdown",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "秒后自动关闭",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "正在关闭...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorView(
    error: Throwable,
    command: String,
    outputLines: List<String>,
    viewModel: StarterViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val needsPairing = error is SSLProtocolException || error is ConnectException

    val isProcessKillError = error.message?.contains("无法终止进程") == true ||
                             error.message?.contains("停止现有服务") == true

    val (errorTitle, errorMessage, errorTip) = when {
        isProcessKillError -> Triple(
            "服务已在运行",
            "无法终止现有进程",
            ""
        )
        error is AdbKeyException -> Triple(
            "KeyStore 错误",
            "设备的 KeyStore 机制已损坏",
            "这可能是系统问题，请尝试重启设备或使用 Root 模式"
        )
        error is NotRootedException -> Triple(
            "权限不足",
            "设备未 Root 或无 Root 权限",
            "请确保设备已 Root 并授予应用超级用户权限"
        )
        error is ConnectException -> Triple(
            "无线调试未启用",
            "无法连接到 ADB 服务",
            "请在开发者选项中启用无线调试功能"
        )
        error is SSLProtocolException -> Triple(
            "配对未完成",
            "设备尚未完成配对",
            "使用无线调试前需要先完成配对步骤"
        )
        else -> Triple(
            "启动失败",
            error.message ?: "未知错误",
            "请展开查看完整日志以了解详细信息"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Surface(
            shape = AppShape.shapes.iconLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(140.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
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
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = errorTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = AppShape.shapes.cardMedium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = AppShape.shapes.iconSmall
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "启动命令",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = AppShape.shapes.iconSmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (isExpanded && outputLines.isNotEmpty()) {
                    HorizontalDivider()

                    Surface(
                        shape = AppShape.shapes.iconSmall,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            outputLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (errorTip.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.shapes.cardMedium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

        when {
            isProcessKillError -> {
                Button(
                    onClick = {
                        if (Stellar.pingBinder()) {
                            try {
                                Stellar.exit()
                            } catch (_: Throwable) {
                            }
                        }
                        viewModel.retry()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.buttonMedium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "关闭服务并重试",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            needsPairing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, AdbPairingTutorialActivity::class.java)
                        )
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.buttonMedium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "前往配对",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.buttonMedium
                ) {
                    Text(
                        text = "返回",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            else -> {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val logText = buildString {
                            appendLine("=== Stellar 启动错误报告 ===")
                            appendLine()
                            appendLine("错误类型: $errorTitle")
                            appendLine("错误信息: $errorMessage")
                            if (errorTip.isNotEmpty()) {
                                appendLine("提示: $errorTip")
                            }
                            appendLine()
                            appendLine("执行命令:")
                            appendLine(command)
                            appendLine()
                            if (outputLines.isNotEmpty()) {
                                appendLine("命令输出:")
                                outputLines.forEach { line ->
                                    appendLine(line)
                                }
                            }
                            appendLine()
                            appendLine("设备信息:")
                            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            appendLine("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}")
                            appendLine("应用版本: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
                        }
                        val clip = android.content.ClipData.newPlainText("Stellar 错误日志", logText)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "错误日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.buttonMedium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "复制错误日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShape.shapes.buttonMedium
                ) {
                    Text(
                        text = "返回",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

class StarterViewModel(
    private val isRoot: Boolean,
    private val host: String?,
    private val port: Int
) : ViewModel() {

    private val _state = MutableStateFlow<StarterState>(
        StarterState.Loading(
            command = when {
                isRoot -> Starter.internalCommand
                else -> "adb shell ${Starter.userCommand}"
            }
        )
    )
    val state: StateFlow<StarterState> = _state.asStateFlow()

    private val _outputLines = MutableStateFlow<List<String>>(emptyList())
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    val lastCommand: String = when {
        isRoot -> Starter.internalCommand
        else -> "adb shell ${Starter.userCommand}"
    }

    private val adbWirelessHelper = AdbWirelessHelper()

    init {
        startService()
    }

    private fun addOutputLine(line: String) {
        viewModelScope.launch {
            _outputLines.value = _outputLines.value + line
        }
    }

    private fun setSuccess() {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState is StarterState.Loading) {
                _state.value = currentState.copy(isSuccess = true)
            }
        }
    }

    private fun setError(error: Throwable) {
        viewModelScope.launch {
            _state.value = StarterState.Error(error)
        }
    }

    fun retry() {
        viewModelScope.launch {
            _state.value = StarterState.Loading(
                command = when {
                    isRoot -> Starter.internalCommand
                    else -> "adb shell ${Starter.userCommand}"
                }
            )
            _outputLines.value = emptyList()

            delay(500)

            startService()
        }
    }

    private fun startService() {
        when {
            isRoot -> startRoot()
            else -> startAdb(host!!, port)
        }
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

                            if (it.contains("stellar_starter 正常退出")) {
                                waitForService()
                            }
                        }
                    }
                }).submit { result ->
                    if (result.code != 0) {
                        val errorMsg = when (result.code) {
                            9 -> "无法终止进程，请先从应用中停止现有服务"  // EXIT_FATAL_KILL
                            3 -> "无法设置 CLASSPATH"  // EXIT_FATAL_SET_CLASSPATH
                            4 -> "无法创建进程"  // EXIT_FATAL_FORK
                            5 -> "app_process 执行失败"  // EXIT_FATAL_APP_PROCESS
                            6 -> "权限不足，需要 root 或 adb 权限"  // EXIT_FATAL_UID
                            7 -> "无法获取应用路径"  // EXIT_FATAL_PM_PATH
                            10 -> "SELinux 阻止了应用通过 binder 连接"  // EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX
                            else -> "启动失败，退出码: ${result.code}"
                        }
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

    private fun startAdb(host: String, port: Int) {
        addOutputLine("Connecting to $host:$port...")

        adbWirelessHelper.startStellarViaAdb(
            host = host,
            port = port,
            coroutineScope = viewModelScope,
            onOutput = { output ->
                addOutputLine(output)

                output.lines().forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("错误：")) {
                        val errorMsg = trimmedLine.substringAfter("错误：").trim()
                        setError(Exception(errorMsg))
                    }
                }

                if (output.contains("stellar_starter 正常退出")) {
                    waitForService()
                }
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

                if (_outputLines.value.any { line ->
                    line.contains("错误：") ||
                    line.contains("Error:") ||
                    line.contains("Exception") ||
                    line.contains("FATAL")
                }) {
                    Stellar.removeBinderReceivedListener(listener)
                    val errorLines = _outputLines.value.filter {
                        it.contains("错误：") || it.contains("Error:")
                    }
                    val errorMsg = if (errorLines.isNotEmpty()) {
                        errorLines.last().substringAfter("错误：").substringAfter("Error:").trim()
                    } else {
                        "服务启动失败，请查看日志了解详情"
                    }
                    setError(Exception(errorMsg))
                    return@launch
                }
            }

            if (!binderReceived) {
                Stellar.removeBinderReceivedListener(listener)
                setError(Exception("等待服务启动超时\n\n服务进程可能已崩溃，请检查设备日志"))
            }
        }
    }
}