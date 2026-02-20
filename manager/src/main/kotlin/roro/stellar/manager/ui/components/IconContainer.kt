package roro.stellar.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing

@Composable
fun IconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = AppShape.shapes.iconSmall
) {
    IconContainerBase(
        modifier = modifier,
        containerSize = AppSpacing.iconContainerSize,
        iconSize = AppSpacing.iconSize,
        containerColor = containerColor,
        iconColor = iconColor,
        shape = shape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(AppSpacing.iconSize)
        )
    }
}

@Composable
fun IconContainerBase(
    modifier: Modifier = Modifier,
    containerSize: Dp = AppSpacing.iconContainerSize,
    iconSize: Dp = AppSpacing.iconSize,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = AppShape.shapes.iconSmall,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
