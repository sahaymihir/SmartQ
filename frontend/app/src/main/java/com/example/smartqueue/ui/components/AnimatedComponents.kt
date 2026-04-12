package com.example.smartqueue.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartqueue.ui.theme.SmartQShapeConstants
import kotlinx.coroutines.delay

/**
 * Animated Primary Button — CTA with press animation
 */
@Composable
fun AnimatedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "button_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = SmartQShapeConstants.ButtonShape,
    ) {
        AnimatedContent(targetState = isLoading, label = "button_content") { loading ->
            if (loading) {
                PulsingDot()
            } else {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Animated Stat Card — Elevates on hover with value animation
 */
@Composable
fun AnimatedStatCard(
    label: String,
    value: String,
    icon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 2.dp,
        animationSpec = tween(durationMillis = 300),
        label = "card_elevation"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 300),
        label = "card_bg"
    )

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = backgroundColor,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevation,
        ),
        shape = SmartQShapeConstants.CardShape,
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                icon()
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Priority Badge — Animated entry with color based on priority
 */
@Composable
fun PriorityBadge(
    priority: String,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(priority) {
        isVisible = false
        delay(100)
        isVisible = true
    }

    val backgroundColor = when (priority.lowercase()) {
        "high" -> Color(0xFFEB4141)
        "medium" -> Color(0xFFFF9500)
        "normal" -> Color(0xFF89CEFF)
        "low" -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.tertiary
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.3f,
        animationSpec = tween(durationMillis = 400),
        label = "badge_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "badge_alpha"
    )

    Card(
        modifier = modifier
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.2f),
        ),
        shape = SmartQShapeConstants.ChipShape,
    ) {
        Text(
            text = priority.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = backgroundColor,
        )
    }
}

/**
 * Queue Item Card — Slides in with animation
 */
@Composable
fun QueueItemCard(
    patientName: String,
    patientId: String,
    priority: String,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(
                durationMillis = 400 + (index * 100),
                easing = LinearEasing,
            ),
        ) + fadeIn(animationSpec = tween(durationMillis = 400 + (index * 100))),
        label = "queue_item_entry",
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = SmartQShapeConstants.CardShape,
        ) {
            Box(Modifier.padding(12.dp)) {
                Text(
                    text = patientName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "ID: $patientId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PriorityBadge(priority = priority)
            }
        }
    }
}

/**
 * Pulsing Loading Dot — Animated indicator
 */
@Composable
fun PulsingDot(
    size: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onPrimary,
) {
    var iteration by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            iteration = (iteration + 1) % 3
        }
    }

    Box(modifier = Modifier.size(size * 3)) {
        repeat(3) { index ->
            val alpha by animateFloatAsState(
                targetValue = if (index == iteration) 1f else 0.3f,
                animationSpec = tween(durationMillis = 500),
                label = "dot_pulse_$index"
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .align(Alignment.Center)
                    .background(color.copy(alpha = alpha), shape = SmartQShapeConstants.ChipShape)
            )
        }
    }
}

/**
 * Expandable Section — With smooth height animation
 */
@Composable
fun ExpandableSection(
    title: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = SmartQShapeConstants.CardShape,
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Button(
                onClick = { onExpandChange(!isExpanded) },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(title)
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                label = "section_expand",
            ) {
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * Animated Separator Line
 */
@Composable
fun AnimatedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }

    val width by animateDpAsState(
        targetValue = if (isVisible) 100.dp else 0.dp,
        animationSpec = tween(durationMillis = 600),
        label = "divider_width"
    )

    Box(
        modifier = modifier
            .size(width, 1.dp)
            .background(color)
    )
}
