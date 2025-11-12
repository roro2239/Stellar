package roro.stellar.manager.ui.features.home

import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.manager.BuildConfig
import roro.stellar.manager.Manifest
import roro.stellar.manager.compat.Resource
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.utils.Logger.LOGGER
import roro.stellar.manager.utils.StellarSystemApis
import roro.stellar.Stellar

/**
 * 主页ViewModel
 * Home ViewModel
 * 
 * 功能说明 Features：
 * - 管理Stellar服务状态数据 - Manages Stellar service status data
 * - 提供服务状态的LiveData - Provides service status LiveData
 * - 在后台线程加载状态信息 - Loads status info in background thread
 * - 包含UID、版本、SELinux上下文等信息 - Contains UID, version, SELinux context, etc.
 * 
 * 数据流 Data Flow：
 * - reload() -> load() -> ServiceStatus -> LiveData更新
 */
class HomeViewModel : ViewModel() {

    /** 服务状态LiveData Service status LiveData */
    private val _serviceStatus = MutableLiveData<Resource<ServiceStatus>>()
    val serviceStatus = _serviceStatus as LiveData<Resource<ServiceStatus>>

    /**
     * 加载服务状态信息
     * Load service status info
     * 
     * @return 服务状态对象
     */
    private fun load(): ServiceStatus {
        // 检查Binder是否存活
        if (!Stellar.pingBinder()) {
            return ServiceStatus()
        }

        // 获取服务信息
        val uid = Stellar.getUid()
        val apiVersion = Stellar.getVersion()
        val patchVersion = Stellar.getServerPatchVersion().let { if (it < 0) 0 else it }
        
        // API 6+支持SELinux上下文
        val seContext = if (apiVersion >= 6) {
            try {
                Stellar.getSELinuxContext()
            } catch (tr: Throwable) {
                LOGGER.w(tr, "getSELinuxContext")
                null
            }
        } else null
        
        // 测试权限
        val permissionTest =
            Stellar.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED

        // 修复旧版本服务卸载后不退出的问题
        // Before a526d6bb, server will not exit on uninstall, manager installed later will get not permission
        // Run a random remote transaction here, report no permission as not running
        StellarSystemApis.checkPermission(Manifest.permission.API_V23, BuildConfig.APPLICATION_ID, 0)
        
        return ServiceStatus(uid, apiVersion, patchVersion, seContext, permissionTest)
    }

    /**
     * 重新加载服务状态
     * Reload service status
     */
    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = load()
                _serviceStatus.postValue(Resource.success(status))
            } catch (e: CancellationException) {
                // 协程取消，忽略
            } catch (e: Throwable) {
                _serviceStatus.postValue(Resource.error(e, ServiceStatus()))
            }
        }
    }
}