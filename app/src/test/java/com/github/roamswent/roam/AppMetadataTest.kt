// Co-authored-by: Google Antigravity <noreply@google.com>
package com.github.roamswent.roam

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppMetadataTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun launcherActivity_declaresRoamLabel_regressionMisconfiguredLauncherBreaksAppIconOrName() {
    val packageManager = context.packageManager
    val launcherIntent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

    val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.queryIntentActivities(
              launcherIntent,
              PackageManager.ResolveInfoFlags.of(0),
          )
        } else {
          @Suppress("DEPRECATION") packageManager.queryIntentActivities(launcherIntent, 0)
        }

    assertEquals(1, resolveInfos.size)
    val activityInfo = resolveInfos[0].activityInfo
    assertEquals("com.github.roamswent.roam.MainActivity", activityInfo.name)
    assertEquals("Roam", activityInfo.loadLabel(packageManager).toString())
  }

  @Test
  @Config(qualifiers = "fr")
  fun launcherActivity_inFrench_resolvesRoamLabel_regressionFrenchLocaleBreaksLauncherName() {
    val packageManager = context.packageManager
    val launcherIntent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

    val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.queryIntentActivities(
              launcherIntent,
              PackageManager.ResolveInfoFlags.of(0),
          )
        } else {
          @Suppress("DEPRECATION") packageManager.queryIntentActivities(launcherIntent, 0)
        }

    assertEquals(1, resolveInfos.size)
    val activityInfo = resolveInfos[0].activityInfo
    assertEquals("Roam", activityInfo.loadLabel(packageManager).toString())
  }

  @Test
  fun applicationInfo_declaresLauncherAndRoundIcon_regressionMissingAppIconBreaksLauncherVisuals() {
    val packageManager = context.packageManager
    @Suppress("DEPRECATION") val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    val applicationInfo = packageInfo.applicationInfo
    checkNotNull(applicationInfo)

    assertEquals(R.mipmap.ic_launcher, applicationInfo.icon)
    assertEquals(R.mipmap.ic_launcher_round, applicationInfo.roundIcon)
  }

  private val android.content.pm.ApplicationInfo.roundIcon: Int
    get() {
      val field = runCatching {
        javaClass.getField("roundIconRes")
      }
          .recoverCatching { javaClass.getDeclaredField("roundIconRes") }
          .getOrNull()
      return field?.apply { isAccessible = true }?.getInt(this) ?: 0
    }

  @Test
  fun iconDrawables_inflateSuccessfully_regressionMissingOrBrokenDrawableAssets() {
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_background))
    assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
    assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
    assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))
  }

  @Test
  fun manifest_mainActivityDoesNotDeclareTitleActivityMain_regressionRevertingToTitleActivityMainBreaksLauncherLabel() {
    val userDir = File(System.getProperty("user.dir") ?: ".")
    val manifestFile =
        listOf(
                userDir.resolve("app/src/main/AndroidManifest.xml"),
                userDir.resolve("src/main/AndroidManifest.xml"),
            )
            .firstOrNull(File::isFile)
    checkNotNull(manifestFile) { "AndroidManifest.xml not found" }

    val content = manifestFile.readText()
    val activityRegex = Regex("""<activity\b([^>]+)>""")
    val mainActivityTag =
        activityRegex
            .findAll(content)
            .map { it.value }
            .firstOrNull {
              it.contains("android:name=\".MainActivity\"") ||
                  it.contains("android:name=\"com.github.roamswent.roam.MainActivity\"")
            }
    checkNotNull(mainActivityTag) { "MainActivity declaration not found in AndroidManifest.xml" }

    assertFalse(
        "MainActivity must not declare android:label=\"@string/title_activity_main\"",
        mainActivityTag.contains("android:label=\"@string/title_activity_main\""),
    )
  }
}
