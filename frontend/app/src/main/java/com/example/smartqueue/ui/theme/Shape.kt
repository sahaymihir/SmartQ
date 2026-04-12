package com.example.smartqueue.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Minimal Modern Rectangular Shapes for SmartQ
 * Rectangular feeling with subtle rounded corners
 */
val SmartQShapes = Shapes(
    // Extra small — Minimal rounding for buttons, chips
    extraSmall = RoundedCornerShape(4.dp),
    
    // Small — Pills, small cards
    small = RoundedCornerShape(8.dp),
    
    // Medium — Standard cards, dialogs
    medium = RoundedCornerShape(12.dp),
    
    // Large — Large containers, bottom sheets
    large = RoundedCornerShape(16.dp),
    
    // Extra large — Maximum rounding (rarely used)
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Shape constants for direct use in modifiers
 */
object SmartQShapeConstants {
    val ButtonShape = RoundedCornerShape(8.dp)
    val CardShape = RoundedCornerShape(12.dp)
    val LargeCardShape = RoundedCornerShape(16.dp)
    val InputShape = RoundedCornerShape(8.dp)
    val DialogShape = RoundedCornerShape(12.dp)
    val ChipShape = RoundedCornerShape(4.dp)
    val BottomSheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
