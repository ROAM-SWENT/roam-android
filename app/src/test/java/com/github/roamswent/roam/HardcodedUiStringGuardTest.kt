package com.github.roamswent.roam

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scans UI source files for hardcoded user-facing string literals.
 *
 * Scope: detects string literals directly passed to Text(...) or contentDescription arguments,
 * including both branches of if/else expressions, named text= arguments anywhere in Text(...)
 * calls, Toast.makeText(...) message arguments, and raw strings ("""...""").
 *
 * Out of scope: string literals assigned to variables or fields (e.g. ViewModel error strings) and
 * string concatenations.
 */
class HardcodedUiStringGuardTest {
  @Test
  fun productionUiSourcesContainNoUnallowlistedUserFacingLiterals() {
    val result = scanProductionUiSources()
    assertNull(result.skipReason, result.skipReason)
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
  fun proseTemplateIsDetectedWithItsSourceLine() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("Text(\"Remove \$interest\")\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Remove \$interest\""), result.violations)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun oldConfidenceTemplateIsDetectedAfterExemptionRemoval() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "MonumentDetailScreen.kt")
          .writeText("Text(\"\${(guide.confidence * 100).roundToInt()}% match\")\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(
          listOf("MonumentDetailScreen.kt:1: \"\${(guide.confidence * 100).roundToInt()}% match\""),
          result.violations,
      )
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun pureIdentifierInterpolationIsNotFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("Text(\"\${C.Tag.dock_scan_label}\")\n")

      val result = UiStringScanner.scan(directory)

      assertTrue(
          "Pure identifier interpolation must remain exempt from the prose heuristic",
          result.violations.isEmpty(),
      )
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun multilineTextLiteralIsDetected() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("Text(\n    \"Hello\"\n)\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Hello\""), result.violations)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun assignedLiteralIsExplicitlyDocumentedAsOutOfScope() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("val label = \"Hello\"\nText(label)\n")

      val result = UiStringScanner.scan(directory)

      assertTrue(
          "Documented limitation: assigned literals passed to Text are not data-flow analyzed",
          result.violations.isEmpty(),
      )
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

  @Test
  fun ifElseBranchesInContentDescriptionAreBothFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt")
          .writeText("Icon(contentDescription = if (condition) \"First\" else \"Second\")\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(
          listOf("Sample.kt:1: \"First\"", "Sample.kt:1: \"Second\""),
          result.violations,
      )
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun laterNamedTextArgumentIsFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt")
          .writeText("Text(modifier = Modifier.padding(8.dp), text = \"Later text\")\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Later text\""), result.violations)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun toastMakeTextMessageLiteralIsFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt")
          .writeText("Toast.makeText(context, \"Toast message\", Toast.LENGTH_SHORT).show()\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Toast message\""), result.violations)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun rawStringInTextIsFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("Text(\"\"\"Raw message\"\"\")\n")

      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Raw message\""), result.violations)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun lineCommentContainingTextIsNotFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("// Text(\"example\")\n")

      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun blockCommentContainingTextIsNotFlagged() {
    val directory = Files.createTempDirectory("ui-string-guard").toFile()
    try {
      File(directory, "Sample.kt").writeText("/* Text(\"example\") */\n")

      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    } finally {
      directory.deleteRecursively()
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
    private val argumentPrefix =
        """(?:(?:\bText\s*\(\s*(?:text\s*=\s*)?)|\btext\s*=\s*|\bcontentDescription\s*=\s*|\bToast\s*\.\s*makeText\s*\(\s*[^,]+,\s*)"""
    private val stringLiteral = """(?:"{3}([\s\S]*?)"{3}|"([^"]*)")"""
    private val directLiteral =
        Regex(
            argumentPrefix + stringLiteral,
            RegexOption.DOT_MATCHES_ALL,
        )
    private val ifElseLiteral =
        Regex(
            argumentPrefix +
                """if\s*\([^)]*\)\s*\{?\s*""" +
                stringLiteral +
                """\s*\}?\s*else\s*\{?\s*""" +
                stringLiteral +
                """\s*\}?""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val comment = Regex("""//.*|/\*[\s\S]*?\*/""")
    private val interpolation = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")
    private val allowlist = mapOf("SettingsScreen.kt" to mapOf("G" to "Google brand mark"))

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
                      !containsUserFacingProse(literal.value) ||
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

    private fun findLiterals(source: String): List<FoundLiteral> {
      val cleanSource = stripComments(source)
      val direct =
          directLiteral.findAll(cleanSource).map { match ->
            val value = match.groupValues[1].ifEmpty { match.groupValues[2] }
            FoundLiteral(
                value = value,
                line = cleanSource.take(match.range.first).count { it == '\n' } + 1,
            )
          }
      val ifElse =
          ifElseLiteral.findAll(cleanSource).flatMap { match ->
            val line = cleanSource.take(match.range.first).count { it == '\n' } + 1
            listOf(
                FoundLiteral(match.groupValues[1].ifEmpty { match.groupValues[2] }, line),
                FoundLiteral(match.groupValues[3].ifEmpty { match.groupValues[4] }, line),
            )
          }
      return (direct + ifElse).sortedBy(FoundLiteral::line).toList()
    }

    private fun stripComments(source: String): String =
        comment.replace(source) { match -> "\n".repeat(match.value.count { it == '\n' }) }

    private fun isAllowlisted(path: String, literal: String): Boolean =
        allowlist.any { (allowlistedPath, literals) ->
          (path == allowlistedPath || path.endsWith("/$allowlistedPath")) && literal in literals
        }

    private fun containsUserFacingProse(literal: String): Boolean =
        interpolation.replace(literal, "").any { it.isLetter() || it == ' ' }
  }
}
