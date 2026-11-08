package com.github.roamswent.roam

import com.github.roamswent.roam.SourceScanTestUtils.assertMissingDirectoryProducesSkip
import com.github.roamswent.roam.SourceScanTestUtils.assertProductionUiSourcesPass
import com.github.roamswent.roam.SourceScanTestUtils.scanKotlinSources
import com.github.roamswent.roam.SourceScanTestUtils.stripComments
import com.github.roamswent.roam.SourceScanTestUtils.withScratchKotlinFile
import java.io.File
import org.junit.Assert.assertEquals
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
    assertProductionUiSourcesPass("Hardcoded user-facing UI strings", UiStringScanner::scan)
  }

  @Test
  fun violationInScratchSourceIsDetected() {
    withScratchKotlinFile("Sample.kt", "Text(\"Oops\")\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Oops\""), result.violations)
    }
  }

  @Test
  fun proseTemplateIsDetectedWithItsSourceLine() {
    withScratchKotlinFile("Sample.kt", "Text(\"Remove \$interest\")\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Remove \$interest\""), result.violations)
    }
  }

  @Test
  fun oldConfidenceTemplateIsDetectedAfterExemptionRemoval() {
    withScratchKotlinFile(
        "MonumentDetailScreen.kt",
        "Text(\"\${(guide.confidence * 100).roundToInt()}% match\")\n",
    ) { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(
          listOf("MonumentDetailScreen.kt:1: \"\${(guide.confidence * 100).roundToInt()}% match\""),
          result.violations,
      )
    }
  }

  @Test
  fun pureIdentifierInterpolationIsNotFlagged() {
    withScratchKotlinFile("Sample.kt", "Text(\"\${C.Tag.dock_scan_label}\")\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertTrue(
          "Pure identifier interpolation must remain exempt from the prose heuristic",
          result.violations.isEmpty(),
      )
    }
  }

  @Test
  fun multilineTextLiteralIsDetected() {
    withScratchKotlinFile("Sample.kt", "Text(\n    \"Hello\"\n)\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Hello\""), result.violations)
    }
  }

  @Test
  fun assignedLiteralIsExplicitlyDocumentedAsOutOfScope() {
    withScratchKotlinFile("Sample.kt", "val label = \"Not scanned\"\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    }
  }

  @Test
  fun missingSourcesProduceSkipResult() {
    assertMissingDirectoryProducesSkip(UiStringScanner::scan)
  }

  @Test
  fun allowlistedGoogleLiteralIsPermittedInSettingsScreen() {
    withScratchKotlinFile("SettingsScreen.kt", "Text(\"G\")\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    }
  }

  @Test
  fun ifElseBranchesInContentDescriptionAreBothFlagged() {
    withScratchKotlinFile(
        "Sample.kt",
        "Icon(contentDescription = if (condition) \"First\" else \"Second\")\n",
    ) { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(
          listOf("Sample.kt:1: \"First\"", "Sample.kt:1: \"Second\""),
          result.violations,
      )
    }
  }

  @Test
  fun laterNamedTextArgumentIsFlagged() {
    withScratchKotlinFile(
        "Sample.kt",
        "Text(modifier = Modifier.padding(8.dp), text = \"Later text\")\n",
    ) { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Later text\""), result.violations)
    }
  }

  @Test
  fun toastMakeTextMessageLiteralIsFlagged() {
    withScratchKotlinFile(
        "Sample.kt",
        "Toast.makeText(context, \"Toast message\", Toast.LENGTH_SHORT).show()\n",
    ) { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Toast message\""), result.violations)
    }
  }

  @Test
  fun rawStringInTextIsFlagged() {
    withScratchKotlinFile("Sample.kt", "Text(\"\"\"Raw message\"\"\")\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertEquals(listOf("Sample.kt:1: \"Raw message\""), result.violations)
    }
  }

  @Test
  fun lineCommentContainingTextIsNotFlagged() {
    withScratchKotlinFile("Sample.kt", "// Text(\"example\")\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    }
  }

  @Test
  fun blockCommentContainingTextIsNotFlagged() {
    withScratchKotlinFile("Sample.kt", "/* Text(\"example\") */\n") { directory ->
      val result = UiStringScanner.scan(directory)

      assertTrue(result.violations.isEmpty())
    }
  }

  internal object UiStringScanner {
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
    private val interpolation = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")
    private val allowlist = mapOf("SettingsScreen.kt" to mapOf("G" to "Google brand mark"))

    fun scan(sourceDirectory: File): ScanResult =
        scanKotlinSources(sourceDirectory) { relativePath, source ->
          findLiterals(source).mapNotNull { literal ->
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

    private fun isAllowlisted(path: String, literal: String): Boolean =
        allowlist.any { (allowlistedPath, literals) ->
          (path == allowlistedPath || path.endsWith("/$allowlistedPath")) && literal in literals
        }

    private fun containsUserFacingProse(literal: String): Boolean =
        interpolation.replace(literal, "").any { it.isLetter() || it == ' ' }
  }
}
