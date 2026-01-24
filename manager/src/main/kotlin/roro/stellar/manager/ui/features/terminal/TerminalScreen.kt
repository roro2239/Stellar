package roro.stellar.manager.ui.features.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.ui.navigation.components.StandardLargeTopAppBar
import roro.stellar.manager.ui.navigation.components.createTopAppBarScrollBehavior
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    topAppBarState: TopAppBarState,
    terminalViewModel: TerminalViewModel = viewModel()
) {
    val scrollBehavior = createTopAppBarScrollBehavior(topAppBarState)
    val state by terminalViewModel.state.collectAsState()
    var commandInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StandardLargeTopAppBar(
                title = "命令",
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = AppSpacing.screenHorizontalPadding)
                .padding(top = AppSpacing.topBarContentSpacing)
                .verticalScroll(rememberScrollState())
        ) {
            CommandInputCard(
                command = commandInput,
                onCommandChange = { commandInput = it },
                onExecute = {
                    if (commandInput.isNotBlank()) {
                        terminalViewModel.executeCommand(commandInput)
                        commandInput = ""
                    }
                },
                isRunning = state.isRunning
            )

            Spacer(modifier = Modifier.height(16.dp))

            PresetCommandsCard(
                onExecuteCommand = { command ->
                    terminalViewModel.executeCommand(command)
                },
                isRunning = state.isRunning
            )
        }
    }

    if (state.showResultDialog) {
        ExecutionResultDialog(
            state = state,
            onDismiss = { terminalViewModel.dismissDialog() }
        )
    }
}

@Composable
private fun CommandInputCard(
    command: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
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
                .padding(20.dp)
        ) {
            Text(
                text = "输入命令",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入命令") },
                shape = AppShape.shapes.inputField,
                enabled = !isRunning,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onExecute() })
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onExecute,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShape.shapes.buttonMedium,
                enabled = command.isNotBlank() && !isRunning
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("执行")
            }
        }
    }
}

@Composable
private fun PresetCommandsCard(
    onExecuteCommand: (String) -> Unit,
    isRunning: Boolean
) {
    var bootCommand by remember {
        mutableStateOf(
            StellarSettings.getPreferences().getString(StellarSettings.BOOT_COMMAND, "") ?: ""
        )
    }
    var followCommand by remember {
        mutableStateOf(
            StellarSettings.getPreferences().getString(StellarSettings.FOLLOW_STARTUP_COMMAND, "") ?: ""
        )
    }

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
                .padding(20.dp)
        ) {
            Text(
                text = "自定义命令",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            CustomCommandItem(
                icon = Icons.Outlined.PowerSettingsNew,
                title = "开机启动命令",
                command = bootCommand,
                onCommandChange = { bootCommand = it },
                onSave = {
                    StellarSettings.getPreferences().edit()
                        .putString(StellarSettings.BOOT_COMMAND, bootCommand).commit()
                },
                onExecute = { onExecuteCommand(bootCommand) },
                enabled = !isRunning
            )

            Spacer(modifier = Modifier.height(12.dp))

            CustomCommandItem(
                icon = Icons.Outlined.Sync,
                title = "跟随启动命令",
                command = followCommand,
                onCommandChange = { followCommand = it },
                onSave = {
                    StellarSettings.getPreferences().edit()
                        .putString(StellarSettings.FOLLOW_STARTUP_COMMAND, followCommand).commit()
                },
                onExecute = { onExecuteCommand(followCommand) },
                enabled = !isRunning
            )
        }
    }
}

@Composable
private fun CustomCommandItem(
    icon: ImageVector,
    title: String,
    command: String,
    onCommandChange: (String) -> Unit,
    onSave: () -> Unit,
    onExecute: () -> Unit,
    enabled: Boolean
) {
    var showSavedHint by remember { mutableStateOf(false) }

    LaunchedEffect(showSavedHint) {
        if (showSavedHint) {
            kotlinx.coroutines.delay(1500)
            showSavedHint = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShape.shapes.cardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (showSavedHint) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入命令") },
                shape = AppShape.shapes.inputField,
                enabled = enabled,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSave()
                        showSavedHint = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = AppShape.shapes.buttonMedium,
                    enabled = enabled
                ) {
                    Text("保存")
                }
                Button(
                    onClick = onExecute,
                    modifier = Modifier.weight(1f),
                    shape = AppShape.shapes.buttonMedium,
                    enabled = command.isNotBlank() && enabled
                ) {
                    Text("执行")
                }
            }
        }
    }
}

@Composable
private fun ExecutionResultDialog(
    state: TerminalState,
    onDismiss: () -> Unit
) {
    val result = state.result

    AlertDialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = if (state.isRunning) "执行中" else "执行结果")
                if (state.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    shape = AppShape.shapes.cardMedium
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
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isRunning
            ) {
                Text(if (state.isRunning) "执行中..." else "关闭")
            }
        }
    )
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
