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
    val result = scanProductionUiSources()
    result.skipReason?.let { assumeTrue(it, false) }
    assertTrue(
        "Hardcoded UI fontSize/lineHeight literals:\n${result.violations.joinToString("\n")}",
        result.violations.isEmpty(),
    )
  }

  @Test
  fun scratchFontSizeInCopiedScannedDirectoryIsFlagged() {
    val productionDir = resolveProductionUiDirectory()
    assumeTrue("UI source directory not found: $productionDir", productionDir.isDirectory)
    val copyDir = Files.createTempDirectory("font-size-guard-copy").toFile()
    try {
      productionDir.copyRecursively(copyDir, overwrite = true)
      File(copyDir, "ScratchScreen.kt").writeText("Text(text = \"Hi\", fontSize = 42.sp)\n")

      val result = FontSizeScanner.scan(copyDir)

      assertEquals(listOf("ScratchScreen.kt:1: fontSize = 42.sp"), result.violations)
    } finally {
      copyDir.deleteRecursively()
    }
  }

  @Test
  fun allowlistedEntryIsNotFlagged() {
    val directory = Files.createTempDirectory("font-size-guard-allowlist").toFile()
    try {
      val typeFile = File(directory, "theme/Type.kt")
      typeFile.parentFile?.mkdirs()
      typeFile.writeText(
          buildString {
            repeat(14) { append('\n') }
            append("fontSize = 40.sp,\n")
            append("lineHeight = 52.sp,\n")
          }
      )

      val legacyFile = File(directory, "friends/FriendsScreen.kt")
      legacyFile.parentFile?.mkdirs()
      legacyFile.writeText(
          buildString {
            repeat(35) { append('\n') }
            append("fontSize = 28.sp,\n")
          }
      )

      val result = FontSizeScanner.scan(directory)

      assertTrue(
          "Allowlisted entries must not be flagged, but found: ${result.violations}",
          result.violations.isEmpty(),
      )
    } finally {
      directory.deleteRecursively()
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
  fun deletingOneLegacyEntryFlagsThatFileAndLine() {
    val productionDir = resolveProductionUiDirectory()
    assumeTrue("UI source directory not found: $productionDir", productionDir.isDirectory)
    val removedEntry =
        FontSizeScanner.defaultAllowlist.first {
          it.file == "friends/FriendsScreen.kt" && it.line == 36
        }
    val reducedAllowlist = FontSizeScanner.defaultAllowlist.filterNot { it == removedEntry }

    val result = FontSizeScanner.scan(productionDir, allowlist = reducedAllowlist)

    assertEquals(
        listOf("friends/FriendsScreen.kt:36: fontSize = 28.sp"),
        result.violations,
    )
  }

  private fun resolveProductionUiDirectory(): File {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val candidates =
        listOf(
            userDir.resolve("app/src/main/java/com/github/roamswent/roam/ui"),
            userDir.resolve("src/main/java/com/github/roamswent/roam/ui"),
        )
    return candidates.firstOrNull(File::isDirectory) ?: candidates.first()
  }

  private fun scanProductionUiSources(): ScanResult =
      FontSizeScanner.scan(resolveProductionUiDirectory())

  enum class AllowlistCategory {
    SCALE_DEFINITION,
    LEGACY,
  }

  data class AllowlistEntry(
      val file: String,
      val line: Int,
      val literal: String,
      val category: AllowlistCategory,
      val reason: String,
  )

  data class ScanResult(
      val violations: List<String>,
      val skipReason: String? = null,
  )

  object FontSizeScanner {
    private val spLiteralPattern =
        Regex(
            """\b((?:[A-Za-z_]*[Ff]ontSize|lineHeight)\s*(?::\s*TextUnit\s*)?=\s*\d+(?:\.\d+)?\.sp)\b"""
        )

    val defaultAllowlist: List<AllowlistEntry> =
        listOf(
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 15,
                literal = "fontSize = 40.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 16,
                literal = "lineHeight = 52.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 23,
                literal = "fontSize = 28.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 24,
                literal = "lineHeight = 36.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 31,
                literal = "fontSize = 26.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 32,
                literal = "lineHeight = 34.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 39,
                literal = "fontSize = 22.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 40,
                literal = "lineHeight = 28.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 47,
                literal = "fontSize = 20.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 48,
                literal = "lineHeight = 26.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 55,
                literal = "fontSize = 18.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 56,
                literal = "lineHeight = 24.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 63,
                literal = "fontSize = 16.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 64,
                literal = "lineHeight = 24.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 71,
                literal = "fontSize = 15.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 72,
                literal = "lineHeight = 20.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 79,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 80,
                literal = "lineHeight = 20.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 87,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 88,
                literal = "lineHeight = 20.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 95,
                literal = "fontSize = 13.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 96,
                literal = "lineHeight = 18.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 103,
                literal = "fontSize = 12.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "theme/Type.kt",
                line = 104,
                literal = "lineHeight = 16.sp",
                category = AllowlistCategory.SCALE_DEFINITION,
                reason = "Type.kt in full — the scale definition itself",
            ),
            AllowlistEntry(
                file = "home/ScanPromptCard.kt",
                line = 56,
                literal = "fontSize = 18.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "home/ScanPromptCard.kt",
                line = 64,
                literal = "fontSize = 13.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "home/ScanPromptCard.kt",
                line = 65,
                literal = "lineHeight = 18.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "home/HomeGreetingHeader.kt",
                line = 42,
                literal = "fontSize = 15.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "home/HomeGreetingHeader.kt",
                line = 50,
                literal = "fontSize = 26.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "home/HomeScreen.kt",
                line = 120,
                literal = "fontSize = 18.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "home/OfflineNoticeCard.kt",
                line = 62,
                literal = "fontSize = 15.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "authentication/SignInScreen.kt",
                line = 145,
                literal = "fontSize = 40.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "authentication/SignInScreen.kt",
                line = 181,
                literal = "fontSize = 16.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 115,
                literal = "fontSize = 22.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 122,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 131,
                literal = "fontSize = 16.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 132,
                literal = "lineHeight = 24.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 142,
                literal = "fontSize = 16.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 143,
                literal = "lineHeight = 24.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 153,
                literal = "fontSize = 16.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "monumentDetail/MonumentDetailScreen.kt",
                line = 154,
                literal = "lineHeight = 24.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "loading/LoadingScreen.kt",
                line = 114,
                literal = "fontSize = 20.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "loading/LoadingScreen.kt",
                line = 140,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "loading/LoadingScreen.kt",
                line = 155,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "collection/CollectionScreen.kt",
                line = 86,
                literal = "fontSize = 28.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "collection/CollectionScreen.kt",
                line = 138,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "collection/CollectionScreen.kt",
                line = 149,
                literal = "fontSize = 12.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "collection/CollectionScreen.kt",
                line = 274,
                literal = "fontSize = 18.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "collection/CollectionScreen.kt",
                line = 282,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "collection/CollectionScreen.kt",
                line = 295,
                literal = "fontSize = 14.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "components/bottomdock/AppBottomDock.kt",
                line = 262,
                literal = "initialFontSize: TextUnit = 11.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "components/bottomdock/AppBottomDock.kt",
                line = 263,
                literal = "minFontSize: TextUnit = 8.5.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
            AllowlistEntry(
                file = "friends/FriendsScreen.kt",
                line = 36,
                literal = "fontSize = 28.sp",
                category = AllowlistCategory.LEGACY,
                reason = "legacy, batch PR pending",
            ),
        )

    fun scan(
        sourceDirectory: File,
        allowlist: List<AllowlistEntry> = defaultAllowlist,
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
                findLiterals(file.readText()).mapNotNull { found ->
                  if (isAllowlisted(relativePath, found, allowlist)) {
                    null
                  } else {
                    "$relativePath:${found.line}: ${found.literal}"
                  }
                }
              }
              .toList()
      return ScanResult(violations)
    }

    private data class FoundLiteral(val literal: String, val line: Int)

    private fun findLiterals(source: String): List<FoundLiteral> =
        spLiteralPattern
            .findAll(source)
            .map { match ->
              FoundLiteral(
                  literal = match.groupValues[1],
                  line = source.take(match.range.first).count { it == '\n' } + 1,
              )
            }
            .sortedBy(FoundLiteral::line)
            .toList()

    private fun isAllowlisted(
        path: String,
        found: FoundLiteral,
        allowlist: List<AllowlistEntry>,
    ): Boolean = allowlist.any { entry ->
      val sameFile = path == entry.file || path.endsWith("/${entry.file}")
      when (entry.category) {
        AllowlistCategory.SCALE_DEFINITION -> sameFile
        AllowlistCategory.LEGACY ->
            sameFile && entry.line == found.line && entry.literal == found.literal
      }
    }
  }
}
