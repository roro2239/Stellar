package com.stellar.demo.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DemoBottomNavigation(
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    NavigationBar {
        DemoScreen.entries.forEachIndexed { index, screen ->
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
