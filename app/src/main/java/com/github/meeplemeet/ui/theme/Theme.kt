package com.github.meeplemeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Different themes the app can be in
enum class ThemeMode {
  LIGHT,
  DARK,
  HIGH_CONTRAST,
  SYSTEM_DEFAULT,
}

// Dark theme colors mapped to the Material3 color scheme
val LightColors =
    lightColorScheme(
        background = PrimaryLight,
        surface = SecondaryLight,
        outline = DividerLight,
        primary = FocusLight,
        secondary = AffirmativeLight,
        tertiary = NeutralLight,
        error = NegativeLight,
        onBackground = TextIconsLight,
        onSurface = TextIconsLight,
        onSurfaceVariant = TextIconsFadeLight,
        onPrimary = TextIconsLight,
        onSecondary = TextIconsLight,
        onError = TextIconsLight,
        onTertiary = TextIconsLight,
        inversePrimary = FocusLight)

// Dark theme colors mapped to the Material3 color scheme
val DarkColors =
    darkColorScheme(
        background = PrimaryDark,
        surface = SecondaryDark,
        outline = DividerDark,
        primary = FocusDark,
        secondary = AffirmativeDark,
        tertiary = NeutralDark,
        error = NegativeDark,
        onBackground = TextIconsDark,
        onSurface = TextIconsDark,
        onSurfaceVariant = TextIconsFadeDark,
        onPrimary = TextIconsDark,
        onSecondary = TextIconsDark,
        onError = TextIconsDark,
        onTertiary = TextIconsDark,
        inversePrimary = FocusDark)

// For now there's no high contrast colors defined as this may not be a feature we're that
// interested in developing.
// Either way, the code is modular such that we only have to add the colors here and we're good to
// go.
val HighContrastColors = darkColorScheme()

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
        themeMode == ThemeMode.HIGH_CONTRAST -> HighContrastColors
        isDark -> DarkColors
        else -> LightColors
      }

  MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes, content = content)
}
