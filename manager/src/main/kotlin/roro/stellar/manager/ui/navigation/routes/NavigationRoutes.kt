package roro.stellar.manager.ui.navigation.routes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainScreen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconFilled: ImageVector
) {
    Home(
        route = "home_graph",
        label = "启动",
        icon = Icons.Outlined.PlayArrow,
        iconFilled = Icons.Filled.PlayArrow
    ),

    Apps(
        route = "apps_graph",
        label = "授权应用",
        icon = Icons.Outlined.Apps,
        iconFilled = Icons.Filled.Apps
    ),

    Terminal(
        route = "terminal_graph",
        label = "命令",
        icon = Icons.Outlined.Terminal,
        iconFilled = Icons.Filled.Terminal
    ),

    Settings(
        route = "settings_graph",
        label = "设置",
        icon = Icons.Outlined.Settings,
        iconFilled = Icons.Filled.Settings
    )
}

enum class HomeScreen(
    val route: String
) {
    Home("home")
}

enum class AppsScreen(
    val route: String
) {
    Home("apps")
}

enum class TerminalScreen(
    val route: String
) {
    Home("terminal")
}

enum class SettingsScreen(
    val route: String
) {
    Home("settings"),
    Logs("logs")
}

