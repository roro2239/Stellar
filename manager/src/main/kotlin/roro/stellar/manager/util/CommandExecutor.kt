package roro.stellar.manager.util

import android.util.Log
import org.json.JSONArray
import roro.stellar.manager.AppConstants
import roro.stellar.manager.StellarSettings


enum class CommandMode {
    CLICK_EXECUTE,
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
}
