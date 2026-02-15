package roro.stellar.manager

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import org.lsposed.hiddenapibypass.HiddenApiBypass
import roro.stellar.manager.compat.BuildUtils.atLeast30
import roro.stellar.manager.util.Logger.Companion.LOGGER

lateinit var application: StellarApplication

class StellarApplication : Application() {

    @RequiresApi(Build.VERSION_CODES.P)
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
    }
}
