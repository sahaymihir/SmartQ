package com.example.smartqueue.ui.admin.compose

import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.smartqueue.R
import com.example.smartqueue.ui.components.AnimatedPrimaryButton
import com.example.smartqueue.ui.components.AnimatedStatCard
import com.example.smartqueue.ui.components.PriorityBadge
import com.example.smartqueue.ui.components.QueueItemCard
import com.example.smartqueue.ui.theme.SmartQTheme
import kotlinx.coroutines.delay

/**
 * Admin Dashboard Screen — Compose Material 3 with Heavy Animations
 * - Animated stat cards with hover effects
 * - Priority badges with scale animations
 * - Queue items that slide in sequentially
 * - Lottie animations for loading states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminName: String = "Dr. Smith",
    queueItems: List<Triple<String, String, String>> = emptyList(),
    queueCountValue: Int = 0,
    completedCountValue: Int = 0,
    avgWaitValue: String = "0m",
    mlLogs: List<String> = emptyList(),
    pausedState: Boolean = false,
    isQueueLoading: Boolean = false,
    isMlRefreshing: Boolean = false,
    onLogout: () -> Unit = {},
    onCallNext: () -> Unit = {},
    onPauseToggle: () -> Unit = {},
    onRefreshML: () -> Unit = {},
) {
    SmartQTheme {
        val queue = if (queueItems.isEmpty()) {
            listOf(
                Triple("Live queue is empty", "--", "normal"),
            )
        } else {
            queueItems
        }
        val logs = if (mlLogs.isEmpty()) {
            listOf(
                "No live ML logs yet",
            )
        } else {
            mlLogs
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Admin Dashboard",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                adminName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.Filled.Logout,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Stats Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedStatCard(
                            label = "Waiting",
                            value = queueCountValue.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                        AnimatedStatCard(
                            label = "Completed",
                            value = completedCountValue.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                        AnimatedStatCard(
                            label = "Avg Wait",
                            value = avgWaitValue,
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                    }
                }

                // Action Buttons
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedPrimaryButton(
                            text = if (pausedState) "▶ Resume" else "⏸ Pause",
                            onClick = {
                                onPauseToggle()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedPrimaryButton(
                            text = "Call Next",
                            onClick = {
                                onCallNext()
                            },
                            isLoading = isQueueLoading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Currently Serving Section
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.medium,
                            )
                            .padding(12.dp),
                    ) {
                        Column {
                            Text(
                                "Currently Serving",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Patient ID: P024",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Queue Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Queue (${queueCountValue})",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        PriorityBadge("High", priority = "High")
                    }
                }

                // Queue Items with animations
                itemsIndexed(queue) { index, (name, id, priority) ->
                    QueueItemCard(
                        patientName = name,
                        patientId = id,
                        priority = priority,
                        index = index,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ML Ops Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "ML Ops Health",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = {
                                onRefreshML()
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh ML Ops",
                                tint = if (isMlRefreshing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                // ML Ops Status Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .padding(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Status: OPERATIONAL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color(0xFF4CAF50),
                                            shape = MaterialTheme.shapes.extraSmall,
                                        ),
                                )
                            }

                            // Success Rate
                            Text(
                                "Success Rate: 99.8%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // Recent Logs
                            logs.forEachIndexed { index, log ->
                                Text(
                                    log,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item {
                    Box(Modifier.height(32.dp)) // Spacing at bottom
                }
            }
        }
    }
}

fun color(value: Long) = androidx.compose.ui.graphics.Color(value)
