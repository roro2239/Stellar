package roro.stellar.manager.ui.features.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.ThemePreferences

class ManagerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ROUTE = "route"
        private const val EXTRA_IS_ROOT = "is_root"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_HAS_SECURE_SETTINGS = "has_secure_settings"

        fun createLogsIntent(context: Context): Intent {
            return Intent(context, ManagerActivity::class.java).apply {
                putExtra(EXTRA_ROUTE, ManagerRoute.Logs.route)
            }
        }

        fun createStarterIntent(
            context: Context,
            isRoot: Boolean,
            host: String?,
            port: Int,
            hasSecureSettings: Boolean = false
        ): Intent {
            return Intent(context, ManagerActivity::class.java).apply {
                putExtra(EXTRA_ROUTE, ManagerRoute.Starter.route)
                putExtra(EXTRA_IS_ROOT, isRoot)
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_HAS_SECURE_SETTINGS, hasSecureSettings)
            }
        }

        @RequiresApi(Build.VERSION_CODES.R)
        fun createPairingIntent(context: Context): Intent {
            return Intent(context, ManagerActivity::class.java).apply {
                putExtra(EXTRA_ROUTE, ManagerRoute.Pairing.route)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val route = intent.getStringExtra(EXTRA_ROUTE) ?: ManagerRoute.Logs.route
        val isRoot = intent.getBooleanExtra(EXTRA_IS_ROOT, true)
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, 0)
        val hasSecureSettings = intent.getBooleanExtra(EXTRA_HAS_SECURE_SETTINGS, false)

        setContent {
            val themeMode = ThemePreferences.themeMode.value
            StellarTheme(themeMode = themeMode) {
                ManagerNavHost(
                    startRoute = route,
                    isRoot = isRoot,
                    host = host,
                    port = port,
                    hasSecureSettings = hasSecureSettings,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ManagerNavHost(
    startRoute: String,
    isRoot: Boolean,
    host: String?,
    port: Int,
    hasSecureSettings: Boolean = false,
    onClose: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        composable(ManagerRoute.Logs.route) {
            LogsScreen(
                onBackClick = onClose
            )
        }

        composable(ManagerRoute.Starter.route) {
            StarterScreen(
                isRoot = isRoot,
                host = host,
                port = port,
                hasSecureSettings = hasSecureSettings,
                onClose = onClose,
                onNavigateToAdbPairing = {
                    navController.navigate(ManagerRoute.Pairing.route)
                }
            )
        }

        composable(ManagerRoute.Pairing.route) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AdbPairingScreen(
                    onBackPressed = onClose
                )
            }
        }
    }
}

sealed class ManagerRoute(val route: String) {
    data object Logs : ManagerRoute("logs")
    data object Starter : ManagerRoute("starter")
    data object Pairing : ManagerRoute("pairing")
}
