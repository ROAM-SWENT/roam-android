package com.github.roamswent.roam

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HardcodedUiStringGuardTest {
  @Test
  fun productionUiSourcesContainNoUnallowlistedUserFacingLiterals() {
    val result = scanProductionUiSources()
    result.skipReason?.let { assumeTrue(it, false) }
    assertTrue(
        "Hardcoded user-facing UI strings:\n${result.violations.joinToString("\n")}",
        result.violations.isEmpty(),
    )
  }

  @Test
  fun violationInScratchSourceIsDetected() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("Text(\"Oops\")\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Oops\""), result.violations)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun allowlistedBrandLiteralIsNotFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      val file = File(directory, "SettingsScreen.kt")
      file.writeText("Text(\"G\")\n")

      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun missingSourcesProduceSkipResult() {
    val parent = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      val result = UiStringScanner.scan(parent.resolve("missing"))

      assertFalse(result.skipReason.isNullOrBlank())
    } finally {
      parent.deleteRecursively()
    }
  }

  private fun scanProductionUiSources(): ScanResult {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val candidates =
        listOf(
            userDir.resolve("app/src/main/java/com/github/roamswent/roam/ui"),
            userDir.resolve("src/main/java/com/github/roamswent/roam/ui"),
        )
    return UiStringScanner.scan(candidates.firstOrNull(File::isDirectory) ?: candidates.first())
  }

  data class ScanResult(
      val violations: List<String>,
      val skipReason: String? = null,
  )

  object UiStringScanner {
    private val textLiteral =
        Regex(
            """\bText\s*\(\s*(?:text\s*=\s*)?"([^"]*)"""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val contentDescriptionLiteral =
        Regex("""\bcontentDescription\s*=\s*"([^"]*)""", RegexOption.DOT_MATCHES_ALL)
    private val allowlist = mapOf("SettingsScreen.kt" to setOf("G" to "Google brand mark"))

    fun scan(sourceDirectory: File): ScanResult {
      if (!sourceDirectory.isDirectory) {
        return ScanResult(emptyList(), "UI source directory not found: $sourceDirectory")
      }
      val violations =
          sourceDirectory
              .walkTopDown()
              .filter { it.isFile && it.extension == "kt" }
              .flatMap { file ->
                val relativePath = file.relativeTo(sourceDirectory).path
                findLiterals(file.readText()).mapNotNull { literal ->
                  if (
                      literal.value.isEmpty() ||
                          literal.value.contains('$') ||
                          isAllowlisted(relativePath, literal.value)
                  ) {
                    null
                  } else {
                    "$relativePath:${literal.line}: \"${literal.value}\""
                  }
                }
              }
              .toList()
      return ScanResult(violations)
    }

    private data class FoundLiteral(val value: String, val line: Int)

    private fun findLiterals(source: String): List<FoundLiteral> =
        (textLiteral.findAll(source) + contentDescriptionLiteral.findAll(source))
            .map { match ->
              FoundLiteral(
                  value = match.groupValues[1],
                  line = source.take(match.range.first).count { it == '\n' } + 1,
              )
            }
            .sortedBy(FoundLiteral::line)
            .toList()

    private fun isAllowlisted(path: String, literal: String): Boolean =
        allowlist.entries.any { (allowlistedPath, literals) ->
          path == allowlistedPath || path.endsWith("/$allowlistedPath")
        } &&
            allowlist.entries
                .first { (allowlistedPath, _) ->
                  path == allowlistedPath || path.endsWith("/$allowlistedPath")
                }
                .value
                .any { it.first == literal }
  }
}
