package roro.stellar.manager.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import roro.stellar.manager.ui.theme.AppSpacing

data class ScreenConfig(
    val isLandscape: Boolean,
    val gridColumns: Int
)

val LocalScreenConfig = compositionLocalOf {
    ScreenConfig(isLandscape = false, gridColumns = 1)
}

@Composable
fun AdaptiveLayoutProvider(
    portraitColumns: Int = 1,
    landscapeColumns: Int = 2,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isLandscape) landscapeColumns else portraitColumns

    val screenConfig = ScreenConfig(
        isLandscape = isLandscape,
        gridColumns = gridColumns
    )

    CompositionLocalProvider(LocalScreenConfig provides screenConfig) {
        content()
    }
}

@Composable
fun AdaptiveLazyGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: LazyGridScope.() -> Unit
) {
    val screenConfig = LocalScreenConfig.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(screenConfig.gridColumns),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.cardSpacing),
        content = content
    )
}
