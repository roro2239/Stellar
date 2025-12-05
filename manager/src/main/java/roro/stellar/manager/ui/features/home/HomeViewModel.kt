package roro.stellar.manager.ui.features.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.compat.Resource
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.util.Logger.Companion.LOGGER

class HomeViewModel : ViewModel() {

    private val _serviceStatus = MutableLiveData<Resource<ServiceStatus>>()
    val serviceStatus = _serviceStatus as LiveData<Resource<ServiceStatus>>

    private fun load(): ServiceStatus {
        if (!Stellar.pingBinder()) {
            return ServiceStatus()
        }

        val uid = Stellar.uid
        val apiVersion = Stellar.version
        val patchVersion = Stellar.serverPatchVersion.let { if (it < 0) 0 else it }
        
        val seContext = if (apiVersion >= 6) {
            try {
                Stellar.sELinuxContext
            } catch (tr: Throwable) {
                LOGGER.w(tr, "getSELinuxContext")
                null
            }
        } else null

        return ServiceStatus(uid, apiVersion, patchVersion, seContext)
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = load()
                _serviceStatus.postValue(Resource.success(status))
            } catch (e: CancellationException) {
            } catch (e: Throwable) {
                _serviceStatus.postValue(Resource.error(e, ServiceStatus()))
            }
        }
    }
}