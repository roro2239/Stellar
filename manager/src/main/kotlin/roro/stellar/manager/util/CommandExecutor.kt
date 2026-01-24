package roro.stellar.manager.util

import android.util.Log
import org.json.JSONArray
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.StellarSettings


enum class CommandMode {
    CLICK_EXECUTE,
    AUTO_START,
    FOLLOW_SERVICE
}

data class CommandItem(
    val id: String,
    val title: String,
    val command: String,
    val mode: CommandMode
)

object CommandExecutor {
    private const val COMMANDS_KEY = "saved_commands"

    fun loadCommands(): List<CommandItem> {
        val json = StellarSettings.getPreferences().getString(COMMANDS_KEY, "[]") ?: "[]"
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
            Log.e(AppConstants.TAG, "加载命令失败", e)
            emptyList()
        }
    }

    fun getCommandsByMode(mode: CommandMode): List<CommandItem> {
        return loadCommands().filter { it.mode == mode }
    }

    fun executeCommandsByMode(mode: CommandMode) {
        val commands = getCommandsByMode(mode)
        if (commands.isEmpty()) {
            Log.i(AppConstants.TAG, "没有 ${mode.name} 模式的命令需要执行")
            return
        }

        Log.i(AppConstants.TAG, "开始执行 ${mode.name} 模式的 ${commands.size} 个命令")
        commands.forEach { cmd ->
            executeCommand(cmd)
        }
    }

    private fun executeCommand(cmd: CommandItem) {
        try {
            Log.i(AppConstants.TAG, "执行命令: ${cmd.title}")

            if (Stellar.pingBinder()) {
                val process = Stellar.newProcess(arrayOf("sh", "-c", cmd.command), null, null)
                val exitCode = process.waitFor()
                Log.i(AppConstants.TAG, "命令执行完成: ${cmd.title}, 退出码: $exitCode")
            } else {
                Log.w(AppConstants.TAG, "Stellar 服务不可用，跳过命令: ${cmd.title}")
            }
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "命令执行失败: ${cmd.title}", e)
        }
    }

    fun executeAutoStartCommands() {
        executeCommandsByMode(CommandMode.AUTO_START)
    }

    fun executeFollowServiceCommands() {
        executeCommandsByMode(CommandMode.FOLLOW_SERVICE)
    }
}
