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

/**
 * Stellar Manager主Activity
 * 
 * 应用程序启动入口，管理主界面导航和UI状态。
 */
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
    
    // Shizuku权限监听器
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku权限已授予", Toast.LENGTH_SHORT).show()
                // 刷新界面状态
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
        
        // 初始化Shizuku
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            // Shizuku未安装或不可用
            e.printStackTrace()
        }
        
        // 初始加载
        checkServerStatus()
        
        // 加载应用列表（需要服务运行）
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
        
        // 移除Shizuku监听器
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * 主界面内容
 * 
 * 构建应用的主要界面结构，包括底部导航栏和页面内容。
 */
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
    
    // 处理系统返回键
    BackHandler {
        if (navController.previousBackStackEntry == null) {
            // 双击退出
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
                            // 清除导航栈
                            popUpTo(0) {
                                inclusive = true
                            }
                            // 避免重复导航到同一目标
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
            // 主页导航图
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
            
            // 授权应用导航图
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
            
            // 设置导航图
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