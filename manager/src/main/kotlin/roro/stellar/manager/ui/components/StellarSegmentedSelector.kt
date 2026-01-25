package roro.stellar.manager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import roro.stellar.manager.ui.theme.AppShape
import roro.stellar.manager.ui.theme.AppSpacing
import kotlin.math.roundToInt

@Composable
fun <T> StellarSegmentedSelector(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = AppSpacing.selectorItemHeight
) {
    val density = LocalDensity.current
    val itemCount = items.size
    val currentIndex = items.indexOf(selectedItem).coerceIn(0, items.lastIndex)

    var innerWidth by remember { mutableIntStateOf(0) }
    val spacing = AppSpacing.selectorItemSpacing
    val spacingPx = with(density) { spacing.toPx() }

    val animatedIndex by animateFloatAsState(
        targetValue = currentIndex.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "selector_index"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = AppShape.shapes.cardMedium
            )
            .padding(AppSpacing.selectorPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size -> innerWidth = size.width },
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            repeat(itemCount) {
                Spacer(modifier = Modifier.weight(1f).height(itemHeight))
            }
        }

        if (innerWidth > 0) {
            val itemWidth = (innerWidth - spacingPx * (itemCount - 1)) / itemCount
            val offsetX = animatedIndex * (itemWidth + spacingPx)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .width(with(density) { itemWidth.toDp() })
                    .height(itemHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = AppShape.shapes.iconSmall
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = currentIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onItemSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}
