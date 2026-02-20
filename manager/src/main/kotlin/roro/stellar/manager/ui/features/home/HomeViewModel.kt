package roro.stellar.manager.ui.features.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.common.state.Resource
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.startup.service.SelfStarterService

class HomeViewModel : ViewModel() {

    private val _serviceStatus = MutableLiveData<Resource<ServiceStatus>>()
    val serviceStatus = _serviceStatus as LiveData<Resource<ServiceStatus>>

    private fun load(): ServiceStatus {
        if (!Stellar.pingBinder()) return ServiceStatus()
        return ServiceStatus(Stellar.uid, Stellar.version)
    }

    fun reload(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = load()
                _serviceStatus.postValue(Resource.success(status))

                val prefs = StellarSettings.getPreferences()
                val lastVersion = prefs.getInt(StellarSettings.LAST_VERSION_CODE, BuildConfig.VERSION_CODE)
                if (status.isRunning && lastVersion < BuildConfig.VERSION_CODE) {
                    restartService(context, status.uid == 0)
                }
                prefs.edit().putInt(StellarSettings.LAST_VERSION_CODE, BuildConfig.VERSION_CODE).apply()
            } catch (e: CancellationException) {
            } catch (e: Throwable) {
                _serviceStatus.postValue(Resource.error(e, ServiceStatus()))
            }
        }
    }

    private fun restartService(context: Context, isRoot: Boolean) {
        if (isRoot) {
            Shell.cmd(Starter.internalCommand).submit()
        } else {
            context.startService(Intent(context, SelfStarterService::class.java))
        }
    }
}
