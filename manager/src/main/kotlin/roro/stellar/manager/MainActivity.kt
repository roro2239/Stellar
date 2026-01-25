package roro.stellar.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import roro.stellar.manager.management.appsViewModel
import roro.stellar.manager.ui.features.apps.AppsScreen
import roro.stellar.manager.ui.features.home.HomeScreen
import roro.stellar.manager.ui.features.home.HomeViewModel
import roro.stellar.manager.ui.features.settings.SettingsScreen
import roro.stellar.manager.ui.features.terminal.TerminalScreen
import roro.stellar.manager.ui.navigation.components.LocalTopAppBarState
import roro.stellar.manager.ui.navigation.components.StandardBottomNavigation
import roro.stellar.manager.ui.navigation.components.TopAppBarProvider
import roro.stellar.manager.ui.navigation.routes.HomeScreen as HomeScreenRoute
import roro.stellar.manager.ui.navigation.routes.MainScreen
import roro.stellar.manager.ui.navigation.routes.SettingsScreen as SettingsScreenRoute
import androidx.navigation.NavType
import androidx.navigation.navArgument
import roro.stellar.manager.ui.features.starter.StarterScreen
import roro.stellar.manager.ui.features.home.others.AdbPairingTutorialScreen
import roro.stellar.manager.ui.navigation.safePopBackStack
import roro.stellar.manager.ui.theme.StellarTheme
import roro.stellar.manager.ui.theme.ThemePreferences
import roro.stellar.manager.ui.features.logs.LogsScreen
import roro.stellar.Stellar

data class StarterParams(
    val isRoot: Boolean,
    val host: String?,
    val port: Int
)

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO_STARTER = "navigate_to_starter"
        const val EXTRA_STARTER_IS_ROOT = "starter_is_root"
        const val EXTRA_STARTER_HOST = "starter_host"
        const val EXTRA_STARTER_PORT = "starter_port"
    }

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
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            val themeMode = ThemePreferences.themeMode.value

            val navigateToStarter = intent.getBooleanExtra(EXTRA_NAVIGATE_TO_STARTER, false)
            val starterIsRoot = intent.getBooleanExtra(EXTRA_STARTER_IS_ROOT, false)
            val starterHost = intent.getStringExtra(EXTRA_STARTER_HOST)
            val starterPort = intent.getIntExtra(EXTRA_STARTER_PORT, 0)

            StellarTheme(themeMode = themeMode) {
                TopAppBarProvider {
                    MainScreenContent(
                        homeViewModel = homeModel,
                        appsViewModel = appsModel,
                        initialStarterParams = if (navigateToStarter) {
                            StarterParams(starterIsRoot, starterHost, starterPort)
                        } else null
                    )
                }
            }
        }

        Stellar.addBinderReceivedListenerSticky(binderReceivedListener)
        Stellar.addBinderDeadListener(binderDeadListener)
        
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(
    homeViewModel: HomeViewModel,
    appsViewModel: roro.stellar.manager.management.AppsViewModel,
    initialStarterParams: StarterParams? = null
) {
    val topAppBarState = LocalTopAppBarState.current!!
    val navController = rememberNavController()
    var selectedIndex by remember { mutableIntStateOf(0) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val context = navController.context

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val hideBottomBarRoutes = listOf(
        HomeScreenRoute.Starter.route,
        HomeScreenRoute.AdbPairingTutorial.route,
        SettingsScreenRoute.Logs.route
    )
    val shouldShowBottomBar = currentRoute !in hideBottomBarRoutes

    androidx.compose.runtime.LaunchedEffect(initialStarterParams) {
        if (initialStarterParams != null) {
            navController.navigate(
                HomeScreenRoute.starterRoute(
                    initialStarterParams.isRoot,
                    initialStarterParams.host,
                    initialStarterParams.port
                )
            )
        }
    }

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
            AnimatedVisibility(
                visible = shouldShowBottomBar,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
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
            }
        },
        contentWindowInsets = if (shouldShowBottomBar) WindowInsets.navigationBars else WindowInsets(0)
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
                        appsViewModel = appsViewModel,
                        onNavigateToStarter = { isRoot, host, port ->
                            navController.navigate(HomeScreenRoute.starterRoute(isRoot, host, port))
                        },
                        onNavigateToAdbPairing = {
                            navController.navigate(HomeScreenRoute.AdbPairingTutorial.route)
                        }
                    )
                }
                composable(
                    route = HomeScreenRoute.Starter.route,
                    arguments = listOf(
                        navArgument("isRoot") { type = NavType.BoolType },
                        navArgument("host") { type = NavType.StringType },
                        navArgument("port") { type = NavType.IntType }
                    ),
                    enterTransition = { slideInHorizontally(tween(300)) { it } },
                    exitTransition = { slideOutHorizontally(tween(300)) { it } },
                    popEnterTransition = { slideInHorizontally(tween(300)) { -it } },
                    popExitTransition = { slideOutHorizontally(tween(300)) { it } }
                ) { backStackEntry ->
                    val isRoot = backStackEntry.arguments?.getBoolean("isRoot") ?: true
                    val host = backStackEntry.arguments?.getString("host")?.takeIf { it != "null" }
                    val port = backStackEntry.arguments?.getInt("port") ?: 0
                    StarterScreen(
                        topAppBarState = topAppBarState,
                        isRoot = isRoot,
                        host = host,
                        port = port,
                        onClose = { navController.safePopBackStack() }
                    )
                }
                composable(
                    route = HomeScreenRoute.AdbPairingTutorial.route,
                    enterTransition = { slideInHorizontally(tween(300)) { it } },
                    exitTransition = { slideOutHorizontally(tween(300)) { it } },
                    popEnterTransition = { slideInHorizontally(tween(300)) { -it } },
                    popExitTransition = { slideOutHorizontally(tween(300)) { it } }
                ) {
                    AdbPairingTutorialScreen(
                        topAppBarState = topAppBarState,
                        onBackPressed = { navController.safePopBackStack() }
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
                            navController.navigate(SettingsScreenRoute.Logs.route)
                        }
                    )
                }
                composable(
                    route = SettingsScreenRoute.Logs.route,
                    enterTransition = { slideInHorizontally(tween(300)) { it } },
                    exitTransition = { slideOutHorizontally(tween(300)) { it } },
                    popEnterTransition = { slideInHorizontally(tween(300)) { -it } },
                    popExitTransition = { slideOutHorizontally(tween(300)) { it } }
                ) {
                    LogsScreen(
                        topAppBarState = topAppBarState,
                        onBackClick = {
                            navController.safePopBackStack()
                        }
                    )
                }
            }
        }
    }
}