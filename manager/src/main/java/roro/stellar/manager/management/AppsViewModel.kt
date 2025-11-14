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
 * 在Activity中获取应用列表ViewModel
 * Get apps ViewModel in Activity
 */
@MainThread
fun ComponentActivity.appsViewModel() = viewModels<AppsViewModel> { 
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppsViewModel(this@appsViewModel) as T
        }
    }
}

/**
 * 在Fragment中获取应用列表ViewModel
 * Get apps ViewModel in Fragment
 */
@MainThread
fun Fragment.appsViewModel() = activityViewModels<AppsViewModel> { 
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppsViewModel(requireContext()) as T
        }
    }
}

/**
 * 应用列表ViewModel
 * Apps List ViewModel
 * 
 * 功能说明 Features：
 * - 管理请求权限的应用列表 - Manages list of apps requesting permissions
 * - 提供应用列表和已授权数量的LiveData - Provides app list and granted count LiveData
 * - 在后台线程加载数据 - Loads data in background thread
 * - 支持共享ViewModel在Activity/Fragment间共享数据 - Supports shared ViewModel for data sharing
 * 
 * 使用方式 Usage：
 * ```kotlin
 * val appsModel by appsViewModel()
 * appsModel.packages.observe(this) { resource ->
 *     // 处理应用列表
 * }
 * ```
 */
class AppsViewModel(context: Context) : ViewModel() {

    /** 应用包列表LiveData App package list LiveData */
    private val _packages = MutableLiveData<Resource<List<PackageInfo>>>()
    val packages = _packages as LiveData<Resource<List<PackageInfo>>>

    /** 已授权应用数量LiveData Granted apps count LiveData */
    private val _grantedCount = MutableLiveData<Resource<Int>>()
    val grantedCount = _grantedCount as LiveData<Resource<Int>>

    /**
     * 加载应用列表
     * Load app list
     * 
     * @param onlyCount 是否只加载数量（不加载完整列表）
     */
    fun load(onlyCount: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list: MutableList<PackageInfo> = ArrayList()
                var count = 0
                
                // 遍历所有请求权限的应用
                for (pi in AuthorizationManager.getPackages()) {
                    list.add(pi)
                    // 统计已授权的应用数量
                    if (Stellar.getFlagsForUid(pi.applicationInfo!!.uid) == AuthorizationManager.FLAG_GRANTED) count++
                }
                
                // 更新LiveData
                if (!onlyCount) _packages.postValue(Resource.success(list))
                _grantedCount.postValue(Resource.success(count))
            } catch (_: CancellationException) {
                // 协程取消，忽略
            } catch (e: Throwable) {
                _packages.postValue(Resource.error(e, null))
                _grantedCount.postValue(Resource.error(e, 0))
            }
        }
    }
}

