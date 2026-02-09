package roro.stellar.shizuku.server

import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import rikka.hidden.compat.PackageManagerApis
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class ShizukuConfigManager {

    private val config: ShizukuConfig

    init {
        this.config = load()

        var changed = false
        for (entry in LinkedHashMap(config.permissions)) {
            val packages = PackageManagerApis.getPackagesForUidNoThrow(entry.key)
            if (packages.isEmpty()) {
                Log.i(TAG, "移除不存在的 uid $entry.key 的配置")
                config.permissions.remove(entry.key)
                changed = true
            }
        }

        if (changed) {
            scheduleWrite()
        }
    }

    fun getFlagForUid(uid: Int): Int {
        synchronized(this) {
            return config.permissions[uid] ?: FLAG_ASK
        }
    }

    fun updateFlagForUid(uid: Int, flag: Int) {
        synchronized(this) {
            config.permissions[uid] = flag
            scheduleWrite()
            Log.i(TAG, "更新 uid $uid 的权限为 $flag")
        }
    }

    fun removeUid(uid: Int) {
        synchronized(this) {
            config.permissions.remove(uid)
            scheduleWrite()
        }
    }

    private fun scheduleWrite() {
        write(config)
    }

    companion object {
        private const val TAG = "ShizukuConfigManager"

        const val FLAG_ASK = 0
        const val FLAG_GRANTED = 1
        const val FLAG_DENIED = 2

        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private val FILE = File("/data/user_de/0/com.android.shell/shizuku_compat.json")
        private val ATOMIC_FILE = AtomicFile(FILE)

        private fun load(): ShizukuConfig {
            val stream: FileInputStream
            try {
                stream = ATOMIC_FILE.openRead()
            } catch (_: FileNotFoundException) {
                Log.i(TAG, "配置文件不存在，创建新配置")
                return ShizukuConfig()
            }

            var config: ShizukuConfig? = null
            try {
                config = GSON.fromJson(
                    InputStreamReader(stream),
                    ShizukuConfig::class.java
                )
            } catch (tr: Throwable) {
                Log.w(TAG, "加载配置失败", tr)
            } finally {
                try {
                    stream.close()
                } catch (e: IOException) {
                    Log.w(TAG, "关闭配置文件失败: $e")
                }
            }
            return config ?: ShizukuConfig()
        }

        private fun write(config: ShizukuConfig) {
            synchronized(ATOMIC_FILE) {
                val stream: FileOutputStream
                try {
                    stream = ATOMIC_FILE.startWrite()
                } catch (e: IOException) {
                    Log.w(TAG, "写入配置失败: $e")
                    return
                }
                try {
                    val json = GSON.toJson(config)
                    stream.write(json.toByteArray())
                    ATOMIC_FILE.finishWrite(stream)
                    Log.v(TAG, "配置已保存")
                } catch (tr: Throwable) {
                    Log.w(TAG, "保存配置失败", tr)
                    ATOMIC_FILE.failWrite(stream)
                }
            }
        }
    }
}

data class ShizukuConfig(
    val permissions: MutableMap<Int, Int> = mutableMapOf()
)
