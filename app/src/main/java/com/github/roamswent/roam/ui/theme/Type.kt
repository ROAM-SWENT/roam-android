/*
 * Co-authored-by: Codex <codex@openai.com>
 * Co-authored-by: Google Antigravity <noreply@google.com>
 */
package com.github.roamswent.roam.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R

val Typography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 40.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
    )

@OptIn(ExperimentalTextApi::class)
private val guideFontFamily =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
          Font(
              R.font.inter_variable,
              weight = weight,
              variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
          )
        }
    )

internal val GuideTypography =
    Typography.copy(
        headlineMedium = guideTextStyle(25, 32, FontWeight.Bold),
        titleMedium = guideTextStyle(17, 22, FontWeight.SemiBold),
        titleSmall = guideTextStyle(16, 22, FontWeight.SemiBold),
        bodyLarge = guideTextStyle(14, 22),
        bodyMedium = guideTextStyle(13, 19),
        bodySmall = guideTextStyle(12, 16),
        labelLarge = guideTextStyle(14, 17, FontWeight.SemiBold),
        labelSmall = guideTextStyle(11, 16, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
    )

internal val Typography.guideOverview: TextStyle
  get() = bodyLarge.copy(lineHeight = 21.sp)

private fun guideTextStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = guideFontFamily,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = 0.sp,
        lineHeight = lineHeight.sp,
        lineHeightStyle =
            LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

val DockLabelFontSize = 11.sp
val DockLabelMinFontSize = 8.5.sp
