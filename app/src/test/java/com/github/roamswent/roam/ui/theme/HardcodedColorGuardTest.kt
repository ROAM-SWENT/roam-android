// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.theme

import com.github.roamswent.roam.ScanResult
import com.github.roamswent.roam.SourceScanTestUtils.assertMissingDirectoryProducesSkip
import com.github.roamswent.roam.SourceScanTestUtils.assertProductionUiSourcesPass
import com.github.roamswent.roam.SourceScanTestUtils.findLineMatches
import com.github.roamswent.roam.SourceScanTestUtils.scanKotlinSources
import com.github.roamswent.roam.SourceScanTestUtils.stripComments
import com.github.roamswent.roam.SourceScanTestUtils.withCopiedUiFile
import com.github.roamswent.roam.SourceScanTestUtils.withScratchKotlinFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fails the build on a new raw color (`Color.White`, `Color(0xFF...)`, ...) in production UI code:
 * a raw color does not follow dark mode. Colors belong in `ui/theme/Color.kt`.
 */
class HardcodedColorGuardTest {
  @Test
  fun productionUiSourcesContainNoUnallowlistedColorLiterals() {
    assertProductionUiSourcesPass("Hardcoded UI color violations", ColorScanner::scan)
  }

  @Test
  fun rawWhiteInCopiedExploreScreenIsFlagged() {
    withCopiedUiFile("explore/ExploreScreen.kt") { rootDir, file ->
      val legacyCount = ColorScanner.legacyFiles.getValue("explore/ExploreScreen.kt")
      file.appendText("val scratch = Color.White\n")

      val result = ColorScanner.scan(rootDir)

      assertEquals(1, result.violations.size)
      assertTrue(
          result.violations.single(),
          result.violations
              .single()
              .startsWith(
                  "explore/ExploreScreen.kt: expected $legacyCount legacy literal(s), " +
                      "found ${legacyCount + 1}"
              ),
      )
    }
  }

  @Test
  fun hexColorInNewFileIsFlagged() {
    withScratchKotlinFile("Sample.kt", "val a = 1\nval b = Color(0xFF1B433E)\n") { rootDir ->
      val result = ColorScanner.scan(rootDir, emptyMap())

      assertEquals(listOf("Sample.kt:2: Color(0xFF1B433E)"), result.violations)
    }
  }

  @Test
  fun themeAwareAndTransparentColorsAreAllowed() {
    withScratchKotlinFile(
        "Sample.kt",
        "val a = MaterialTheme.colorScheme.surface\n" +
            "val b = MaterialTheme.cardContainer\n" +
            "val c = Color.Transparent\n" +
            "// Color.White in a comment\n",
    ) { rootDir ->
      val result = ColorScanner.scan(rootDir, emptyMap())

      assertTrue(result.violations.toString(), result.violations.isEmpty())
    }
  }

  @Test
  fun colorsInThemeFolderAreIgnored() {
    withCopiedUiFile("theme/Color.kt") { rootDir, file ->
      file.appendText("val ScratchToken = Color(0xFF123456)\n")

      val result = ColorScanner.scan(rootDir)

      assertTrue(result.violations.isEmpty())
    }
  }

  @Test
  fun legacyFileUnderCountIsFlagged() {
    withScratchKotlinFile("LegacySample.kt", "val a = MaterialTheme.colorScheme.surface\n") {
        rootDir ->
      val result = ColorScanner.scan(rootDir, mapOf("LegacySample.kt" to 1))

      assertEquals(
          listOf("LegacySample.kt: expected 1 legacy literal(s), found 0 [none]"),
          result.violations,
      )
    }
  }

  @Test
  fun missingSourcesProduceSkipResult() {
    assertMissingDirectoryProducesSkip(ColorScanner::scan)
  }

  internal object ColorScanner {
    private val colorLiteralPattern =
        Regex(
            """(Color\.(?:White|Black|Gray|LightGray|DarkGray|Red|Green|Blue|Yellow|Cyan|Magenta)\b|Color\(0x[0-9A-Fa-f]+\))"""
        )

    /**
     * Existing literals that are reviewed and kept: each is behind an `isDark` branch, drawn over
     * the camera feed, white content on a brand-color button, or the Google brand mark. Lower a
     * count when you remove one; never raise it, use a theme-aware color from `Color.kt` instead.
     */
    val legacyFiles: Map<String, Int> =
        mapOf(
            "authentication/SignInScreen.kt" to 1,
            "collection/CollectionScreen.kt" to 4,
            "components/bottomdock/DockDestination.kt" to 2,
            "explore/ExploreScreen.kt" to 1,
            "friends/FriendsComponents.kt" to 2,
            "home/HomeScreen.kt" to 1,
            "home/OfflineNoticeCard.kt" to 2,
            "home/ScanPromptCard.kt" to 3,
            "scan/ScanReviewScreen.kt" to 3,
            "scan/ScanScreen.kt" to 7,
            "scan/components/FramingReticle.kt" to 1,
            "settings/SettingsScreen.kt" to 8,
        )

    fun scan(sourceDirectory: File): ScanResult = scan(sourceDirectory, legacyFiles)

    fun scan(sourceDirectory: File, legacyCounts: Map<String, Int>): ScanResult =
        scanKotlinSources(sourceDirectory) { relativePath, source ->
          evaluateFile(relativePath, source, legacyCounts)
        }

    private fun evaluateFile(
        relativePath: String,
        source: String,
        legacyCounts: Map<String, Int>,
    ): List<String> {
      if (relativePath.startsWith("theme/")) return emptyList()
      val matches = findLineMatches(stripComments(source), colorLiteralPattern)
      val expectedCount = legacyCounts[relativePath]
      if (expectedCount != null) {
        if (matches.size == expectedCount) return emptyList()
        val details =
            if (matches.isEmpty()) "none"
            else matches.joinToString(", ") { "$relativePath:${it.line}: ${it.literal}" }
        return listOf(
            "$relativePath: expected $expectedCount legacy literal(s), found ${matches.size} [$details]"
        )
      }
      return matches.map { "$relativePath:${it.line}: ${it.literal}" }
    }
  }
}
