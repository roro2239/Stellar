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
import roro.stellar.manager.R

enum class MainScreen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconFilled: ImageVector
) {
    Home(
        route = "home_graph",
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.PlayArrow,
        iconFilled = Icons.Filled.PlayArrow
    ),

    Apps(
        route = "apps_graph",
        labelRes = R.string.nav_apps,
        icon = Icons.Outlined.Apps,
        iconFilled = Icons.Filled.Apps
    ),

    Terminal(
        route = "terminal_graph",
        labelRes = R.string.nav_terminal,
        icon = Icons.Outlined.Terminal,
        iconFilled = Icons.Filled.Terminal
    ),

    Settings(
        route = "settings_graph",
        labelRes = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        iconFilled = Icons.Filled.Settings
    )
}

