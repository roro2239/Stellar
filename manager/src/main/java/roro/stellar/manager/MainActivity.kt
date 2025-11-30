package roro.stellar.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import roro.stellar.manager.management.appsViewModel
import roro.stellar.manager.ui.features.apps.AppsScreen
import roro.stellar.manager.ui.features.home.HomeScreen
import roro.stellar.manager.ui.features.home.HomeViewModel
import roro.stellar.manager.ui.features.settings.SettingsScreen
import roro.stellar.manager.ui.navigation.components.LocalTopAppBarState
import roro.stellar.manager.ui.navigation.components.StandardBottomNavigation
import roro.stellar.manager.ui.navigation.components.TopAppBarProvider
import roro.stellar.manager.ui.navigation.routes.MainScreen
import roro.stellar.manager.ui.navigation.safePopBackStack
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.Stellar
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val binderReceivedListener = Stellar.OnBinderReceivedListener {
        checkServerStatus()
        try {
            appsModel.load()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val binderDeadListener = Stellar.OnBinderDeadListener {
        checkServerStatus()
    }

    private val homeModel by viewModels<HomeViewModel>()
    private val appsModel by appsViewModel()
    
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku权限已授予", Toast.LENGTH_SHORT).show()
                homeModel.reload()
            } else {
                Toast.makeText(this, "Shizuku权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            val themeMode = ThemePreferences.themeMode.value
            StellarTheme(themeMode = themeMode) {
                TopAppBarProvider {
                    MainScreenContent(
                        homeViewModel = homeModel,
                        appsViewModel = appsModel
                    )
                }
            }
        }

        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        checkServerStatus()
        
        if (Stellar.pingBinder() && appsModel.packages.value == null) {
            appsModel.load()
        }
    }

    override fun onResume() {
        super.onResume()
        checkServerStatus()
        if (Stellar.pingBinder()) {
            appsModel.load(true)
        }
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        Stellar.removeBinderReceivedListener(binderReceivedListener)
        Stellar.removeBinderDeadListener(binderDeadListener)
        
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    homeViewModel: HomeViewModel,
    appsViewModel: roro.stellar.manager.management.AppsViewModel
) {
    val topAppBarState = LocalTopAppBarState.current!!
    val navController = rememberNavController()
    var selectedIndex by remember { mutableIntStateOf(0) }
    
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val context = navController.context
    
    BackHandler {
        if (navController.previousBackStackEntry == null) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            }
        } else {
            navController.safePopBackStack()
        }
    }

    Scaffold(
        bottomBar = {
            StandardBottomNavigation(
                selectedIndex = selectedIndex,
                onItemClick = { index ->
                    if (selectedIndex != index) {
                        selectedIndex = index
                        val route = MainScreen.entries[index].route
                        navController.navigate(route) {
                            popUpTo(0) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) {
        NavHost(
            navController = navController,
            startDestination = MainScreen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            navigation(
                startDestination = "home",
                route = MainScreen.Home.route
            ) {
                composable("home") {
                    HomeScreen(
                        topAppBarState = topAppBarState,
                        homeViewModel = homeViewModel,
                        appsViewModel = appsViewModel
                    )
                }
            }
            
            navigation(
                startDestination = "apps",
                route = MainScreen.Apps.route
            ) {
                composable("apps") {
                    AppsScreen(
                        topAppBarState = topAppBarState,
                        appsViewModel = appsViewModel
                    )
                }
            }
            
            navigation(
                startDestination = "settings",
                route = MainScreen.Settings.route
            ) {
                composable("settings") {
                    SettingsScreen(topAppBarState = topAppBarState)
                }
            }
        }
    }
}