package com.github.roamswent.roam

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

internal data class ScanResult(
    val violations: List<String>,
    val skipReason: String? = null,
)

internal data class LineMatch(
    val literal: String,
    val line: Int,
)

internal object SourceScanTestUtils {
  private val comment = Regex("""//.*|/\*[\s\S]*?\*/""")

  fun stripComments(source: String): String =
      comment.replace(source) { match -> "\n".repeat(match.value.count { it == '\n' }) }

  fun resolveUiSourceDirectory(): File {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val candidates =
        listOf(
            userDir.resolve("app/src/main/java/com/github/roamswent/roam/ui"),
            userDir.resolve("src/main/java/com/github/roamswent/roam/ui"),
        )
    return candidates.firstOrNull(File::isDirectory) ?: candidates.first()
  }

  fun assertProductionUiSourcesPass(
      failureHeading: String,
      scanner: (File) -> ScanResult,
  ) {
    val result = scanner(resolveUiSourceDirectory())
    assertNull(result.skipReason, result.skipReason)
    assertTrue(
        "$failureHeading:\n${result.violations.joinToString("\n")}",
        result.violations.isEmpty(),
    )
  }

  fun scanKotlinSources(
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

  fun findLineMatches(source: String, vararg patterns: Regex): List<LineMatch> =
      patterns
          .asSequence()
          .flatMap { it.findAll(source) }
          .map { match ->
            LineMatch(
                literal = match.groupValues[1],
                line = source.take(match.range.first).count { it == '\n' } + 1,
            )
          }
          .sortedBy(LineMatch::line)
          .toList()

  fun withScratchKotlinFile(
      fileName: String,
      content: String,
      block: (File) -> Unit,
  ) {
    val tempRoot = Files.createTempDirectory("source-scan-guard").toFile()
    try {
      val targetFile = tempRoot.resolve(fileName)
      targetFile.parentFile?.mkdirs()
      targetFile.writeText(content)
      block(tempRoot)
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  fun withCopiedUiFile(relativePath: String, block: (File, File) -> Unit) {
    val productionDir = resolveUiSourceDirectory()
    assumeTrue("UI source directory not found: $productionDir", productionDir.isDirectory)
    val sourceFile = productionDir.resolve(relativePath)
    assumeTrue("UI source file not found: $sourceFile", sourceFile.isFile)
    val tempRoot = Files.createTempDirectory("source-scan-guard").toFile()
    try {
      val targetFile = tempRoot.resolve(relativePath)
      targetFile.parentFile?.mkdirs()
      sourceFile.copyTo(targetFile, overwrite = true)
      block(tempRoot, targetFile)
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  fun assertMissingDirectoryProducesSkip(scanner: (File) -> ScanResult) {
    val parent = Files.createTempDirectory("source-scan-guard-missing").toFile()
    try {
      val result = scanner(parent.resolve("missing"))
      assertFalse(result.skipReason.isNullOrBlank())
      assertTrue(result.violations.isEmpty())
    } finally {
      parent.deleteRecursively()
    }
  }
}
