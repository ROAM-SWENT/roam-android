package com.github.roamswent.roam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypographyTest {
  @get:Rule val composeTestRule = createComposeRule()

  private data class SlotExpectation(
      val name: String,
      val selector: (MaterialTypography) -> TextStyle,
      val fontSize: TextUnit,
      val lineHeight: TextUnit,
      val fontWeight: FontWeight,
      val letterSpacing: TextUnit,
  )

  private val expectedScale =
      listOf(
          SlotExpectation(
              name = "displaySmall",
              selector = MaterialTypography::displaySmall,
              fontSize = 40.sp,
              lineHeight = 52.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "headlineMedium",
              selector = MaterialTypography::headlineMedium,
              fontSize = 28.sp,
              lineHeight = 36.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "headlineSmall",
              selector = MaterialTypography::headlineSmall,
              fontSize = 26.sp,
              lineHeight = 34.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "titleLarge",
              selector = MaterialTypography::titleLarge,
              fontSize = 22.sp,
              lineHeight = 28.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "titleMedium",
              selector = MaterialTypography::titleMedium,
              fontSize = 20.sp,
              lineHeight = 26.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "titleSmall",
              selector = MaterialTypography::titleSmall,
              fontSize = 18.sp,
              lineHeight = 24.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "bodyLarge",
              selector = MaterialTypography::bodyLarge,
              fontSize = 16.sp,
              lineHeight = 24.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.5.sp,
          ),
          SlotExpectation(
              name = "bodyMedium",
              selector = MaterialTypography::bodyMedium,
              fontSize = 15.sp,
              lineHeight = 20.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "bodySmall",
              selector = MaterialTypography::bodySmall,
              fontSize = 14.sp,
              lineHeight = 20.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "labelLarge",
              selector = MaterialTypography::labelLarge,
              fontSize = 14.sp,
              lineHeight = 20.sp,
              fontWeight = FontWeight.Medium,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "labelMedium",
              selector = MaterialTypography::labelMedium,
              fontSize = 13.sp,
              lineHeight = 18.sp,
              fontWeight = FontWeight.Medium,
              letterSpacing = 0.sp,
          ),
          SlotExpectation(
              name = "labelSmall",
              selector = MaterialTypography::labelSmall,
              fontSize = 12.sp,
              lineHeight = 16.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.sp,
          ),
      )

  @Test
  fun materialThemeTypographyExposesAllTwelveScaleSlots() {
    var resolvedTypography: MaterialTypography? = null

    composeTestRule.setContent {
      SampleAppTheme(dynamicColor = false) { resolvedTypography = MaterialTheme.typography }
    }

    composeTestRule.runOnIdle {
      val actualTypography = requireNotNull(resolvedTypography)
      assertEquals(Typography, actualTypography)
      assertEquals(12, expectedScale.size)

      expectedScale.forEach { slot ->
        val actual = slot.selector(actualTypography)
        assertEquals("${slot.name}.fontFamily", FontFamily.Default, actual.fontFamily)
        assertEquals("${slot.name}.fontSize", slot.fontSize, actual.fontSize)
        assertEquals("${slot.name}.lineHeight", slot.lineHeight, actual.lineHeight)
        assertEquals("${slot.name}.fontWeight", slot.fontWeight, actual.fontWeight)
        assertEquals("${slot.name}.letterSpacing", slot.letterSpacing, actual.letterSpacing)
      }
    }
  }
}
