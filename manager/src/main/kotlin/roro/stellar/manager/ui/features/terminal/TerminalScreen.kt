package roro.stellar.manager.ui.features.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.ui.components.StellarDialog
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import java.util.UUID

enum class CommandMode(val title: String, val icon: ImageVector, val description: String) {
    CLICK_EXECUTE("点击执行", Icons.Outlined.PlayArrow, "手动点击执行命令"),
    FOLLOW_SERVICE("跟随服务", Icons.Outlined.Sync, "服务启动时自动执行")
}

data class CommandItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val command: String,
    val mode: CommandMode
)

private const val COMMANDS_KEY = "saved_commands"

private fun loadCommands(): List<CommandItem> {
    val prefs = StellarSettings.getPreferences()
    val json = prefs.getString(COMMANDS_KEY, "[]") ?: "[]"
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            CommandItem(
                id = obj.getString("id"),
                title = obj.getString("title"),
                command = obj.getString("command"),
                mode = CommandMode.valueOf(obj.getString("mode"))
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveCommands(commands: List<CommandItem>) {
    val array = JSONArray()
    commands.forEach { cmd ->
        array.put(JSONObject().apply {
            put("id", cmd.id)
            put("title", cmd.title)
            put("command", cmd.command)
            put("mode", cmd.mode.name)
        })
    }
    StellarSettings.getPreferences().edit()
        .putString(COMMANDS_KEY, array.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    topAppBarState: TopAppBarState,
    terminalViewModel: TerminalViewModel = viewModel()
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val state by terminalViewModel.state.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showQuickExecuteDialog by remember { mutableStateOf(false) }
    var editingCommand by remember { mutableStateOf<CommandItem?>(null) }
    var commands by remember { mutableStateOf(loadCommands()) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StandardLargeTopAppBar(
                title = "命令",
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickExecuteDialog = true },
                shape = AppShape.shapes.cardMedium
            ) {
                Icon(Icons.Outlined.Terminal, contentDescription = "快速执行")
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + AppSpacing.topBarContentSpacing,
                bottom = 96.dp,
                start = AppSpacing.screenHorizontalPadding,
                end = AppSpacing.screenHorizontalPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(commands, key = { it.id }) { cmd ->
                CommandCard(
                    item = cmd,
                    onExecute = { terminalViewModel.executeCommand(cmd.command) },
                    onEdit = { editingCommand = cmd },
                    onDelete = {
                        commands = commands.filter { it.id != cmd.id }
                        saveCommands(commands)
                    },
                    isRunning = state.isRunning
                )
            }
            item(key = "add_card") {
                AddCommandCard(onClick = { showCreateDialog = true })
            }
        }
    }

    if (showCreateDialog) {
        CreateCommandDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, command, mode ->
                val newCommand = CommandItem(
                    title = title,
                    command = command,
                    mode = mode
                )
                commands = commands + newCommand
                saveCommands(commands)
                if (mode == CommandMode.CLICK_EXECUTE) {
                    terminalViewModel.executeCommand(command)
                }
                showCreateDialog = false
            }
        )
    }

    editingCommand?.let { item ->
        EditCommandDialog(
            item = item,
            onDismiss = { editingCommand = null },
            onConfirm = { title, command ->
                commands = commands.map {
                    if (it.id == item.id) it.copy(title = title, command = command) else it
                }
                saveCommands(commands)
                editingCommand = null
            }
        )
    }

    if (showQuickExecuteDialog) {
        QuickExecuteDialog(
            onDismiss = { showQuickExecuteDialog = false },
            onExecute = { command ->
                terminalViewModel.executeCommand(command)
                showQuickExecuteDialog = false
            }
        )
    }

    if (state.showResultDialog) {
        ExecutionResultDialog(
            state = state,
            onDismiss = { terminalViewModel.dismissDialog() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandCard(
    item: CommandItem,
    onExecute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isRunning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppShape.shapes.iconSmall)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.mode.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp),
                shape = AppShape.shapes.cardMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = item.command,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onExecute,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    enabled = !isRunning,
                    shape = AppShape.shapes.buttonMedium,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "执行",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp),
                    shape = AppShape.shapes.buttonMedium
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                    shape = AppShape.shapes.buttonMedium,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCommandCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppShape.shapes.iconSmall)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "添加命令",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp),
                shape = AppShape.shapes.cardMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {}

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.height(36.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCommandDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, CommandMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(CommandMode.CLICK_EXECUTE) }
    var title by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    StellarDialog(
        onDismissRequest = onDismiss,
        title = "创建命令",
        confirmText = if (selectedMode == CommandMode.CLICK_EXECUTE) "执行" else "保存",
        confirmEnabled = command.isNotBlank(),
        onConfirm = { onConfirm(title.ifBlank { command.take(20) }, command, selectedMode) },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.dialogContentSpacing)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                placeholder = { Text("命令名称") },
                shape = AppShape.shapes.inputField,
                singleLine = true
            )

            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("命令") },
                placeholder = { Text("输入要执行的命令") },
                shape = AppShape.shapes.inputField,
                singleLine = false,
                maxLines = 3,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )

            Text(
                text = "选择模式",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandMode.entries.forEach { mode ->
                    ModeSelectionItem(
                        mode = mode,
                        selected = selectedMode == mode,
                        enabled = true,
                        onClick = { selectedMode = mode }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSelectionItem(
    mode: CommandMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardMedium,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            if (selected) {
                RadioButton(selected = true, onClick = null)
            }
        }
    }
}

@Composable
private fun EditCommandDialog(
    item: CommandItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var command by remember { mutableStateOf(item.command) }

    StellarDialog(
        onDismissRequest = onDismiss,
        title = "编辑命令",
        confirmText = "保存",
        confirmEnabled = command.isNotBlank(),
        onConfirm = { onConfirm(title, command) },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.dialogContentSpacing)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.shapes.dialogContent,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.mode.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.mode.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                shape = AppShape.shapes.inputField,
                singleLine = true
            )

            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("命令") },
                shape = AppShape.shapes.inputField,
                singleLine = false,
                maxLines = 3,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Composable
private fun QuickExecuteDialog(
    onDismiss: () -> Unit,
    onExecute: (String) -> Unit
) {
    var command by remember { mutableStateOf("") }

    StellarDialog(
        onDismissRequest = onDismiss,
        title = "快速执行",
        confirmText = "执行",
        confirmEnabled = command.isNotBlank(),
        onConfirm = { onExecute(command) },
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("命令") },
            placeholder = { Text("输入要执行的命令") },
            shape = AppShape.shapes.inputField,
            singleLine = false,
            maxLines = 3,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecutionResultDialog(
    state: TerminalState,
    onDismiss: () -> Unit
) {
    val result = state.result

    BasicAlertDialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() }
    ) {
        Surface(
            shape = AppShape.shapes.dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.dialogPadding)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (state.isRunning) "执行中" else "执行结果",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.dialogContentSpacing)
                ) {
                    if (result != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoChip(label = "返回值", value = result.exitCode.toString(), isError = result.isError)
                            InfoChip(label = "耗时", value = "${result.executionTimeMs}ms", isError = result.isError)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = AppShape.shapes.dialogContent
                    ) {
                        val scrollState = rememberScrollState()
                        val output = if (state.isRunning) {
                            state.currentOutput.ifEmpty { "执行中..." }
                        } else {
                            result?.output ?: ""
                        }
                        Text(
                            text = output,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isRunning
                    ) {
                        Text(if (state.isRunning) "执行中..." else "关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, isError: Boolean = false) {
    val backgroundColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = AppShape.shapes.iconSmall,
        color = backgroundColor
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}
