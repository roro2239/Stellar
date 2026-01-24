package com.stellar.demo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

enum class DemoScreen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconFilled: ImageVector
) {
    HOME(
        route = "home",
        label = "首页",
        icon = Icons.Outlined.Home,
        iconFilled = Icons.Filled.Home
    ),
    FUNCTIONS(
        route = "functions",
        label = "功能",
        icon = Icons.Outlined.Terminal,
        iconFilled = Icons.Filled.Terminal
    ),
    SETTINGS(
        route = "settings",
        label = "设置",
        icon = Icons.Outlined.Settings,
        iconFilled = Icons.Filled.Settings
    )
}
