package roro.stellar.manager.ui.navigation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * 统一的顶部应用栏管理器
 * 
 * 提供整个应用的TopAppBar状态管理和统一组件，确保所有页面的应用栏行为一致性。
 */

/**
 * 记忆化的顶部应用栏状态创建函数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTopAppBarState(): TopAppBarState {
    return rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(
            initialHeightOffsetLimit = -Float.MAX_VALUE,
            initialHeightOffset = 0f,
            initialContentOffset = 0f
        )
    }
}

/**
 * 创建统一的TopAppBar滚动行为
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun createTopAppBarScrollBehavior(
    topAppBarState: TopAppBarState
): TopAppBarScrollBehavior {
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        topAppBarState
    )
}

/**
 * 统一的大型顶部应用栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardLargeTopAppBar(
    title: String,
    titleModifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        title = { 
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                modifier = titleModifier
            ) 
        },
        navigationIcon = navigationIcon,
        actions = { actions() },
        scrollBehavior = scrollBehavior
    )
}

