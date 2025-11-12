package roro.stellar.manager.ui.navigation.routes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Stellar 应用导航路由配置
 * 
 * 集中管理应用的所有导航路由、页面标识和相关配置。
 * 提供类型安全的路由定义和统一的导航管理。
 */

/**
 * 主要界面导航枚举
 * 
 * 定义应用的主要功能模块和对应的导航信息，包括路由、标签和图标。
 * 用于底部导航栏的构建和页面切换控制。
 * 
 * @param route 导航路由标识符
 * @param label 显示在底部导航栏的文本标签
 * @param icon 未选中状态显示的图标
 * @param iconFilled 选中状态显示的填充图标
 */
enum class MainScreen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconFilled: ImageVector
) {
    /**
     * 主页模块
     * 
     * 应用主页，显示服务状态和快捷操作。
     * 作为用户进入应用后的默认页面。
     */
    Home(
        route = "home_graph",
        label = "启动",
        icon = Icons.Outlined.PlayArrow,
        iconFilled = Icons.Filled.PlayArrow
    ),

    /**
     * 授权应用模块
     * 
     * 管理已授权的应用列表和权限。
     */
    Apps(
        route = "apps_graph",
        label = "授权应用",
        icon = Icons.Outlined.Apps,
        iconFilled = Icons.Filled.Apps
    ),

    /**
     * 设置模块
     * 
     * 应用设置和配置页面。
     */
    Settings(
        route = "settings_graph",
        label = "设置",
        icon = Icons.Outlined.Settings,
        iconFilled = Icons.Filled.Settings
    )
}

/**
 * 主页导航枚举
 */
enum class HomeScreen(
    val route: String
) {
    /** 主页面 */
    Home("home")
}

/**
 * 授权应用页面导航枚举
 */
enum class AppsScreen(
    val route: String
) {
    /** 授权应用主页面 */
    Home("apps")
}

/**
 * 设置页面导航枚举
 */
enum class SettingsScreen(
    val route: String
) {
    /** 设置主页面 */
    Home("settings")
}

