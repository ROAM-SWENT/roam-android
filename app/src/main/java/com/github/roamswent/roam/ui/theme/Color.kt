// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Mistral Vibe <vibe@mistral.ai>
package com.github.roamswent.roam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val ScanTeal = Color(0xFF2A9D8F)
val HomeActiveTab = Color(0xFF1B433E)
val HomeInactiveTab = Color(0xFF6E7673)
val ScreenBackground = Color(0xFFF2F4F3)
val DockOutline = Color(0xFFDDE3E0)
val DockBarBackground = Color.White
val DockScanIcon = Color.White
val ShutterHalo = Color(0x99000000)
val ShutterWhite = Color.White
val CameraCaptureOverlay = Color.Black.copy(alpha = 0.35f)
val LoadingTextPrimary = Color(0xFF1B2B27)
val LoadingTextSecondary = Color(0xFF6B7773)
val LoadingProgressAccent = Color(0xFF3FA08A)
val LoadingProgressTrack = Color(0xFFDDE3E0)
val LoadingErrorText = Color(0xFFB3261E)
val HomeTextPrimary = Color(0xFF181D1B)
val HomeTextSecondary = Color(0xFF6E7673)
val HomeAccentGreen = Color(0xFF1B433E)
val CollectionPlaceholder = Color(0xFFD5DDD9)
val CollectionPlaceholderIcon = Color(0xFF8A9A94)

/*
 * Theme-aware colors for the app's own light palette. Each one resolves to the light brand color
 * in light mode and to a color scheme role in dark mode, so screens never pick a light-only color.
 */

/** Background behind a full screen. */
val MaterialTheme.screenBackground: Color
  @Composable get() = if (isDark) colorScheme.background else ScreenBackground

/** Container of a card floating over a screen or a map. */
val MaterialTheme.cardContainer: Color
  @Composable get() = if (isDark) colorScheme.surfaceVariant else Color.White

/** Translucent circle behind an icon button drawn over a photo. */
val MaterialTheme.photoButtonContainer: Color
  @Composable
  get() = (if (isDark) colorScheme.surface else Color.White).copy(alpha = PHOTO_BUTTON_ALPHA)

/** Accent tint for icons drawn on a surface or on [photoButtonContainer]. */
val MaterialTheme.accentIcon: Color
  @Composable get() = if (isDark) LoadingProgressAccent else HomeAccentGreen

/** Secondary text on a surface, such as a hint or an empty-state message. */
val MaterialTheme.secondaryText: Color
  @Composable get() = if (isDark) colorScheme.onSurfaceVariant else HomeTextSecondary

private const val PHOTO_BUTTON_ALPHA = 0.85f
