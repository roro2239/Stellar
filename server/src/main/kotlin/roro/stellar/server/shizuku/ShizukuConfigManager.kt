package roro.stellar.server.shizuku

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import roro.stellar.server.util.Logger
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Shizuku 应用配置管理器
 * 单独存储 Shizuku 兼容应用的授权配置
 */
class ShizukuConfigManager {

    private val config: ShizukuConfig
    private val configFile = File("/data/local/tmp/shizuku_config.json")

    init {
        config = load()
    }

    private fun load(): ShizukuConfig {
        return try {
            if (configFile.exists()) {
                BufferedReader(FileReader(configFile)).use { reader ->
                    GSON.fromJson(reader, ShizukuConfig::class.java) ?: ShizukuConfig()
                }
            } else {
                ShizukuConfig()
            }
        } catch (e: Exception) {
            LOGGER.w(e, "加载 Shizuku 配置失败")
            ShizukuConfig()
        }
    }

    private fun save() {
        try {
            configFile.parentFile?.mkdirs()
            FileWriter(configFile).use { writer ->
                GSON.toJson(config, writer)
            }
            LOGGER.v("Shizuku 配置已保存")
        } catch (e: Exception) {
            LOGGER.w(e, "保存 Shizuku 配置失败")
        }
    }

    fun find(uid: Int): ShizukuAppEntry? {
        synchronized(this) {
            return config.apps[uid]
        }
    }

    fun getFlag(uid: Int): Int {
        synchronized(this) {
            return config.apps[uid]?.flag ?: FLAG_ASK
        }
    }

    fun updateFlag(uid: Int, packageName: String, flag: Int) {
        synchronized(this) {
            var entry = config.apps[uid]
            if (entry == null) {
                entry = ShizukuAppEntry(packageName, flag)
                config.apps[uid] = entry
            } else {
                entry.packageName = packageName
                entry.flag = flag
            }
            save()
        }
    }

    fun getAllEntries(): Map<Int, ShizukuAppEntry> {
        synchronized(this) {
            return config.apps.toMap()
        }
    }

    companion object {
        private val LOGGER = Logger("ShizukuConfigManager")
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

        const val FLAG_ASK = 0
        const val FLAG_GRANTED = 1
        const val FLAG_DENIED = 2
    }
}

/**
 * Shizuku 配置数据类
 */
data class ShizukuConfig(
    val apps: MutableMap<Int, ShizukuAppEntry> = mutableMapOf()
)

/**
 * Shizuku 应用条目
 */
data class ShizukuAppEntry(
    var packageName: String,
    var flag: Int = 0
)
