package roro.stellar.shizuku.server

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
            Log.w(TAG, "加载 Shizuku 配置失败", e)
            ShizukuConfig()
        }
    }

    private fun save() {
        try {
            configFile.parentFile?.mkdirs()
            FileWriter(configFile).use { writer ->
                GSON.toJson(config, writer)
            }
            Log.v(TAG, "Shizuku 配置已保存")
        } catch (e: Exception) {
            Log.w(TAG, "保存 Shizuku 配置失败", e)
        }
    }

    fun find(uid: Int): ShizukuAppEntry? {
        synchronized(this) {
            return config.apps[uid]
        }
    }

    fun getFlag(uid: Int): Int {
        synchronized(this) {
            val entry = config.apps[uid]
            if (entry != null && entry.packageName == ShizukuApiConstants.SHIZUKU_APP_PACKAGE_NAME) {
                return FLAG_DENIED
            }
            return entry?.flag ?: FLAG_ASK
        }
    }

    fun updateFlag(uid: Int, packageName: String, flag: Int) {
        synchronized(this) {
            val finalFlag = if (packageName == ShizukuApiConstants.SHIZUKU_APP_PACKAGE_NAME) {
                FLAG_DENIED
            } else {
                flag
            }
            
            var entry = config.apps[uid]
            if (entry == null) {
                entry = ShizukuAppEntry(packageName, finalFlag)
                config.apps[uid] = entry
            } else {
                entry.packageName = packageName
                entry.flag = finalFlag
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
        private const val TAG = "ShizukuConfigManager"
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