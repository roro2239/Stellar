package roro.stellar.manager

import android.os.Bundle
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import roro.stellar.StellarProvider
import roro.stellar.manager.db.AppDatabase
import roro.stellar.manager.db.ConfigEntity
import roro.stellar.server.StellarConfig

class StellarManagerProvider : StellarProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val db = AppDatabase.get(context!!)
        when (method) {
            METHOD_GET_SHIZUKU_COMPAT -> {
                val value = db.configDao().get(KEY_SHIZUKU_COMPAT) ?: "true"
                return Bundle().apply { putBoolean(KEY_SHIZUKU_COMPAT, value.toBoolean()) }
            }
            METHOD_LOAD_CONFIG -> {
                val entries = db.configDao().getAll()
                val config = StellarConfig()
                entries.forEach { e ->
                    when (e.key) {
                        KEY_SHIZUKU_COMPAT -> config.shizukuCompatEnabled = e.value.toBoolean()
                        KEY_PACKAGES_JSON -> config.packages = GSON.fromJson(e.value,
                            object : TypeToken<MutableMap<Int, StellarConfig.PackageEntry>>() {}.type) ?: mutableMapOf()
                    }
                }
                return Bundle().apply { putString(KEY_CONFIG_JSON, GSON.toJson(config)) }
            }
            METHOD_SAVE_CONFIG -> {
                val json = extras?.getString(KEY_CONFIG_JSON) ?: return null
                val config = GSON.fromJson(json, StellarConfig::class.java) ?: return null
                db.configDao().setAll(listOf(
                    ConfigEntity(KEY_SHIZUKU_COMPAT, config.shizukuCompatEnabled.toString()),
                    ConfigEntity(KEY_PACKAGES_JSON, GSON.toJson(config.packages))
                ))
                return Bundle()
            }
        }
        if (extras == null) return null
        return super.call(method, arg, extras)
    }

    companion object {
        private val GSON = GsonBuilder().create()
        const val METHOD_GET_SHIZUKU_COMPAT = "getShizukuCompat"
        const val KEY_SHIZUKU_COMPAT = "shizukuCompat"
        const val METHOD_LOAD_CONFIG = "loadConfig"
        const val METHOD_SAVE_CONFIG = "saveConfig"
        const val KEY_CONFIG_JSON = "configJson"
        const val KEY_PACKAGES_JSON = "packagesJson"
    }
}
