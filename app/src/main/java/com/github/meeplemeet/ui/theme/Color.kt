package com.github.meeplemeet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Define all the colors for light and dark themes (and potentially others) made to match with Figma
 * design
 */

/** Light theme colors */
val primaryLight = Color(0xFFf9f9f9) // Background
val secondaryLight = Color(0xFFe0e0e0) // Nav bars
val dividerLight = Color(0xFFc8c8c8) // For containers
val neutralLight = Color(0xFF5a7dd9) // Put emphasis but show a neutral tone
val affirmativeLight = Color(0xFF88ac4d) // Put emphasis and show a positive tone
val negativeLight = Color(0xFFc26451) // Put emphasis and show a negative tone
val focusLight = Color(0xFFd09c47) // Put emphasis and show focus
val textIconsLight = Color(0xFF000000) // Primary text and icons
val textIconsFadeLight =
    Color(0xCC000000) // Secondary text and icons (disabled state, less emphasis)

/** Dark theme colors */
val primaryDark = Color(0xff2c2c2c) // Background
val secondaryDark = Color(0xff505050) // Nav bars
val dividerDark = Color(0xff5e5e5e) // For containers
val neutralDark = Color(0xFF91abe3) // Put emphasis but show a neutral tone
val affirmativeDark = Color(0xFFb6cf86) // Put emphasis and show a positive tone
val negativeDark = Color(0xFFb15d4c) // Put emphasis and show a negative tone
val focusDark = Color(0xFFdaac54) // Put emphasis and show focus
val textIconsDark = Color(0xFFFFFFFF) // Primary text and icons
val textIconsFadeDark =
    Color(0xCCFFFFFF) // Secondary text and icons (disabled state, less emphasis)
