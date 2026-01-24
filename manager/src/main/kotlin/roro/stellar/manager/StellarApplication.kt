package roro.stellar.manager

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import roro.stellar.Stellar
import roro.stellar.manager.compat.BuildUtils.atLeast30
import roro.stellar.manager.util.Logger.Companion.LOGGER

lateinit var application: StellarApplication

class StellarApplication : Application() {

    companion object {

        init {
            LOGGER.d("init")

            @Suppress("DEPRECATION")
            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            
            HiddenApiBypass.setHiddenApiExemptions("")

            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }
    }

    private fun init(context: Context) {
        StellarSettings.initialize(context)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        init(this)
        createMarkerDirectory()
        setupFollowStartupListener()
    }

    private fun setupFollowStartupListener() {
        Stellar.addBinderReceivedListener(Stellar.OnBinderReceivedListener {
            val followCommand = StellarSettings.getPreferences()
                .getString(StellarSettings.FOLLOW_STARTUP_COMMAND, null)
            if (!followCommand.isNullOrBlank()) {
                LOGGER.i("执行跟随启动命令: $followCommand")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val process = Stellar.newProcess(
                            arrayOf("sh", "-c", followCommand),
                            null,
                            null
                        )
                        val exitCode = process.waitFor()
                        LOGGER.i("跟随启动命令执行完成，退出码: $exitCode")
                    } catch (e: Exception) {
                        LOGGER.e("执行跟随启动命令失败: ${e.message}")
                    }
                }
            }
        })
    }

    private fun createMarkerDirectory() {
        try {
            val markerDir = getExternalFilesDir(null)
            if (markerDir != null) {
                if (!markerDir.exists()) {
                    markerDir.mkdirs()
                }
                val markerFile = java.io.File(markerDir, ".stellar_marker")
                if (!markerFile.exists()) {
                    markerFile.createNewFile()
                }
                LOGGER.i("已创建 Stellar 标记目录: ${markerDir.absolutePath}")
            }
        } catch (e: Exception) {
            LOGGER.w("创建标记目录失败: ${e.message}")
        }
    }
}