package com.github.meeplemeet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Set of Material shape styles to start with
val Shapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp))

// Aliases if ever needed
object ComponentShapes {
  val None = RoundedCornerShape(0.dp)
  val Small = RoundedCornerShape(4.dp)
  val Medium = RoundedCornerShape(8.dp)
  val Large = RoundedCornerShape(12.dp)
}
