package roro.stellar.manager.ui.features.logs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 过滤器状态
    var selectedLevels by remember { mutableStateOf(setOf("V", "D", "I", "W", "E")) }
    var searchQuery by remember { mutableStateOf("") }

    // 过滤后的日志
    val filteredLogs = remember(logs, selectedLevels, searchQuery) {
        logs.filter { log ->
            val level = when {
                log.contains(" E/") -> "E"
                log.contains(" W/") -> "W"
                log.contains(" I/") -> "I"
                log.contains(" D/") -> "D"
                log.contains(" V/") -> "V"
                else -> "?"
            }
            val levelMatch = level in selectedLevels || level == "?"
            val searchMatch = searchQuery.isEmpty() ||
                log.contains(searchQuery, ignoreCase = true)
            levelMatch && searchMatch
        }
    }

    fun loadLogs() {
        if (!Stellar.pingBinder()) {
            Toast.makeText(context, "服务未运行", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    Stellar.getLogs()
                }
                logs = result.reversed()
            } catch (e: Exception) {
                Toast.makeText(context, "加载日志失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun clearLogs() {
        if (!Stellar.pingBinder()) {
            Toast.makeText(context, "服务未运行", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Stellar.clearLogs()
                }
                logs = emptyList()
                Toast.makeText(context, "日志已清除", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "清除日志失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyLogs() {
        if (logs.isEmpty()) {
            Toast.makeText(context, "没有日志可复制", Toast.LENGTH_SHORT).show()
            return
        }
        val logsText = logs.reversed().joinToString("\n")
        if (ClipboardUtils.put(context, logsText)) {
            Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务日志") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { copyLogs() }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制日志",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { clearLogs() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "清除日志",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { loadLogs() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        text = "搜索日志...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // 过滤器栏
            LogFilterBar(
                selectedLevels = selectedLevels,
                onLevelToggle = { level ->
                    selectedLevels = if (level in selectedLevels) {
                        selectedLevels - level
                    } else {
                        selectedLevels + level
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (filteredLogs.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无日志",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "日志将在服务运行时显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { log ->
                        LogItem(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: String) {
    val level = parseLogLevel(log)
    val parts = parseLogEntry(log)
    val levelColor = getLevelColor(level)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 第一行：级别指示器 + Tag + 时间
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 级别圆点指示器
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(levelColor)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Tag
            Text(
                text = parts.tag,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 时间戳
            if (parts.timestamp.isNotEmpty()) {
                Text(
                    text = parts.timestamp.substringAfter(" ").substringBefore("."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 第二行：日志消息
        Text(
            text = parts.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

private data class LogParts(
    val timestamp: String,
    val tag: String,
    val message: String
)

private fun parseLogLevel(log: String): String = when {
    log.contains(" E/") -> "E"
    log.contains(" W/") -> "W"
    log.contains(" I/") -> "I"
    log.contains(" D/") -> "D"
    log.contains(" V/") -> "V"
    else -> "?"
}

@Composable
private fun getLevelColor(level: String): Color = when (level) {
    "E" -> MaterialTheme.colorScheme.error
    "W" -> Color(0xFFFF9800)
    "I" -> MaterialTheme.colorScheme.primary
    "D" -> Color(0xFF4CAF50)
    "V" -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun parseLogEntry(log: String): LogParts {
    // 格式: "MM-dd HH:mm:ss.SSS L/Tag: Message"
    val regex = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) [VDIWEA]/([^:]+): (.*)$""")
    val match = regex.find(log)

    return if (match != null) {
        val (timestamp, tag, message) = match.destructured
        LogParts(timestamp, tag, message)
    } else {
        // 尝试简单分割
        val colonIndex = log.indexOf(": ")
        if (colonIndex > 0) {
            LogParts(
                timestamp = "",
                tag = log.substring(0, colonIndex.coerceAtMost(20)),
                message = log.substring((colonIndex + 2).coerceAtMost(log.length))
            )
        } else {
            LogParts("", "Log", log)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogFilterBar(
    selectedLevels: Set<String>,
    onLevelToggle: (String) -> Unit
) {
    val levels = listOf(
        "V" to "Verbose",
        "D" to "Debug",
        "I" to "Info",
        "W" to "Warn",
        "E" to "Error"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        levels.forEach { (level, name) ->
            val isSelected = level in selectedLevels
            val levelColor = getLevelColor(level)

            FilterChip(
                selected = isSelected,
                onClick = { onLevelToggle(level) },
                label = {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(levelColor)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = levelColor.copy(alpha = 0.12f),
                    selectedLabelColor = levelColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = levelColor.copy(alpha = 0.5f)
                )
            )
        }
    }
}
