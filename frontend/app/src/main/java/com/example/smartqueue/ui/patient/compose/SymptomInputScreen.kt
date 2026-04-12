package com.example.smartqueue.ui.patient.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartqueue.ui.components.AnimatedPrimaryButton
import com.example.smartqueue.ui.components.PulsingDot
import com.example.smartqueue.ui.theme.SmartQTheme
import kotlinx.coroutines.delay

/**
 * Symptom Input Screen — Interactive form with animated responses
 * Guides patient through symptom entry with dynamic form behaviors
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomInputScreen(
    onSubmit: (chiefComplaint: String, symptoms: List<String>, severity: Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
) {
    SmartQTheme {
        var chiefComplaint by remember { mutableStateOf("") }
        var selectedSymptoms by remember { mutableStateOf<Set<String>>(emptySet()) }
        var severity by remember { mutableStateOf(0) }
        var isSubmitting by remember { mutableStateOf(false) }
        var formStep by remember { mutableStateOf(0) } // 0: Chief complaint, 1: Symptoms, 2: Severity

        val commonSymptoms = listOf(
            "Fever", "Cough", "Headache", "Body Ache", "Sore Throat",
            "Nausea", "Shortness of Breath", "Chest Pain", "Diarrhea", "Fatigue"
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Describe Your Symptoms",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                "Step ${formStep + 1} of 3",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
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
                // Progress Indicator
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(3) { step ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .background(
                                        if (step <= formStep) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ),
                            )
                        }
                    }
                }

                // Step: Chief Complaint
                item {
                    AnimatedVisibility(
                        visible = formStep == 0,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        label = "chief_complaint_step",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "What is your main concern?",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // Text input field
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                BasicTextField(
                                    value = chiefComplaint,
                                    onValueChange = { chiefComplaint = it },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (chiefComplaint.isEmpty()) {
                                            Text(
                                                "e.g., Severe headache and fever",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        innerTextField()
                                    },
                                )
                            }

                            AnimatedPrimaryButton(
                                text = "Next",
                                onClick = { if (chiefComplaint.isNotEmpty()) formStep = 1 },
                                enabled = chiefComplaint.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Step: Select Symptoms
                item {
                    AnimatedVisibility(
                        visible = formStep == 1,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        label = "symptoms_step",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Select any other symptoms:",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // Symptom grid
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                commonSymptoms.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        row.forEach { symptom ->
                                            SymptomChip(
                                                text = symptom,
                                                isSelected = symptom in selectedSymptoms,
                                                onClick = {
                                                    selectedSymptoms = if (symptom in selectedSymptoms) {
                                                        selectedSymptoms - symptom
                                                    } else {
                                                        selectedSymptoms + symptom
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) {
                                            Box(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AnimatedPrimaryButton(
                                    text = "Back",
                                    onClick = { formStep = 0 },
                                    modifier = Modifier.weight(1f),
                                )
                                AnimatedPrimaryButton(
                                    text = "Next",
                                    onClick = { formStep = 2 },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Step: Severity
                item {
                    AnimatedVisibility(
                        visible = formStep == 2,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        label = "severity_step",
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "How severe is your condition?",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // Severity slider
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                repeat(5) { level ->
                                    SeverityOption(
                                        level = level + 1,
                                        label = when (level) {
                                            0 -> "Mild - Can manage at home"
                                            1 -> "Moderate - Need medical advice"
                                            2 -> "Significant - Should see doctor soon"
                                            3 -> "Severe - Needs urgent attention"
                                            else -> "Critical - Immediate care needed"
                                        },
                                        isSelected = severity == level + 1,
                                        onClick = { severity = level + 1 },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AnimatedPrimaryButton(
                                    text = "Back",
                                    onClick = { formStep = 1 },
                                    modifier = Modifier.weight(1f),
                                )
                                AnimatedPrimaryButton(
                                    text = "Submit",
                                    onClick = {
                                        isSubmitting = true
                                        onSubmit(chiefComplaint, selectedSymptoms.toList(), severity)
                                    },
                                    isLoading = isSubmitting,
                                    enabled = severity > 0,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
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
 * Animated Symptom Chip
 */
@Composable
fun SymptomChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "chip_bg"
    )

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "chip_scale"
    )

    Card(
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = true, onClick = onClick)
            .androidx.compose.foundation.scale(scale),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/**
 * Severity Selection Option
 */
@Composable
fun SeverityOption(
    level: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val severityColor = when (level) {
        1 -> Color(0xFF4CAF50) // Green - Mild
        2 -> Color(0xFF89CEFF) // Blue - Moderate
        3 -> Color(0xFFFF9500) // Orange - Significant
        4 -> Color(0xFFEB4141) // Red - Severe
        5 -> Color(0xFF8B0000) // Dark Red - Critical
        else -> MaterialTheme.colorScheme.primary
    }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        label = "severity_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = true, onClick = onClick)
            .androidx.compose.foundation.scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                severityColor.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSelected) 24.dp else 16.dp)
                    .background(
                        severityColor,
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Level $level",
                    style = MaterialTheme.typography.labelLarge,
                    color = severityColor,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
