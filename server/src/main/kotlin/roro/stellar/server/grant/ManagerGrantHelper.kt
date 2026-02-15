package roro.stellar.server.grant

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.Logger

object ManagerGrantHelper {
    private val LOGGER = Logger("ManagerGrantHelper")

    fun grantWriteSecureSettings(managerAppId: Int) {
        try {
            val pm = rikka.hidden.compat.PermissionManagerApis.checkPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS,
                managerAppId
            )
            if (pm == PackageManager.PERMISSION_GRANTED) {
                LOGGER.i("管理器已拥有 WRITE_SECURE_SETTINGS 权限")
                return
            }

            LOGGER.i("正在授予管理器 WRITE_SECURE_SETTINGS 权限...")
            Runtime.getRuntime().exec(arrayOf(
                "pm", "grant", MANAGER_APPLICATION_ID,
                Manifest.permission.WRITE_SECURE_SETTINGS
            )).waitFor()
            LOGGER.i("WRITE_SECURE_SETTINGS 权限授予完成")
        } catch (e: Throwable) {
            LOGGER.e(e, "授予 WRITE_SECURE_SETTINGS 权限失败")
        }
    }

    fun grantAccessibilityService() {
        try {
            val serviceName = "$MANAGER_APPLICATION_ID/.service.StellarAccessibilityService"

            val process = Runtime.getRuntime().exec(arrayOf(
                "settings", "get", "secure",
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ))
            val currentServices = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (currentServices.contains(serviceName)) {
                LOGGER.i("无障碍服务已启用")
                return
            }

            val newServices = if (currentServices.isEmpty() || currentServices == "null") {
                serviceName
            } else {
                "$currentServices:$serviceName"
            }

            LOGGER.i("正在授予无障碍服务权限...")
            Runtime.getRuntime().exec(arrayOf(
                "settings", "put", "secure",
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newServices
            )).waitFor()
            Runtime.getRuntime().exec(arrayOf(
                "settings", "put", "secure",
                "accessibility_enabled",
                "1"
            )).waitFor()
            LOGGER.i("无障碍服务权限授予完成")
        } catch (e: Throwable) {
            LOGGER.e(e, "授予无障碍服务权限失败")
        }
    }
}
