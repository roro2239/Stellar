package roro.stellar.server.command

import org.json.JSONArray
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.Logger
import java.io.File

object FollowCommandExecutor {
    private val LOGGER = Logger("FollowCommandExecutor")
    private const val FOLLOW_COMMANDS_FILE = "/storage/emulated/0/Android/data/$MANAGER_APPLICATION_ID/files/follow_commands.json"

    fun execute() {
        try {
            val file = File(FOLLOW_COMMANDS_FILE)
            if (!file.exists()) {
                LOGGER.i("跟随服务命令文件不存在，跳过执行")
                return
            }

            val array = JSONArray(file.readText())
            if (array.length() == 0) {
                LOGGER.i("没有跟随服务命令需要执行")
                return
            }

            LOGGER.i("开始执行 ${array.length()} 个跟随服务命令")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "未命名")
                val command = obj.getString("command")

                try {
                    LOGGER.i("执行命令: $title")
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    val exitCode = process.waitFor()
                    LOGGER.i("命令执行完成: $title, 退出码: $exitCode")
                } catch (e: Exception) {
                    LOGGER.e(e, "命令执行失败: $title")
                }
            }
        } catch (e: Exception) {
            LOGGER.e(e, "读取或执行跟随服务命令失败")
        }
    }
}
