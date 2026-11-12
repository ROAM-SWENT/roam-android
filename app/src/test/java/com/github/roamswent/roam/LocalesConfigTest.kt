package com.github.roamswent.roam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalesConfigTest {

  @Test
  fun localesConfig_declaresExactlyEnAndFr_regressionMissingLocaleBreaksSystemPerAppLanguageList() {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val configFile =
        listOf(
                userDir.resolve("app/src/main/res/xml/locales_config.xml"),
                userDir.resolve("src/main/res/xml/locales_config.xml"),
            )
            .firstOrNull(File::isFile)
    checkNotNull(configFile) { "locales_config.xml not found" }

    val localeRegex = Regex("""<locale\s+android:name="([^"]+)"""")
    val locales =
        configFile.readLines().mapNotNull { line -> localeRegex.find(line)?.groupValues?.get(1) }

    assertEquals(listOf("en", "fr"), locales)
  }

  @Test
  fun manifest_declaresAppLocalesMetadataHolderServiceWithAutoStoreLocales() {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val manifestFile =
        listOf(
                userDir.resolve("app/src/main/AndroidManifest.xml"),
                userDir.resolve("src/main/AndroidManifest.xml"),
            )
            .firstOrNull(File::isFile)
    checkNotNull(manifestFile) { "AndroidManifest.xml not found" }

    val content = manifestFile.readText()
    org.junit.Assert.assertTrue(
        content.contains("androidx.appcompat.app.AppLocalesMetadataHolderService")
    )
    org.junit.Assert.assertTrue(content.contains("android:name=\"autoStoreLocales\""))
    org.junit.Assert.assertTrue(content.contains("android:value=\"true\""))
  }
}
