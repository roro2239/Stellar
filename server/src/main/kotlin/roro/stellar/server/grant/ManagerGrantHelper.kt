package roro.stellar.server.grant

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.hidden.compat.PermissionManagerApis
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.util.Logger

object ManagerGrantHelper {
    private val LOGGER = Logger("ManagerGrantHelper")

    fun grantWriteSecureSettings(managerAppId: Int) {
        try {
            val pm = PermissionManagerApis.checkPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS,
                managerAppId
            )
            if (pm == PackageManager.PERMISSION_GRANTED) {
                LOGGER.i("Manager already has WRITE_SECURE_SETTINGS")
                return
            }

            LOGGER.i("Granting WRITE_SECURE_SETTINGS to manager...")
            Runtime.getRuntime().exec(
                arrayOf(
                    "pm", "grant", MANAGER_APPLICATION_ID,
                    Manifest.permission.WRITE_SECURE_SETTINGS
                )
            ).waitFor()
            LOGGER.i("WRITE_SECURE_SETTINGS grant completed")
        } catch (e: Throwable) {
            LOGGER.e(e, "Failed to grant WRITE_SECURE_SETTINGS")
        }
    }

    fun grantAccessibilityService() {
        try {
            val serviceName = "$MANAGER_APPLICATION_ID/.service.StellarAccessibilityService"

            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "settings", "get", "secure",
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            )
            val currentServices = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            val services = currentServices
                .split(":")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "null" }
                .toMutableSet()

            if (services.contains(serviceName)) {
                LOGGER.i("Accessibility service already enabled")
                return
            }

            services.add(serviceName)
            val newServices = services.joinToString(":")

            LOGGER.i("Granting accessibility service...")
            Runtime.getRuntime().exec(
                arrayOf(
                    "settings", "put", "secure",
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
            ).waitFor()
            Runtime.getRuntime().exec(
                arrayOf(
                    "settings", "put", "secure",
                    "accessibility_enabled",
                    "1"
                )
            ).waitFor()
            LOGGER.i("Accessibility service granted")
        } catch (e: Throwable) {
            LOGGER.e(e, "Failed to grant accessibility service")
        }
    }
}
