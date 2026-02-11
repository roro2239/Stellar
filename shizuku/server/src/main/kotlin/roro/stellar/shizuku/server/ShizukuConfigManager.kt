package roro.stellar.shizuku.server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Shizuku 配置管理器
 * 管理 Shizuku 权限配置
 */
class ShizukuConfigManager(private val configDir: File) {

    companion object {
        private const val TAG = "ShizukuConfigManager"
        private const val CONFIG_FILE = "shizuku_permissions.json"
    }

    private val gson = Gson()
    private val configFile = File(configDir, CONFIG_FILE)
    private val permissions = mutableMapOf<Int, Int>()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        try {
            if (configFile.exists()) {
                val json = configFile.readText()
                val type = object : TypeToken<Map<Int, Int>>() {}.type
                val loaded = gson.fromJson<Map<Int, Int>>(json, type)
                permissions.clear()
                permissions.putAll(loaded)
                ShizukuLogger.i(TAG, "加载 Shizuku 权限配置: ${permissions.size} 条")
            }
        } catch (e: Exception) {
            ShizukuLogger.e(TAG, "加载 Shizuku 权限配置失败", e)
        }
    }

    private fun saveConfig() {
        try {
            configDir.mkdirs()
            val json = gson.toJson(permissions)
            configFile.writeText(json)
        } catch (e: Exception) {
            ShizukuLogger.e(TAG, "保存 Shizuku 权限配置失败", e)
        }
    }

    fun getPermission(uid: Int): Int {
        return permissions[uid] ?: ShizukuApiConstants.FLAG_ASK
    }

    fun setPermission(uid: Int, flag: Int) {
        permissions[uid] = flag
        saveConfig()
    }
}
