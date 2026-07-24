package roro.stellar.manager.ui.features.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private var currentProcess: Process? = null

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
                currentProcess = process

                val outputLines = mutableListOf<String>()
                val outputLock = Any()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                val maxStoredLines = 5000
                val maxDisplayLines = 200
                var updateInterval = 50L
                var lastUpdateTime = System.currentTimeMillis()
                var isHighFrequency = false

                fun appendOutputLine(line: String) {
                    val displayText = synchronized(outputLock) {
                        outputLines.add(line)

                        if (outputLines.size > maxStoredLines) {
                            outputLines.removeAt(0)
                        }

                        if (!isHighFrequency && outputLines.size >= 100) {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed < 1000) {
                                isHighFrequency = true
                                updateInterval = 500L
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= updateInterval) {
                            val displayLines = outputLines.takeLast(maxDisplayLines)
                            lastUpdateTime = now
                            if (outputLines.size > maxDisplayLines) {
                                "... (${outputLines.size} lines total, showing last $maxDisplayLines)\n" + displayLines.joinToString("\n")
                            } else {
                                displayLines.joinToString("\n")
                            }
                        } else {
                            null
                        }
                    }
                    if (displayText != null) {
                        _state.value = _state.value.copy(currentOutput = displayText)
                    }
                }

                val exitCode = coroutineScope {
                    val stdoutJob = async(Dispatchers.IO) {
                        reader.lineSequence().forEach(::appendOutputLine)
                    }
                    val stderrJob = async(Dispatchers.IO) {
                        errorReader.lineSequence().forEach(::appendOutputLine)
                    }
                    val code = process.waitFor()
                    stdoutJob.await()
                    stderrJob.await()
                    code
                }
                process.destroy()
                currentProcess = null
                val executionTime = System.currentTimeMillis() - startTime

                val finalOutput = synchronized(outputLock) {
                    if (outputLines.size > maxStoredLines) {
                        "... (${outputLines.size} lines total, showing last $maxStoredLines)\n" + outputLines.takeLast(maxStoredLines).joinToString("\n")
                    } else {
                        outputLines.joinToString("\n")
                    }
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
                currentProcess?.destroy()
                currentProcess = null
                val executionTime = System.currentTimeMillis() - startTime
                _state.value = _state.value.copy(
                    isRunning = false,
                    result = ExecutionResult(
                        command = command,
                        output = _state.value.currentOutput + "\n\n[Interrupted]",
                        exitCode = -1,
                        executionTimeMs = executionTime,
                        isError = true
                    )
                )
                throw e
            } catch (e: Exception) {
                currentProcess?.destroy()
                currentProcess = null
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

    fun cancelExecution() {
        currentJob?.cancel()
        currentProcess?.destroy()
        currentProcess = null
    }
}
