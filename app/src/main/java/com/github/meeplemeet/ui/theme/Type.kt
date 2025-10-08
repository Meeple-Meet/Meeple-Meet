package com.github.meeplemeet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.meeplemeet.R

/**
 * Font family for the app, using the Inter font since it's the one Figma uses
 * (https://fonts.google.com/specimen/Inter)
 */
val appFontFamily =
    FontFamily(
        fonts =
            listOf(
                Font(R.font.inter_thin, FontWeight.Thin),
                Font(R.font.inter_regular, FontWeight.Normal),
                Font(R.font.inter_medium, FontWeight.Medium),
                Font(R.font.inter_semibold, FontWeight.SemiBold)))

/** Used for big screen titles, such as the login/signup screen */
val displayLarge =
    TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 50.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp)

/** Used for main body text */
val bodyMedium =
    TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp)

/**
 * Used for secondary body text, text that is usually less important or that should be read after
 * the main body text
 */
val bodySmall =
    TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp)

/** Used for small text, think of captions */
val labelSmall =
    TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp)

/** Set of Material typography styles to start with */
val appTypography =
    Typography(
        displayLarge = displayLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelSmall = labelSmall)
