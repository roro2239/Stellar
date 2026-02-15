package roro.stellar.manager.ui.navigation.components

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun createTopAppBarScrollBehavior(
    topAppBarState: TopAppBarState
): TopAppBarScrollBehavior {
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        topAppBarState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardLargeTopAppBar(
    title: String,
    titleModifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val colors = if (isLandscape) {
        TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    } else {
        TopAppBarDefaults.largeTopAppBarColors()
    }

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
        scrollBehavior = scrollBehavior,
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val colors = if (isLandscape) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }

    androidx.compose.material3.TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = navigationIcon,
        actions = { actions() },
        colors = colors
    )
}

