package roro.stellar.manager.ui.features.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import java.io.BufferedReader
import java.io.InputStreamReader

data class ExecutionResult(
    val command: String,
    val output: String,
    val exitCode: Int,
    val executionTimeMs: Long,
    val isError: Boolean = false
)

data class TerminalState(
    val isRunning: Boolean = false,
    val currentOutput: String = "",
    val showResultDialog: Boolean = false,
    val result: ExecutionResult? = null
)

class TerminalViewModel : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private var currentJob: Job? = null

    fun executeCommand(command: String) {
        if (command.isBlank() || _state.value.isRunning) return
        if (!Stellar.pingBinder()) return

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isRunning = true,
                currentOutput = "",
                showResultDialog = true
            )

            try {
                val process = Stellar.newProcess(
                    arrayOf("sh", "-c", command),
                    null,
                    null
                )

                val outputLines = mutableListOf<String>()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                val maxStoredLines = 5000
                val maxDisplayLines = 200
                val updateInterval = 500L
                var lastUpdateTime = System.currentTimeMillis()

                reader.lineSequence().forEach { line ->
                    outputLines.add(line)

                    if (outputLines.size > maxStoredLines) {
                        outputLines.removeAt(0)
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= updateInterval) {
                        val displayLines = outputLines.takeLast(maxDisplayLines)
                        val displayText = if (outputLines.size > maxDisplayLines) {
                            "... (${outputLines.size} lines total, showing last $maxDisplayLines)\n" + displayLines.joinToString("\n")
                        } else {
                            displayLines.joinToString("\n")
                        }
                        _state.value = _state.value.copy(currentOutput = displayText)
                        lastUpdateTime = now
                    }
                }

                errorReader.lineSequence().forEach { line ->
                    outputLines.add(line)

                    if (outputLines.size > maxStoredLines) {
                        outputLines.removeAt(0)
                    }
                }

                val exitCode = process.waitFor()
                process.destroy()
                val executionTime = System.currentTimeMillis() - startTime

                val finalOutput = if (outputLines.size > maxStoredLines) {
                    "... (${outputLines.size} lines total, showing last $maxStoredLines)\n" + outputLines.takeLast(maxStoredLines).joinToString("\n")
                } else {
                    outputLines.joinToString("\n")
                }

                _state.value = _state.value.copy(
                    isRunning = false,
                    result = ExecutionResult(
                        command = command,
                        output = finalOutput,
                        exitCode = exitCode,
                        executionTimeMs = executionTime,
                        isError = exitCode != 0
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                _state.value = _state.value.copy(
                    isRunning = false,
                    result = ExecutionResult(
                        command = command,
                        output = "Error: ${e.message}",
                        exitCode = -1,
                        executionTimeMs = executionTime,
                        isError = true
                    )
                )
            }
        }
    }

    fun dismissDialog() {
        _state.value = _state.value.copy(showResultDialog = false, result = null)
    }
}
