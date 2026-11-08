/*
 * Co-authored-by: Google Antigravity <noreply@google.com>
 */
package com.github.roamswent.roam.ui.theme

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HardcodedFontSizeGuardTest {
  @Test
  fun productionUiSourcesContainNoUnallowlistedFontSizeLiterals() {
    val result = FontSizeScanner.scan(resolveUiSourceDirectory())
    result.skipReason?.let { assumeTrue(it, false) }
    assertTrue(
        "Hardcoded UI fontSize/lineHeight violations:\n${result.violations.joinToString("\n")}",
        result.violations.isEmpty(),
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
    val parent = Files.createTempDirectory("font-size-guard-missing").toFile()
    try {
      val result = FontSizeScanner.scan(parent.resolve("missing"))

      assertFalse(result.skipReason.isNullOrBlank())
      assertTrue(result.violations.isEmpty())
    } finally {
      parent.deleteRecursively()
    }
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
                  "initialFontSize: TextUnit = DockLabelFontSize",
                  "initialFontSize: TextUnit = 11.sp",
              )
      file.writeText(updated)
      val expectedLine =
          updated.lines().indexOfFirst { it.contains("initialFontSize: TextUnit = 11.sp") } + 1

      val result = FontSizeScanner.scan(rootDir)

      assertEquals(
          listOf(
              "components/bottomdock/AppBottomDock.kt:$expectedLine: " +
                  "initialFontSize: TextUnit = 11.sp"
          ),
          result.violations,
      )
    }
  }

  object FontSizeScanner {
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

  data class ScanResult(
      val violations: List<String>,
      val skipReason: String? = null,
  )

  private data class LineMatch(val literal: String, val line: Int)

  companion object {
    private fun resolveUiSourceDirectory(): File {
      val userDir = File(System.getProperty("user.dir") ?: ".")
      val candidates =
          listOf(
              userDir.resolve("app/src/main/java/com/github/roamswent/roam/ui"),
              userDir.resolve("src/main/java/com/github/roamswent/roam/ui"),
          )
      return candidates.firstOrNull(File::isDirectory) ?: candidates.first()
    }

    private fun scanKotlinSources(
        sourceDirectory: File,
        fileEvaluator: (relativePath: String, source: String) -> List<String>,
    ): ScanResult {
      if (!sourceDirectory.isDirectory) {
        return ScanResult(emptyList(), "UI source directory not found: $sourceDirectory")
      }
      val violations =
          sourceDirectory
              .walkTopDown()
              .filter { it.isFile && it.extension == "kt" }
              .sortedBy { it.relativeTo(sourceDirectory).invariantSeparatorsPath }
              .flatMap { file ->
                val relativePath = file.relativeTo(sourceDirectory).invariantSeparatorsPath
                fileEvaluator(relativePath, file.readText())
              }
              .toList()
      return ScanResult(violations)
    }

    private fun findLineMatches(source: String, pattern: Regex): List<LineMatch> =
        pattern
            .findAll(source)
            .map { match ->
              LineMatch(
                  literal = match.groupValues[1],
                  line = source.take(match.range.first).count { it == '\n' } + 1,
              )
            }
            .sortedBy(LineMatch::line)
            .toList()

    private fun withCopiedUiFile(relativePath: String, block: (File, File) -> Unit) {
      val productionDir = resolveUiSourceDirectory()
      assumeTrue("UI source directory not found: $productionDir", productionDir.isDirectory)
      val sourceFile = productionDir.resolve(relativePath)
      assumeTrue("UI source file not found: $sourceFile", sourceFile.isFile)
      val tempRoot = Files.createTempDirectory("font-size-guard").toFile()
      try {
        val targetFile = tempRoot.resolve(relativePath)
        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
        block(tempRoot, targetFile)
      } finally {
        tempRoot.deleteRecursively()
      }
    }
  }
}
