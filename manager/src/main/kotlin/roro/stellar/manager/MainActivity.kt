package roro.stellar.manager

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import roro.stellar.manager.domain.apps.AppsViewModel
import roro.stellar.manager.domain.apps.appsViewModel
import roro.stellar.manager.ui.features.apps.AppsScreen
import roro.stellar.manager.ui.features.home.HomeScreen
import roro.stellar.manager.ui.features.home.HomeViewModel
import roro.stellar.manager.ui.features.settings.SettingsScreen
import roro.stellar.manager.ui.features.terminal.TerminalScreen
import roro.stellar.manager.ui.features.manager.ManagerActivity
import roro.stellar.manager.ui.components.AdaptiveLayoutProvider
import roro.stellar.manager.ui.navigation.components.LocalNavigationState
import roro.stellar.manager.ui.navigation.components.LocalTopAppBarState
import roro.stellar.manager.ui.navigation.components.NavigationState
import roro.stellar.manager.ui.navigation.components.StandardBottomNavigation
import roro.stellar.manager.ui.navigation.components.StandardNavigationRail
import roro.stellar.manager.ui.navigation.components.TopAppBarProvider
import roro.stellar.manager.ui.navigation.routes.MainScreen
import roro.stellar.manager.ui.navigation.safePopBackStack
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.Stellar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
        
        checkServerStatus()
        
        if (Stellar.pingBinder() && appsModel.stellarApps.value == null) {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    homeViewModel: HomeViewModel,
    appsViewModel: AppsViewModel
) {
    val topAppBarState = LocalTopAppBarState.current!!
    val navController = rememberNavController()
    var selectedIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val context = navController.context

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    val onNavigationItemClick: (Int) -> Unit = { index ->
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

    val navigationState = NavigationState(
        selectedIndex = selectedIndex,
        onItemClick = onNavigationItemClick
    )

    val navHostContent: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = MainScreen.Home.route,
            modifier = modifier,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            navigation(
                startDestination = "home",
                route = MainScreen.Home.route
            ) {
                composable("home") {
                    HomeScreen(
                        topAppBarState = topAppBarState,
                        homeViewModel = homeViewModel,
                        appsViewModel = appsViewModel,
                        onNavigateToStarter = { isRoot, host, port, hasSecureSettings ->
                            context.startActivity(ManagerActivity.createStarterIntent(context, isRoot, host, port, hasSecureSettings))
                        }
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
                startDestination = "terminal",
                route = MainScreen.Terminal.route
            ) {
                composable("terminal") {
                    TerminalScreen(
                        topAppBarState = topAppBarState
                    )
                }
            }

            navigation(
                startDestination = "settings",
                route = MainScreen.Settings.route
            ) {
                composable("settings") {
                    SettingsScreen(
                        topAppBarState = topAppBarState,
                        onNavigateToLogs = {
                            context.startActivity(ManagerActivity.createLogsIntent(context))
                        }
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalNavigationState provides navigationState) {
        AdaptiveLayoutProvider {
            if (isLandscape) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                ) {
                    StandardNavigationRail(
                        selectedIndex = selectedIndex,
                        onItemClick = onNavigationItemClick
                    )
                    navHostContent(Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            StandardBottomNavigation(
                                selectedIndex = selectedIndex,
                                onItemClick = onNavigationItemClick
                            )
                        },
                        contentWindowInsets = WindowInsets.navigationBars
                    ) {
                        navHostContent(Modifier.fillMaxSize().padding(it))
                    }
                }
            }
        }
    }
}
