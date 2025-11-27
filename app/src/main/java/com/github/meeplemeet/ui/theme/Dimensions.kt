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
    val extraMedium: Dp = 10.dp
    val large: Dp = 12.dp
    val extraLarge: Dp = 16.dp
    val xLarge: Dp = 20.dp
    val xxLarge: Dp = 24.dp
    val xxxLarge: Dp = 32.dp
    val xxxxLarge: Dp = 60.dp
  }

  // Padding
  object Padding {
    val tiny: Dp = 2.dp
    val small: Dp = 4.dp
    val mediumSmall: Dp = 6.dp
    val medium: Dp = 8.dp
    val extraMedium: Dp = 10.dp
    val large: Dp = 12.dp
    val extraLarge: Dp = 16.dp
    val xLarge: Dp = 20.dp
    val xxLarge: Dp = 24.dp
    val xxxLarge: Dp = 32.dp
    val huge: Dp = 36.dp
    val giant: Dp = 56.dp
  }

  // Icon sizes
  object IconSize {
    val tiny: Dp = 11.dp
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
    val navigation: Dp = 42.dp
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
    val floating: Dp = 6.dp
    val extraHigh: Dp = 8.dp
    val xxHigh: Dp = 12.dp
    val raised: Dp = 3.dp
  }

  // Text sizes
  object TextSize {
    val tiny: TextUnit = 9.sp
    val small: TextUnit = 12.sp
    val medium: TextUnit = 13.sp
    val standard: TextUnit = 14.sp
    val body: TextUnit = 15.sp
    val subtitle: TextUnit = 16.sp
    val title: TextUnit = 17.sp
    val heading: TextUnit = 18.sp
    val largeHeading: TextUnit = 20.sp
    val xLarge: TextUnit = 32.sp
    val extraLarge: TextUnit = 36.sp
    val displayMedium: TextUnit = 56.sp
  }

  // Text indentation
  object TextIndent {
    val listIndent: TextUnit = 8.sp
  }

  // Line height
  object LineHeight {
    val standard: TextUnit = 16.sp
  }

  // Divider thickness
  object DividerThickness {
    val thin: Dp = 0.5.dp
    val standard: Dp = 1.dp
    val medium: Dp = 2.dp
    val strokeWidth: Dp = 3.dp
    val thick: Dp = 8.dp
  }

  // Component-specific widths
  object ComponentWidth {
    val spaceLabelWidth: Dp = 110.dp
    val fieldBoxWidth: Dp = 88.dp
    val inputFieldWidth: Dp = 80.dp
    val chipSize: Dp = 35.dp
    val pagerDotSpacing: Dp = 3.dp
    val pagerDotSmall: Dp = 6.dp
  }

  // List and container dimensions
  object ContainerSize {
    val maxListHeight: Dp = 600.dp
    val bottomSheetHeight: Dp = 400.dp
    val bottomSpacer: Dp = 100.dp
    val timeFieldHeight: Dp = 56.dp
    val iconButtonTouch: Dp = 40.dp
    val iconButtonSize: Dp = 48.dp
    val maxInputHeight: Dp = 120.dp
    val discussionCardMinHeight: Dp = 68.dp
    val discussionCardMaxHeight: Dp = 84.dp
    val pageImageSize: Dp = 400.dp
    val mapHeight: Dp = 300.dp
    val dividerHorizontalPadding: Dp = 30.dp
  }

  // Map-specific dimensions
  object MapDimensions {
    val markerRadius: Dp = 14.dp
    val offsetX268: Dp = 268.dp
    val offsetY208: Dp = 208.dp
    val offsetX90: Dp = 90.dp
    val offsetY80: Dp = 80.dp
    val offsetX240: Dp = 240.dp
    val offsetY240: Dp = 240.dp
    val offsetX180: Dp = 180.dp
    val offsetY140: Dp = 140.dp
    val offsetX70: Dp = 70.dp
    val offsetY260: Dp = 260.dp
    val offsetX280: Dp = 280.dp
    val offsetY180: Dp = 180.dp
  }

  // Badge dimensions
  object BadgeSize {
    val offsetX: Dp = 8.dp
    val offsetY: Dp = (-6).dp
    val minSize: Dp = 20.dp
  }

  // Button corner radius
  object ButtonRadius {
    val rounded: Dp = 20.dp
  }

  // Numeric constants
  object Numbers {
    const val searchResultLimit: Int = 5
    const val defaultTimeHour: Int = 19
    const val defaultTimeMinute: Int = 0
    const val defaultShopStartHour: Int = 7
    const val defaultShopStartMinute: Int = 30
    const val defaultShopEndHour: Int = 20
    const val defaultShopEndMinute: Int = 0
    const val singleLine: Int = 1
    const val quantityMin: Int = 1
    const val quantityMax: Int = 40
  }

  // Fractions (for width/height multipliers)
  object Fractions {
    const val topBarDivider: Float = 0.7f
  }

  // Multipliers
  object Multipliers {
    const val double: Int = 2
    const val quadruple: Int = 4
  }

  // Weight constants for Modifier.weight()
  object Weight {
    const val full: Float = 1f
  }

  // Alpha values
  object Alpha {
    const val full: Float = 1f
    const val opaque: Float = 0.65f
    const val editingBorder: Float = 1f
    const val readonlyBorder: Float = 0.3f
    const val pulseEffect: Float = 0.2f
    const val dialogIconTranslucent: Float = 0.7f
    const val dialogOverlayDark: Float = 0.9f
    const val dialogOverlayTransparent: Float = 0.0f
    const val dialogButtonTranslucent: Float = 0.2f
    const val dialogTextSemiTransparent: Float = 0.8f
  }

  // Rotation angles
  object Angles {
    const val none: Float = 0f
    const val expanded: Float = 180f
    const val collapsed: Float = 0f
  }
}
