package com.github.meeplemeet.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Common dimensions for spacing, sizes, and typography across the app */
object Dimensions {
  // Spacing
  object Spacing {
    val none: Dp = 0.dp
    val extraSmall: Dp = 2.dp
    val small: Dp = 4.dp
    val medium: Dp = 8.dp
    val large: Dp = 12.dp
    val extraLarge: Dp = 16.dp
    val xxLarge: Dp = 24.dp
    val xxxLarge: Dp = 32.dp
  }

  // Padding
  object Padding {
    val tiny: Dp = 2.dp
    val small: Dp = 4.dp
    val medium: Dp = 8.dp
    val large: Dp = 12.dp
    val extraLarge: Dp = 16.dp
  }

  // Icon sizes
  object IconSize {
    val small: Dp = 16.dp
    val medium: Dp = 18.dp
    val standard: Dp = 20.dp
    val large: Dp = 24.dp
    val extraLarge: Dp = 28.dp
    val huge: Dp = 48.dp
    val giant: Dp = 64.dp
    val massive: Dp = 80.dp
  }

  // Avatar sizes
  object AvatarSize {
    val tiny: Dp = 24.dp
    val small: Dp = 32.dp
    val medium: Dp = 40.dp
    val large: Dp = 48.dp
    val extraLarge: Dp = 56.dp
  }

  // Button sizes
  object ButtonSize {
    val small: Dp = 32.dp
    val medium: Dp = 36.dp
    val standard: Dp = 48.dp
  }

  // Corner radius
  object CornerRadius {
    val none: Dp = 0.dp
    val small: Dp = 4.dp
    val medium: Dp = 8.dp
    val large: Dp = 12.dp
    val extraLarge: Dp = 16.dp
    val round: Dp = 24.dp
  }

  // Elevation
  object Elevation {
    val none: Dp = 0.dp
    val minimal: Dp = 0.5.dp
    val low: Dp = 1.dp
    val medium: Dp = 2.dp
    val high: Dp = 4.dp
    val extraHigh: Dp = 8.dp
  }

  // Text sizes
  object TextSize {
    val tiny: TextUnit = 11.sp
    val small: TextUnit = 12.sp
    val medium: TextUnit = 13.sp
    val standard: TextUnit = 14.sp
    val body: TextUnit = 15.sp
    val subtitle: TextUnit = 16.sp
    val title: TextUnit = 17.sp
    val heading: TextUnit = 18.sp
    val largeHeading: TextUnit = 20.sp
  }

  // Divider thickness
  object DividerThickness {
    val thin: Dp = 0.5.dp
    val standard: Dp = 1.dp
    val medium: Dp = 2.dp
    val thick: Dp = 8.dp
  }
}
