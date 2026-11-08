package com.github.roamswent.roam.ui.theme

import com.github.roamswent.roam.ScanResult
import com.github.roamswent.roam.SourceScanTestUtils.assertMissingDirectoryProducesSkip
import com.github.roamswent.roam.SourceScanTestUtils.assertProductionUiSourcesPass
import com.github.roamswent.roam.SourceScanTestUtils.findLineMatches
import com.github.roamswent.roam.SourceScanTestUtils.scanKotlinSources
import com.github.roamswent.roam.SourceScanTestUtils.withCopiedUiFile
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
    withCopiedUiFile("friends/FriendsScreen.kt") { rootDir, file ->
      val extraLine = file.readText().count { it == '\n' } + 1
      file.appendText("Text(text = \"Extra\", fontSize = 42.sp)\n")

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf(
              "friends/FriendsScreen.kt: expected 1 legacy literal(s), found 2 " +
                  "[friends/FriendsScreen.kt:36: fontSize = 28.sp, " +
                  "friends/FriendsScreen.kt:$extraLine: fontSize = 42.sp]"
          ),
          result.violations,
      )
    }
  }

  @Test
  fun legacyFileUnderCountIsFlagged() {
    withCopiedUiFile("friends/FriendsScreen.kt") { rootDir, file ->
      file.writeText(file.readText().replace("fontSize = 28.sp,", ""))

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf("friends/FriendsScreen.kt: expected 1 legacy literal(s), found 0 [none]"),
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

  internal object FontSizeScanner {
    private val spLiteralPattern =
        Regex(
            """\b((?:[A-Za-z]*[Ff]ontSize|lineHeight)\s*(?::\s*TextUnit\s*)?=\s*\d+(?:\.\d+)?\.sp)\b"""
        )

    val exemptedFiles: Set<String> = setOf("theme/Type.kt")

    val legacyFiles: Map<String, Int> =
        mapOf(
            "home/ScanPromptCard.kt" to 3,
            "home/HomeGreetingHeader.kt" to 2,
            "home/HomeScreen.kt" to 1,
            "home/OfflineNoticeCard.kt" to 1,
            "authentication/SignInScreen.kt" to 2,
            "monumentDetail/MonumentDetailScreen.kt" to 8,
            "loading/LoadingScreen.kt" to 3,
            "collection/CollectionScreen.kt" to 6,
            "friends/FriendsScreen.kt" to 1,
        )

    fun scan(sourceDirectory: File): ScanResult =
        scanKotlinSources(sourceDirectory) { relativePath, source ->
          evaluateFile(relativePath, source)
        }

    private fun evaluateFile(relativePath: String, source: String): List<String> {
      if (relativePath in exemptedFiles) return emptyList()
      val matches = findLineMatches(source, spLiteralPattern)
      val expectedCount = legacyFiles[relativePath]
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
