package com.github.meeplemeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Messaging and forum-specific colors that adapt to light and dark themes */
object MessagingColors {
  // WhatsApp-style colors
  val whatsappGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF00A884) else Color(0xFF25D366)

  val whatsappGreenLight: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF005C4B) else Color(0xFFDCF8C6)

  val whatsappLightGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1F3933) else Color(0xFFE1F5DC)

  // Reddit-style colors
  val redditOrange: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF5722) else Color(0xFFFF4500)

  val redditBlue: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF5E9AFF) else Color(0xFF1A73E8)

  val redditBlueBg: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1A2F4D) else Color(0xFFE8F0FE)

  // Background and surface colors
  val messagingBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0B141A) else Color(0xFFF5F5F5)

  val messagingSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E2C35) else Color(0xFFFFFFFF)

  val inputBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A3942) else Color(0xFFF5F5F5)

  // Text colors
  val primaryText: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE8E8E8) else Color(0xFF1C1C1E)

  val secondaryText: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8E979F) else Color(0xFF8E8E93)

  val metadataText: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6B7C87) else Color(0xFF667781)

  // Dividers
  val divider: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A3942) else Color(0xFFE5E5EA)

  val thickDivider: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF151E26) else Color(0xFFE5E5EA)

  // Bubble colors
  val messageBubbleOwn: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF005C4B) else Color(0xFFDCF8C6)

  val messageBubbleOther: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E2C35) else Color(0xFFFFFFFF)

  // Avatar placeholder
  val avatarBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFBD6B47) else Color(0xFFFF4500)

  // Poll/selection colors
  val selectionBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1F3933) else Color(0xFFE8F5E9)

  val neutralBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF2A3942) else Color(0xFFF5F5F5)

  // Icon tints
  val iconTint: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8E979F) else Color(0xFF667781)
}
