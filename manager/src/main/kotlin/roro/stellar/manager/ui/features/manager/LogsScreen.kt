package roro.stellar.manager.ui.features.manager

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import roro.stellar.manager.R
import roro.stellar.Stellar
import roro.stellar.manager.compat.ClipboardUtils
import roro.stellar.manager.ui.navigation.components.FixedTopAppBar
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing

@SuppressLint("LocalContextGetResourceValueCall", "StringFormatInvalid")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedLevels by remember { mutableStateOf(setOf("V", "D", "I", "W", "E")) }
    var searchQuery by remember { mutableStateOf("") }

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

    val loadLogs: () -> Unit = {
        if (!Stellar.pingBinder()) {
            Toast.makeText(context, context.getString(R.string.service_not_running_toast), Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                isLoading = true
                try {
                    val result = withContext(Dispatchers.IO) {
                        Stellar.getLogs()
                    }
                    logs = result.reversed()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMessage = context.getString(R.string.load_logs_failed, e.message ?: "")
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val clearLogs: () -> Unit = {
        if (!Stellar.pingBinder()) {
            Toast.makeText(context, context.getString(R.string.service_not_running_toast), Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        Stellar.clearLogs()
                    }
                    logs = emptyList()
                    Toast.makeText(context, context.getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    val errorMessage = context.getString(R.string.clear_logs_failed, e.message ?: "")
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val copyLogs: () -> Unit = {
        if (logs.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.no_logs_to_copy), Toast.LENGTH_SHORT).show()
        } else {
            val logsText = logs.reversed().joinToString("\n")
            if (ClipboardUtils.put(context, logsText)) {
                Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LogsTopBar(
                onBackClick = onBackClick,
                onCopyLogs = { copyLogs() },
                onClearLogs = { clearLogs() },
                onRefresh = { loadLogs() }
            )
        }
    ) { paddingValues ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .padding(start = AppSpacing.screenHorizontalPadding, top = 8.dp, bottom = 8.dp)
                ) {
                    SearchBar(searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    LogFilterBarVertical(selectedLevels = selectedLevels, onLevelToggle = { level ->
                        selectedLevels = if (level in selectedLevels) {
                            selectedLevels - level
                        } else {
                            selectedLevels + level
                        }
                    })
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    if (filteredLogs.isEmpty() && !isLoading) {
                        EmptyLogsView()
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(filteredLogs) { log -> LogItem(log = log) }
                        }
                    }
                }
            }
        } else {
            LogsContent(
                paddingValues = paddingValues,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedLevels = selectedLevels,
                onLevelToggle = { level ->
                    selectedLevels = if (level in selectedLevels) {
                        selectedLevels - level
                    } else {
                        selectedLevels + level
                    }
                },
                filteredLogs = filteredLogs,
                isLoading = isLoading,
                listState = listState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsTopBar(
    onBackClick: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onRefresh: () -> Unit
) {
    FixedTopAppBar(
        title = stringResource(R.string.service_logs),
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            LogsTopBarActions(onCopyLogs, onClearLogs, onRefresh)
        }
    )
}

@Composable
private fun LogsTopBarActions(
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onRefresh: () -> Unit
) {
    Row {
        Surface(
            onClick = onCopyLogs,
            shape = AppShape.shapes.iconSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.copy_logs),
                modifier = Modifier.padding(8.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            onClick = onClearLogs,
            shape = AppShape.shapes.iconSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = stringResource(R.string.clear_logs),
                modifier = Modifier.padding(8.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            onClick = onRefresh,
            shape = AppShape.shapes.iconSmall,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.refresh),
                modifier = Modifier.padding(8.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun LogsContent(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedLevels: Set<String>,
    onLevelToggle: (String) -> Unit,
    filteredLogs: List<String>,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        SearchBar(searchQuery = searchQuery, onSearchQueryChange = onSearchQueryChange)
        LogFilterBar(selectedLevels = selectedLevels, onLevelToggle = onLevelToggle)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (filteredLogs.isEmpty() && !isLoading) {
            EmptyLogsView()
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filteredLogs) { log -> LogItem(log = log) }
            }
        }
    }
}

@Composable
private fun SearchBar(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenHorizontalPadding, vertical = 8.dp),
        shape = AppShape.shapes.inputField,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacing.screenHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = AppShape.shapes.cardLarge,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_logs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.logs_will_show),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogFilterBar(
    selectedLevels: Set<String>,
    onLevelToggle: (String) -> Unit
) {
    val levels = listOf("V" to "Verbose", "D" to "Debug", "I" to "Info", "W" to "Warn", "E" to "Error")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontalPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        levels.forEach { (level, name) ->
            val isSelected = level in selectedLevels
            val levelColor = getLevelColor(level)

            Surface(
                onClick = { onLevelToggle(level) },
                shape = AppShape.shapes.buttonSmall14,
                color = if (isSelected) levelColor.copy(alpha = 0.12f)
                       else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(AppShape.shapes.iconSmall)
                            .background(if (isSelected) levelColor else levelColor.copy(alpha = 0.4f))
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) levelColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LogFilterBarVertical(
    selectedLevels: Set<String>,
    onLevelToggle: (String) -> Unit
) {
    val levels = listOf("V" to "Verbose", "D" to "Debug", "I" to "Info", "W" to "Warn", "E" to "Error")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        levels.forEach { (level, name) ->
            val isSelected = level in selectedLevels
            val levelColor = getLevelColor(level)

            Surface(
                onClick = { onLevelToggle(level) },
                shape = AppShape.shapes.buttonSmall14,
                color = if (isSelected) levelColor.copy(alpha = 0.12f)
                       else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(AppShape.shapes.iconSmall)
                            .background(if (isSelected) levelColor else levelColor.copy(alpha = 0.4f))
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) levelColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenHorizontalPadding, vertical = 4.dp),
        shape = AppShape.shapes.cardMedium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(AppShape.shapes.iconSmall)
                    .background(levelColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = parts.tag,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (parts.timestamp.isNotEmpty()) {
                        Text(
                            text = parts.timestamp.substringAfter(" ").substringBefore("."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = parts.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class LogParts(val timestamp: String, val tag: String, val message: String)

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
    val regex = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) [VDIWEA]/([^:]+): (.*)$""")
    val match = regex.find(log)

    return if (match != null) {
        val (timestamp, tag, message) = match.destructured
        LogParts(timestamp, tag, message)
    } else {
        val colonIndex = log.indexOf(": ")
        if (colonIndex > 0) {
            LogParts("", log.substring(0, colonIndex.coerceAtMost(20)),
                     log.substring((colonIndex + 2).coerceAtMost(log.length)))
        } else {
            LogParts("", "Log", log)
        }
    }
}
