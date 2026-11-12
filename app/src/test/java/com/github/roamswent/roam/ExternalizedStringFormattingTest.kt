package com.github.roamswent.roam

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ExternalizedStringFormattingTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun matchPercentHasExactXmlEscapingAndFormatsToSinglePercent() {
    assertEquals(
        "%1\$d%% match",
        xmlStringValue("monument_detail_match_percent"),
    )
    assertEquals("87% match", context.getString(R.string.monument_detail_match_percent, 87))
  }

  @Test
  fun factBulletFormatsWithBulletAndFact() {
    assertEquals("• %1\$s", xmlStringValue("monument_detail_fact_bullet"))
    assertEquals(
        "• Built in 1889",
        context.getString(R.string.monument_detail_fact_bullet, "Built in 1889"),
    )
  }

  @Test
  fun removeInterestSubstitutesInterestWithoutBackticks() {
    assertEquals(
        "Remove Architecture",
        context.getString(R.string.settings_remove_interest, "Architecture"),
    )
    assertEquals(
        -1,
        context.getString(R.string.settings_remove_interest, "Architecture").indexOf('`'),
    )
  }

  @Test
  @Config(qualifiers = "fr")
  fun removeInterestFrenchSubstitutesInterestWithoutBackticks_regressionDetectsBacktickOrPlaceholderDefect() {
    val formatted = context.getString(R.string.settings_remove_interest, "Architecture")
    assertEquals("Supprimer Architecture", formatted)
    assertEquals(-1, formatted.indexOf('`'))
  }

  @Test
  @Config(qualifiers = "fr")
  fun matchPercentFrenchFormatsIntegerToSinglePercent_regressionDetectsEscapingDefect() {
    val formatted = context.getString(R.string.monument_detail_match_percent, 87)
    assertEquals("87% de correspondance", formatted)
    assertTrue(formatted.contains("87%"))
    assertFalse(formatted.contains("%%"))
  }

  @Test
  @Config(qualifiers = "fr")
  fun factBulletFrenchFormatsWithSampleFact_regressionDetectsPlaceholderDefect() {
    val formatted = context.getString(R.string.monument_detail_fact_bullet, "Construit en 1889")
    assertEquals("• Construit en 1889", formatted)
  }

  @Test
  @Config(qualifiers = "fr")
  fun frenchResourceWithApostropheRendersWithoutLiteralBackslash_regressionDetectsEscapingDefect() {
    val rendered = context.getString(R.string.loading_error_generic)
    assertEquals("Une erreur s'est produite.", rendered)
    assertTrue(rendered.contains("'"))
    assertFalse(rendered.contains("\\"))
  }

  private fun xmlStringValue(name: String): String {
    val sourceRoot = File(System.getProperty("user.dir") ?: ".")
    val stringsFile =
        listOf(
                sourceRoot.resolve("app/src/main/res/values/strings.xml"),
                sourceRoot.resolve("src/main/res/values/strings.xml"),
            )
            .firstOrNull(File::isFile)
    checkNotNull(stringsFile) { "Production strings.xml not found" }
    val line = stringsFile.readLines().first { it.contains("<string name=\"$name\">") }
    return line.substringAfter("\">").substringBefore("</string>")
  }
}
