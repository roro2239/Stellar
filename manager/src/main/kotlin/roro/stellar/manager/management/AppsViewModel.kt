package roro.stellar.manager.management

import android.content.Context
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.authorization.AuthorizationManager
import roro.stellar.manager.compat.Resource

/**
 * 应用类型枚举
 */
enum class AppType {
    STELLAR,  // Stellar 原生应用
    SHIZUKU   // Shizuku 兼容应用
}

/**
 * 带类型标记的应用信息
 */
data class AppInfo(
    val packageInfo: PackageInfo,
    val appType: AppType
)

@MainThread
fun ComponentActivity.appsViewModel() = viewModels<AppsViewModel> { 
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppsViewModel(this@appsViewModel) as T
        }
    }
}

@MainThread
fun Fragment.appsViewModel() = activityViewModels<AppsViewModel> { 
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppsViewModel(requireContext()) as T
        }
    }
}

class AppsViewModel(context: Context) : ViewModel() {

    private val _stellarApps = MutableLiveData<Resource<List<AppInfo>>>()
    val stellarApps = _stellarApps as LiveData<Resource<List<AppInfo>>>

    private val _shizukuApps = MutableLiveData<Resource<List<AppInfo>>>()
    val shizukuApps = _shizukuApps as LiveData<Resource<List<AppInfo>>>

    private val _grantedCount = MutableLiveData<Resource<Int>>()
    val grantedCount = _grantedCount as LiveData<Resource<Int>>

    fun load(onlyCount: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stellarList = mutableListOf<AppInfo>()
                val shizukuList = mutableListOf<AppInfo>()
                var count = 0

                for (pi in AuthorizationManager.getPackages()) {
                    val appType = AuthorizationManager.getAppType(pi)
                    val appInfo = AppInfo(pi, appType)

                    when (appType) {
                        AppType.STELLAR -> stellarList.add(appInfo)
                        AppType.SHIZUKU -> shizukuList.add(appInfo)
                    }

                    if (Stellar.getFlagForUid(pi.applicationInfo!!.uid, "stellar") == AuthorizationManager.FLAG_GRANTED) {
                        count++
                    }
                }

                if (!onlyCount) {
                    _stellarApps.postValue(Resource.success(stellarList))
                    _shizukuApps.postValue(Resource.success(shizukuList))
                }
                _grantedCount.postValue(Resource.success(count))
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                _stellarApps.postValue(Resource.error(e, null))
                _shizukuApps.postValue(Resource.error(e, null))
                _grantedCount.postValue(Resource.error(e, 0))
            }
        }
    }
}

