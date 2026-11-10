package com.github.roamswent.roam

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
