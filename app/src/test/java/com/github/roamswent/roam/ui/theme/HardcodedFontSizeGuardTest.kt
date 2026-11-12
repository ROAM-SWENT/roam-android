package com.github.roamswent.roam.ui.theme

import com.github.roamswent.roam.ScanResult
import com.github.roamswent.roam.SourceScanTestUtils.assertMissingDirectoryProducesSkip
import com.github.roamswent.roam.SourceScanTestUtils.assertProductionUiSourcesPass
import com.github.roamswent.roam.SourceScanTestUtils.findLineMatches
import com.github.roamswent.roam.SourceScanTestUtils.scanKotlinSources
import com.github.roamswent.roam.SourceScanTestUtils.withCopiedUiFile
import com.github.roamswent.roam.SourceScanTestUtils.withScratchKotlinFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcodedFontSizeGuardTest {
  @Test
  fun productionUiSourcesContainNoUnallowlistedFontSizeLiterals() {
    assertProductionUiSourcesPass(
        "Hardcoded UI fontSize/lineHeight violations",
        FontSizeScanner::scan,
    )
  }

  @Test
  fun scratchFontSizeInCopiedNonLegacyFileIsFlagged() {
    withCopiedUiFile("explore/ExploreScreen.kt") { rootDir, file ->
      val lineCount = file.readText().count { it == '\n' } + 1
      file.appendText("Text(text = \"Scratch\", fontSize = 42.sp)\n")

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf("explore/ExploreScreen.kt:$lineCount: fontSize = 42.sp"),
          result.violations,
      )
    }
  }

  @Test
  fun violationInsideTypeKtCopyIsIgnored() {
    withCopiedUiFile("theme/Type.kt") { rootDir, file ->
      file.appendText("val ScratchToken = TextStyle(fontSize = 99.sp, lineHeight = 110.sp)\n")

      val result = FontSizeScanner.scan(rootDir)

      assertTrue(result.violations.isEmpty())
    }
  }

  @Test
  fun legacyFileOverCountIsFlagged() {
    withScratchKotlinFile(
        "LegacySample.kt",
        "Text(text = \"ExtraOne\", fontSize = 28.sp)\nText(text = \"ExtraTwo\", fontSize = 42.sp)\n",
    ) { rootDir ->
      val result = FontSizeScanner.scan(rootDir, mapOf("LegacySample.kt" to 1))

      assertEquals(
          listOf(
              "LegacySample.kt: expected 1 legacy literal(s), found 2 " +
                  "[LegacySample.kt:1: fontSize = 28.sp, " +
                  "LegacySample.kt:2: fontSize = 42.sp]"
          ),
          result.violations,
      )
    }
  }

  @Test
  fun legacyFileUnderCountIsFlagged() {
    withScratchKotlinFile(
        "LegacySample.kt",
        "Text(text = \"No hardcoded size\")\n",
    ) { rootDir ->
      val result = FontSizeScanner.scan(rootDir, mapOf("LegacySample.kt" to 1))

      assertEquals(
          listOf("LegacySample.kt: expected 1 legacy literal(s), found 0 [none]"),
          result.violations,
      )
    }
  }

  @Test
  fun missingSourcesProduceSkipResult() {
    assertMissingDirectoryProducesSkip(FontSizeScanner::scan)
  }

  @Test
  fun scratchFontSizeInAppBottomDockCopyIsFlagged() {
    withCopiedUiFile("components/bottomdock/AppBottomDock.kt") { rootDir, file ->
      val extraLine = file.readText().count { it == '\n' } + 1
      file.appendText("Text(text = \"Dock\", fontSize = 11.sp)\n")

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf("components/bottomdock/AppBottomDock.kt:$extraLine: fontSize = 11.sp"),
          result.violations,
      )
    }
  }

  @Test
  fun reInlinedDefaultFontSizeInAppBottomDockCopyIsFlagged() {
    withCopiedUiFile("components/bottomdock/AppBottomDock.kt") { rootDir, file ->
      val updated =
          file
              .readText()
              .replace(
                  "minFontSize: TextUnit = DockLabelMinFontSize",
                  "minFontSize: TextUnit = 8.5.sp",
              )
      file.writeText(updated)
      val expectedLine =
          updated.lines().indexOfFirst { it.contains("minFontSize: TextUnit = 8.5.sp") } + 1

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf(
              "components/bottomdock/AppBottomDock.kt:$expectedLine: " +
                  "minFontSize: TextUnit = 8.5.sp"
          ),
          result.violations,
      )
    }
  }

  @Test
  fun reintroducedFontSizeInMigratedFileIsFlagged() {
    withCopiedUiFile("collection/CollectionScreen.kt") { rootDir, file ->
      val lineCount = file.readText().count { it == '\n' } + 1
      file.appendText("Text(text = \"Regression\", fontSize = 14.sp)\n")

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf("collection/CollectionScreen.kt:$lineCount: fontSize = 14.sp"),
          result.violations,
      )
    }
  }

  internal object FontSizeScanner {
    private val spLiteralPattern =
        Regex(
            """\b((?:[A-Za-z]*[Ff]ontSize|lineHeight)\s*(?::\s*TextUnit\s*)?=\s*\d+(?:\.\d+)?\.sp)\b"""
        )

    val exemptedFiles: Set<String> = setOf("theme/Type.kt")

    val legacyFiles: Map<String, Int> = emptyMap()

    fun scan(sourceDirectory: File): ScanResult = scan(sourceDirectory, legacyFiles)

    fun scan(
        sourceDirectory: File,
        legacyCounts: Map<String, Int>,
    ): ScanResult =
        scanKotlinSources(sourceDirectory) { relativePath, source ->
          evaluateFile(relativePath, source, legacyCounts)
        }

    fun evaluateFile(
        relativePath: String,
        source: String,
        legacyCounts: Map<String, Int> = legacyFiles,
    ): List<String> {
      if (relativePath in exemptedFiles) return emptyList()
      val matches = findLineMatches(source, spLiteralPattern)
      val expectedCount = legacyCounts[relativePath]
      if (expectedCount != null) {
        if (matches.size == expectedCount) return emptyList()
        val details =
            if (matches.isEmpty()) {
              "none"
            } else {
              matches.joinToString(", ") { "$relativePath:${it.line}: ${it.literal}" }
            }
        return listOf(
            "$relativePath: expected $expectedCount legacy literal(s), found ${matches.size} [$details]"
        )
      }
      return matches.map { "$relativePath:${it.line}: ${it.literal}" }
    }
  }
}
