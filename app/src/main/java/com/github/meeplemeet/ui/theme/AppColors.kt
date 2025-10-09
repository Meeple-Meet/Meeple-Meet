package com.github.meeplemeet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Access to the app colors mapped to the current Material3 color scheme The idea is simple, to keep
 * things in order with Figma, we have the AppColors object which maps to the Material3 color
 * scheme. Material3 doesn't allow for attribute name changes so that's the workaround I've done. In
 * the code you Just need to call AppColors.Primary and it will select the right one for the theme
 * that will be used by the user
 */
object AppColors {
  val primary: Color
    @Composable get() = MaterialTheme.colorScheme.background

  val secondary: Color
    @Composable get() = MaterialTheme.colorScheme.surface

  val divider: Color
    @Composable get() = MaterialTheme.colorScheme.outline

  val neutral: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

  val affirmative: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

  val negative: Color
    @Composable get() = MaterialTheme.colorScheme.error

  val focus: Color
    @Composable get() = MaterialTheme.colorScheme.inversePrimary

  val textIcons: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

  val textIconsFade: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}
