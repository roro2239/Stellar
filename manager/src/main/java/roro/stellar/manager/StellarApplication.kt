/**
 * Stellar Manager应用程序类
 * Stellar Manager Application Class
 * 
 * 功能说明 Features：
 * - 应用程序全局初始化入口
 * - 配置Shell环境和Hidden API绕过
 * - 初始化设置和本地化
 * - 加载Native库
 * 
 * 设计要点 Design Points：
 * - 使用伴生对象进行静态初始化
 * - 配置libsu Shell默认行为
 * - 设置Hidden API绕过（Android 9+）
 * - 加载ADB Native库（Android 11+）
 */
package roro.stellar.manager

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import org.lsposed.hiddenapibypass.HiddenApiBypass
import roro.stellar.manager.compat.BuildUtils.atLeast30
import roro.stellar.manager.ktx.logd
import roro.stellar.manager.utils.CrashHandler
import roro.stellar.manager.utils.LogFileManager


/** 全局应用程序实例 Global application instance */
lateinit var application: StellarApplication

/**
 * Stellar管理器应用程序主类
 * Main application class for Stellar Manager
 */
class StellarApplication : Application() {

    companion object {

        init {
            logd("StellarApplication", "init")

            // 配置Shell环境 - 重定向stderr到stdout
            @Suppress("DEPRECATION")
            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            
            // 绕过Hidden API限制
            HiddenApiBypass.setHiddenApiExemptions("")

            // Android 11+：加载ADB Native库（用于无线ADB配对）
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }
    }

    /**
     * 初始化应用程序设置
     * Initialize application settings
     * 
     * @param context 上下文 Context
     */
    private fun init(context: Context?) {
        // 初始化日志文件管理器
        LogFileManager.getInstance().init(this)
        // 初始化崩溃处理器
        CrashHandler.getInstance().init(this)
        
        // 初始化设置系统
        StellarSettings.initialize(context)
        // 禁用夜间模式（使用自定义主题）
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        init(this)
    }
}