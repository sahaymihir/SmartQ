package com.example.smartqueue.ui.doctor.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartqueue.ui.components.AnimatedPrimaryButton
import com.example.smartqueue.ui.components.AnimatedStatCard
import com.example.smartqueue.ui.components.QueueItemCard
import com.example.smartqueue.ui.theme.SmartQTheme
import kotlinx.coroutines.delay

/**
 * Doctor Dashboard — Shows ML model metrics and queue
 * Real-time performance visualization with animated charts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDashboardScreen(
    doctorName: String = "Dr. Smith",
    queueItems: List<Triple<String, String, String>> = emptyList(),
    patientsServedCount: Int = 0,
    avgConsultationMinutesValue: Int = 8,
    triageAccuracyValue: Float = 0.95f,
    callNextLoading: Boolean = false,
    onCallNext: () -> Unit = {},
) {
    SmartQTheme {
        val queue = if (queueItems.isEmpty()) {
            listOf(Triple("Live queue is empty", "--", "normal"))
        } else {
            queueItems
        }

        // Animate metrics on load
        var displayAccuracy by remember { mutableStateOf(0f) }
        LaunchedEffect(Unit) {
            var target = 0f
            while (target < triageAccuracyValue) {
                target += 0.01f
                displayAccuracy = target
                delay(30)
            }
            displayAccuracy = triageAccuracyValue
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Doctor Dashboard",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                doctorName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                // Performance Stats
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AnimatedStatCard(
                            label = "Served",
                            value = patientsServedCount.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                        AnimatedStatCard(
                            label = "Avg Time",
                            value = "${avgConsultationMinutesValue}m",
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                        AnimatedStatCard(
                            label = "Queue",
                            value = queue.size.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp),
                        )
                    }
                }

                // ML Model Metrics Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                        ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "ML Model Performance",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = Icons.Filled.TrendingUp,
                                    contentDescription = "Trending up",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Triage Accuracy
                            PerformanceMetricBar(
                                label = "Triage Accuracy",
                                value = displayAccuracy,
                                target = triageAccuracyValue,
                            )

                            // Specialty Prediction
                            PerformanceMetricBar(
                                label = "Specialty Prediction",
                                value = 0.975f,
                                target = 1f,
                            )

                            // Patient Flow Optimization
                            PerformanceMetricBar(
                                label = "Queue Optimization",
                                value = 0.92f,
                                target = 1f,
                            )

                            Text(
                                "Model: XGBoost v3.1 | Last trained: 2 days ago",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Today's Performance
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Today's Goals",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Patients to see: 15",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "Progress: ${(patientsServedCount.toFloat() / 15 * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            ProportionalBar(
                                filled = patientsServedCount.toFloat(),
                                total = 15f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Queue Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Next in Queue",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                item {
                        AnimatedPrimaryButton(
                            text = "Call Next Patient",
                            onClick = {
                                onCallNext()
                            },
                            isLoading = callNextLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                }

                queue.forEachIndexed { index, (name, id, priority) ->
                    item {
                        QueueItemCard(
                            patientName = name,
                            patientId = id,
                            priority = priority,
                            index = index,
                            modifier = Modifier.fillMaxWidth(),
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

/**
 * Performance Metric Bar with animated fill
 */
@Composable
fun PerformanceMetricBar(
    label: String,
    value: Float,
    target: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall,
                ),
        ) {
            val fillWidth by animateFloatAsState(
                targetValue = value,
                animationSpec = tween(durationMillis = 1000),
                label = "metric_fill"
            )

            val barColor by animateColorAsState(
                targetValue = when {
                    fillWidth >= 0.95f -> Color(0xFF4CAF50)
                    fillWidth >= 0.85f -> Color(0xFF89CEFF)
                    fillWidth >= 0.75f -> Color(0xFFFF9500)
                    else -> Color(0xFFEB4141)
                },
                animationSpec = tween(durationMillis = 800),
                label = "metric_color"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(fillWidth)
                    .fillMaxSize()
                    .background(
                        barColor,
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
        }
    }
}

/**
 * Proportional progress bar
 */
@Composable
fun ProportionalBar(
    filled: Float,
    total: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(6.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
            ),
    ) {
        val proportion = (filled / total).coerceIn(0f, 1f)
        val animatedProportion by animateFloatAsState(
            targetValue = proportion,
            animationSpec = tween(durationMillis = 800),
            label = "progress_bar"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProportion)
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall,
                ),
        )
    }
}
