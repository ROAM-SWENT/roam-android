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
 * Fails the build on any raw color (`Color.White`, `Color(0xFF...)`, ...) in production UI code
 * outside `ui/theme/`: a raw color does not follow dark mode. Every color lives in `Color.kt`.
 */
class HardcodedColorGuardTest {
  @Test
  fun productionUiSourcesContainNoColorLiterals() {
    assertProductionUiSourcesPass("Hardcoded UI color violations", ColorScanner::scan)
  }

  @Test
  fun rawWhiteInCopiedExploreScreenIsFlagged() {
    withCopiedUiFile("explore/ExploreScreen.kt") { rootDir, file ->
      val lineCount = file.readText().count { it == '\n' } + 1
      file.appendText("val scratch = Color.White\n")

      val result = ColorScanner.scan(rootDir)

      assertEquals(listOf("explore/ExploreScreen.kt:$lineCount: Color.White"), result.violations)
    }
  }

  @Test
  fun hexColorInNewFileIsFlagged() {
    withScratchKotlinFile("Sample.kt", "val a = 1\nval b = Color(0xFF1B433E)\n") { rootDir ->
      val result = ColorScanner.scan(rootDir)

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
      val result = ColorScanner.scan(rootDir)

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
  fun missingSourcesProduceSkipResult() {
    assertMissingDirectoryProducesSkip(ColorScanner::scan)
  }

  internal object ColorScanner {
    private val colorLiteralPattern =
        Regex(
            """(Color\.(?:White|Black|Gray|LightGray|DarkGray|Red|Green|Blue|Yellow|Cyan|Magenta)\b|Color\(0x[0-9A-Fa-f]+\))"""
        )

    fun scan(sourceDirectory: File): ScanResult =
        scanKotlinSources(sourceDirectory) { relativePath, source ->
          if (relativePath.startsWith("theme/")) {
            emptyList()
          } else {
            findLineMatches(stripComments(source), colorLiteralPattern).map {
              "$relativePath:${it.line}: ${it.literal}"
            }
          }
        }
  }
}
