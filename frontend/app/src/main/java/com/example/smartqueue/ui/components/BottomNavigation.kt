package com.example.smartqueue.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.example.smartqueue.ui.navigation.BottomNavDestination
import com.example.smartqueue.ui.theme.SmartQShapeConstants

/**
 * Animated Bottom Navigation Bar
 * Slides in from bottom with animated item transitions
 */
@Composable
fun AnimatedBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    isVisible: Boolean = true,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
        ) + fadeOut(),
        label = "bottom_nav_visibility",
    ) {
        NavigationBar(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            BottomNavDestination.values().forEach { destination ->
                val isSelected = currentRoute == destination.route
                val icon = when (destination) {
                    BottomNavDestination.HOME -> Icons.Filled.Home
                    BottomNavDestination.QUEUE -> Icons.Filled.LocationOn
                    BottomNavDestination.PROFILE -> Icons.Filled.Person
                }

                AnimatedNavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(destination.route) },
                    icon = icon,
                    label = destination.label,
                )
            }
        }
    }
}

/**
 * Individual Bottom Nav Item with animation
 */
@Composable
fun RowScope.AnimatedNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "nav_item_scale"
    )

    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "nav_item_bg"
    )

    NavigationBarItem(
        modifier = modifier
            .androidx.compose.foundation.background(
                backgroundColor,
                shape = SmartQShapeConstants.CardShape,
            )
            .androidx.compose.foundation.scale(scale),
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = androidx.compose.ui.Modifier.androidx.compose.animation.core.animateAsState(
                    targetValue = if (selected) 1.2f else 1f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "icon_scale"
                ).value.let { androidx.compose.ui.Modifier }
            )
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
