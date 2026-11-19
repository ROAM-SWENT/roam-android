// Co-authored-by: Gemini 4 Hydrogen <noreply@google.com>

package com.github.roamswent.roam

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

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

  @Test
  fun manifest_mainActivityDeclaresLocaleAndLayoutDirectionConfigChanges_regressionPreventsActivityRecreationFlicker() {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val manifestFile =
        listOf(
                userDir.resolve("app/src/main/AndroidManifest.xml"),
                userDir.resolve("src/main/AndroidManifest.xml"),
            )
            .firstOrNull(File::isFile)
    checkNotNull(manifestFile) { "AndroidManifest.xml not found" }

    val document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile)
    val activities = document.getElementsByTagName("activity")
    val androidNamespace = "http://schemas.android.com/apk/res/android"
    var mainActivityConfigChanges: String? = null
    for (index in 0 until activities.length) {
      val element = activities.item(index) as Element
      if (element.getAttributeNS(androidNamespace, "name") == ".MainActivity") {
        mainActivityConfigChanges = element.getAttributeNS(androidNamespace, "configChanges")
      }
    }

    assertEquals("locale|layoutDirection", mainActivityConfigChanges)
  }
}
