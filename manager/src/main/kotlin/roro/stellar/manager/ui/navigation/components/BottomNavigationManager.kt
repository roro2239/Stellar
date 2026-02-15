package roro.stellar.manager.ui.navigation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import roro.stellar.manager.ui.navigation.routes.MainScreen

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

@Composable
fun StandardNavigationRail(
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MainScreen.entries.forEachIndexed { index, screen ->
                val isSelected = selectedIndex == index

                NavigationRailItem(
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
}

