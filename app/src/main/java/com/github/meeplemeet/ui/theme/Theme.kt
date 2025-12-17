package com.github.meeplemeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import kotlinx.serialization.Serializable

/**
 * Different themes the app can be in LIGHT: Light theme DARK: Dark theme HIGH_CONTRAST: High
 * contrast theme (not implemented yet) SYSTEM_DEFAULT: Follow the system theme
 */
@Serializable
enum class ThemeMode {
  LIGHT,
  DARK,
  HIGH_CONTRAST,
  SYSTEM_DEFAULT,
}

/** Dark theme colors mapped to the Material3 color scheme */
val lightColors =
    lightColorScheme(
        background = primaryLight,
        surface = secondaryLight,
        outline = dividerLight,
        primary = focusLight,
        secondary = affirmativeLight,
        tertiary = neutralLight,
        error = negativeLight,
        onBackground = textIconsLight,
        onSurface = textIconsLight,
        onSurfaceVariant = textIconsFadeLight,
        onPrimary = textIconsLight,
        onSecondary = textIconsLight,
        onError = textIconsLight,
        onTertiary = textIconsLight,
        inversePrimary = focusLight)

/** Dark theme colors mapped to the Material3 color scheme */
val darkColors =
    darkColorScheme(
        background = primaryDark,
        surface = secondaryDark,
        outline = dividerDark,
        primary = focusDark,
        secondary = affirmativeDark,
        tertiary = neutralDark,
        error = negativeDark,
        onBackground = textIconsDark,
        onSurface = textIconsDark,
        onSurfaceVariant = textIconsFadeDark,
        onPrimary = textIconsDark,
        onSecondary = textIconsDark,
        onError = textIconsDark,
        onTertiary = textIconsDark,
        inversePrimary = focusDark)

/**
 * Currently, we do NOT have a high contrast theme defined, this is a placeholder It is however
 * defined to make the code modular, in case we decide to add it/another theme in the future
 */
val highContrastColors = darkColorScheme()

/**
 * CompositionLocal to provide the current theme mode (Dark/Light) to the app. This allows
 * components to check if the app is currently in dark mode, independent of the system setting.
 */
val LocalThemeIsDark = compositionLocalOf { false }

/**
 * The theme entry point, the app should be wrapped in this composable.
 *
 * @param themeMode The theme mode to use, defaults to [ThemeMode.SYSTEM_DEFAULT]
 * @param content The content to be wrapped in the theme
 */
@Composable
fun AppTheme(themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT, content: @Composable () -> Unit) {
  val isDark =
      when (themeMode) {
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        else -> false
      }

  val colors =
      when {
        themeMode == ThemeMode.HIGH_CONTRAST -> highContrastColors
        isDark -> darkColors
        else -> lightColors
      }

  CompositionLocalProvider(LocalThemeIsDark provides isDark) {
    MaterialTheme(
        colorScheme = colors, typography = appTypography, shapes = appShapes, content = content)
  }
}
