package com.example.smartqueue.ui.patient.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import com.example.smartqueue.ui.components.AnimatedPrimaryButton
import com.example.smartqueue.ui.components.AnimatedStatCard
import com.example.smartqueue.ui.components.PriorityBadge
import com.example.smartqueue.ui.components.QueueItemCard
import com.example.smartqueue.ui.theme.SmartQTheme
import kotlinx.coroutines.delay

/**
 * Patient Queue Screen — Shows patient position in queue
 * Real-time updates with animated position changes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientQueueScreen(
    patientName: String = "John Patient",
    onViewDetails: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    SmartQTheme {
        var queuePosition by remember { mutableStateOf(3) }
        var queueLength by remember { mutableStateOf(8) }
        var estimatedWaitMin by remember { mutableStateOf(12) }
        var isRefreshing by remember { mutableStateOf(false) }
        var priority by remember { mutableStateOf("normal") }

        // Mock queue data ahead
        val queueAhead = remember {
            listOf(
                Triple("Patient A", "P101", "high"),
                Triple("Patient B", "P102", "medium"),
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(5000)
                // Simulate queue updates
                if (queuePosition > 1) {
                    queuePosition--
                    estimatedWaitMin = (queuePosition - 1) * 3 + 6
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Your Queue Position",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                patientName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isRefreshing = true
                                onRefresh()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = if (isRefreshing) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                // Primary Position Card (Animated)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.large,
                            )
                            .padding(20.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnimatedContent(targetState = queuePosition, label = "position") { pos ->
                                Text(
                                    "#$pos",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 48.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            Text(
                                "You are here",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            PriorityBadge(priority = priority)
                        }
                    }
                }

                // Stats Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedStatCard(
                            label = "Ahead",
                            value = (queuePosition - 1).toString(),
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                        AnimatedStatCard(
                            label = "Est. Wait",
                            value = "${estimatedWaitMin}m",
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                        AnimatedStatCard(
                            label = "Total",
                            value = queueLength.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                    }
                }

                // Queue Status Section
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                            )
                            .padding(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Queue Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                            shape = MaterialTheme.shapes.extraSmall,
                                        ),
                                )
                            }

                            Text(
                                "Current Doctor: Dr. Smith",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Department: General",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Patients Ahead Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Ahead of You (${queueAhead.size})",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                itemsIndexed(queueAhead) { index, (name, id, prio) ->
                    QueueItemCard(
                        patientName = name,
                        patientId = id,
                        priority = prio,
                        index = index,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Action Buttons
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedPrimaryButton(
                            text = "View Doctor Info",
                            onClick = onViewDetails,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            "Last updated: Just now",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }

                item {
                    Box(Modifier.height(32.dp))
                }
            }
        }
    }
}
