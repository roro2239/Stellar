package roro.stellar.server

import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import rikka.hidden.compat.PackageManagerApis
import rikka.hidden.compat.PermissionManagerApis
import rikka.hidden.compat.UserManagerApis
import roro.stellar.server.ServerConstants.PERMISSION
import roro.stellar.server.ktx.workerHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

/**
 * Stellar配置管理器
 * Stellar Configuration Manager
 *
 *
 * 功能说明 Features：
 *
 *  * 管理应用权限配置的读写 - Manages app permission configuration read/write
 *  * 支持延迟写入优化性能 - Supports delayed write for performance
 *  * 自动同步系统权限状态 - Auto syncs system permission status
 *  * 监听APK变化并更新配置 - Monitors APK changes and updates config
 *
 *
 *
 * 配置存储 Configuration Storage：
 *
 *  * 使用JSON格式存储 - Uses JSON format for storage
 *  * 使用AtomicFile保证原子性 - Uses AtomicFile for atomicity
 *  * 支持配置版本升级 - Supports configuration version upgrade
 *
 */
class StellarConfigManager : ConfigManager() {
    private val mWriteRunner: Runnable = Runnable { write(config) }

    private val config: StellarConfig

    init {
        this.config = load()

        var changed = false

        for (entry in ArrayList<StellarConfig.PackageEntry>(config.packages)) {

            val packages = PackageManagerApis.getPackagesForUidNoThrow(entry.uid)
            if (packages.isEmpty()) {
                LOGGER.i("remove config for uid %d since it has gone", entry.uid)
                config.packages.remove(entry)
                changed = true
                continue
            }

            var packagesChanged = true

            for (packageName in entry.packages) {
                if (packages.contains(packageName)) {
                    packagesChanged = false
                    break
                }
            }

            val rawSize = entry.packages.size
            val s = LinkedHashSet(entry.packages)
            entry.packages.clear()
            entry.packages.addAll(s)
            val shrunkSize = entry.packages.size
            if (shrunkSize < rawSize) {
                LOGGER.w("entry.packages has duplicate! Shrunk. (%d -> %d)", rawSize, shrunkSize)
            }

            if (packagesChanged) {
                LOGGER.i("remove config for uid %d since the packages for it changed", entry.uid)
                config.packages.remove(entry)
                changed = true
            }
        }

        for (userId in UserManagerApis.getUserIdsNoThrow()) {
            for (pi in PackageManagerApis.getInstalledPackagesNoThrow(
                PackageManager.GET_PERMISSIONS.toLong(),
                userId
            )) {
                if (
                    pi == null ||
                    pi.applicationInfo == null ||
                    pi.requestedPermissions == null ||
                    !(pi.requestedPermissions as Array<out String?>).contains(PERMISSION)
                ) {
                    continue
                }

                val uid = pi.applicationInfo!!.uid
                val allowed: Boolean
                try {
                    allowed = PermissionManagerApis.checkPermission(
                        PERMISSION,
                        uid
                    ) == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) {
                    LOGGER.w("checkPermission")
                    continue
                }

                val packages = ArrayList<String?>()
                packages.add(pi.packageName)

                updateLocked(uid, packages, MASK_PERMISSION, if (allowed) FLAG_ALLOWED else 0)
                changed = true
            }
        }

        if (changed) {
            scheduleWriteLocked()
        }
    }

    private fun scheduleWriteLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (workerHandler.hasCallbacks(mWriteRunner)) {
                return
            }
        } else {
            workerHandler.removeCallbacks(mWriteRunner)
        }
        workerHandler.postDelayed(mWriteRunner, WRITE_DELAY)
    }

    private fun findLocked(uid: Int): StellarConfig.PackageEntry? {
        for (entry in config.packages) {
            if (uid == entry.uid) {
                return entry
            }
        }
        return null
    }

    override fun find(uid: Int): StellarConfig.PackageEntry? {
        synchronized(this) {
            return findLocked(uid)
        }
    }

    private fun updateLocked(uid: Int, packages: MutableList<String?>?, mask: Int, values: Int) {
        var entry = findLocked(uid)
        if (entry == null) {
            entry = StellarConfig.PackageEntry(uid, mask and values)
            config.packages.add(entry)
        } else {
            val newValue = (entry.flags and mask.inv()) or (mask and values)
            if (newValue == entry.flags) {
                return
            }
            entry.flags = newValue
        }
        if (packages != null) {
            for (packageName in packages) {
                if (entry.packages.contains(packageName)) {
                    continue
                }
                entry.packages.add(packageName)
            }
        }
        scheduleWriteLocked()
    }

    override fun update(uid: Int, packages: MutableList<String?>?, mask: Int, values: Int) {
        synchronized(this) {
            updateLocked(uid, packages, mask, values)
        }
    }

    private fun removeLocked(uid: Int) {
        val entry = findLocked(uid) ?: return
        config.packages.remove(entry)
        scheduleWriteLocked()
    }

    override fun remove(uid: Int) {
        synchronized(this) {
            removeLocked(uid)
        }
    }

    companion object {
        /** JSON反序列化器 JSON deserializer  */
        private val GSON_IN: Gson = GsonBuilder()
            .create()

        /** JSON序列化器（带版本过滤） JSON serializer (with version filter)  */
        private val GSON_OUT: Gson = GsonBuilder()
            .setVersion(StellarConfig.LATEST_VERSION.toDouble())
            .create()

        /** 延迟写入时间（毫秒） Delayed write time in milliseconds  */
        private const val WRITE_DELAY = (10 * 1000).toLong()

        private val FILE = File("/data/user_de/0/com.android.shell/Stellar.json")
        private val ATOMIC_FILE = AtomicFile(FILE)

        fun load(): StellarConfig {
            val stream: FileInputStream
            try {
                stream = ATOMIC_FILE.openRead()
            } catch (_: FileNotFoundException) {
                LOGGER.i("no existing config file " + ATOMIC_FILE.baseFile + "; starting empty")
                return StellarConfig()
            }

            var config: StellarConfig? = null
            try {
                config = GSON_IN.fromJson(
                    InputStreamReader(stream),
                    StellarConfig::class.java
                )
            } catch (tr: Throwable) {
                LOGGER.w(tr, "load config")
            } finally {
                try {
                    stream.close()
                } catch (e: IOException) {
                    LOGGER.w("failed to close: $e")
                }
            }
            if (config != null) return config
            return StellarConfig()
        }

        fun write(config: StellarConfig?) {
            synchronized(ATOMIC_FILE) {
                val stream: FileOutputStream
                try {
                    stream = ATOMIC_FILE.startWrite()
                } catch (e: IOException) {
                    LOGGER.w("failed to write state: $e")
                    return
                }
                try {
                    val json = GSON_OUT.toJson(config)
                    stream.write(json.toByteArray())

                    ATOMIC_FILE.finishWrite(stream)
                    LOGGER.v("config saved")
                } catch (tr: Throwable) {
                    LOGGER.w(tr, "can't save %s, restoring backup.", ATOMIC_FILE.baseFile)
                    ATOMIC_FILE.failWrite(stream)
                }
            }
        }
    }
}