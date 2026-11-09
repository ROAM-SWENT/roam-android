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
    val resourceId =
        context.resources.getIdentifier(
            "monument_detail_match_percent",
            "string",
            context.packageName,
        )

    assertEquals(
        "%1\$d%% match",
        xmlStringValue("monument_detail_match_percent"),
    )
    assertEquals("87% match", context.getString(resourceId, 87))
  }

  @Test
  fun factBulletFormatsWithBulletAndFact() {
    val resourceId =
        context.resources.getIdentifier(
            "monument_detail_fact_bullet",
            "string",
            context.packageName,
        )

    assertEquals("• %1\$s", xmlStringValue("monument_detail_fact_bullet"))
    assertEquals("• Built in 1889", context.getString(resourceId, "Built in 1889"))
  }

  @Test
  fun removeInterestSubstitutesInterestWithoutBackticks() {
    val resourceId =
        context.resources.getIdentifier("settings_remove_interest", "string", context.packageName)

    assertEquals("Remove Architecture", context.getString(resourceId, "Architecture"))
    assertEquals(
        -1,
        context.getString(resourceId, "Architecture").indexOf('`'),
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
