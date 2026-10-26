package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val TaalaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), // 20 dp chips & small cards
    large = RoundedCornerShape(28.dp),  // 28 dp cards & sheets
    extraLarge = RoundedCornerShape(32.dp)
)
