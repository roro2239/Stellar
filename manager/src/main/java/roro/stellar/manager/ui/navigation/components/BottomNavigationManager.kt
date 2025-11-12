package roro.stellar.manager.ui.navigation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import roro.stellar.manager.ui.navigation.routes.MainScreen

/**
 * 统一的底部导航栏管理器
 * 
 * 提供标准化的底部导航栏组件，管理导航项的状态、图标切换和页面跳转逻辑。
 */

/**
 * 标准底部导航栏组件
 * 
 * @param selectedIndex 当前选中的导航项索引
 * @param onItemClick 导航项点击回调，参数为点击的项目索引
 */
@Composable
fun StandardBottomNavigation(
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    NavigationBar {
        MainScreen.entries.forEachIndexed { index, screen ->
            val isSelected = selectedIndex == index
            
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (isSelected) screen.iconFilled else screen.icon,
                        contentDescription = screen.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { 
                    Text(text = screen.label) 
                },
                selected = isSelected,
                onClick = { onItemClick(index) }
            )
        }
    }
}

