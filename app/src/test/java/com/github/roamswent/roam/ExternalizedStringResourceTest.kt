package com.github.roamswent.roam

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalizedStringResourceTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun resourcesExistWithExpectedEnglishValues() {
    resources().forEach { (resourceName, expectedValue) ->
      val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)

      assertNotEquals("Missing string resource $resourceName", 0, resourceId)
      assertEquals(
          "Unexpected value for $resourceName",
          expectedValue,
          context.getString(resourceId),
      )
    }
  }

  companion object {
    fun resources(): List<Pair<String, String>> =
        listOf(
            "camera_permission_title" to "Camera permission required",
            "camera_permission_message" to "Allow camera access in Settings to scan monuments.",
            "camera_permission_open_settings" to "Open Settings",
            "camera_permission_dismiss" to "Dismiss",
            "scan_review_validate" to "Validate",
            "scan_review_personalize_title" to "Personalize your guide",
            "scan_review_personalize_subtitle" to "Add interests to personalize your AI guide.",
            "scan_review_skip" to "Skip",
            "scan_review_save" to "Save",
            "scan_review_delete_failed_title" to "Delete failed",
            "scan_review_delete_failed_message" to
                "The photo could not be deleted and is still on the device.",
            "scan_review_retry" to "Retry",
            "scan_review_stay" to "Stay",
            "scan_review_send_failed_title" to "Couldn't identify this photo",
            "scan_review_dismiss" to "Dismiss",
            "scan_review_retake_title" to "Retake photo?",
            "scan_review_retake_message" to
                "The image will be permanently deleted from the device.",
            "scan_review_agree" to "Agree",
            "scan_review_cancel" to "Cancel",
            "scan_review_sending_title" to "Image being treated...",
            "scan_review_sending_message" to "Please wait.",
            "scan_close_description" to "Close",
            "scan_gallery_description" to "Choose photo from gallery",
            "scan_camera_access_disabled" to "Camera access was disabled.",
            "scan_choose_photo_from_gallery" to "Choose photo from gallery",
            "scan_go_back" to "Go back",
            "settings_add_interest" to "Add an interest",
            "settings_all_interests_selected" to "All available interests are already selected.",
            "settings_cancel" to "Cancel",
            "settings_sign_out" to "Sign Out",
            "settings_permission_granted" to "Granted",
            "settings_permission_denied" to "Denied",
            "settings_remove_interest" to "Remove %1\$s",
            "loading_error_description" to "Error",
            "monument_detail_picture_description" to "Monument picture",
            "scan_review_photo_title" to "Review your photo",
            "scan_review_choose_another_photo" to "Choose another photo",
            "scan_review_retake" to "Retake",
            "scan_frame_hint" to "Frame the monument",
            "settings_logout_successful" to "Logout successful",
            "settings_profile_role" to "Traveler",
            "settings_theme_auto" to "Auto",
            "settings_theme_light" to "Light",
            "settings_theme_dark" to "Dark",
            "settings_camera_permission" to "Camera permission",
            "settings_location_permission" to "Location permission",
            "settings_manage_permissions" to "Manage Permissions",
            "settings_interests_title" to "My Interests (AI Guide)",
            "settings_interests_subtitle" to "Used by AI to personalize your tourist guides.",
            "settings_interests_empty" to "No interests selected yet.",
            "settings_theme_section_header" to "Theme",
            "settings_permissions_section_header" to "App Permissions",
            "monument_detail_match_percent" to "%1\$d%% match",
            "monument_detail_fact_bullet" to "• %1\$s",
        )
  }
}

@RunWith(RobolectricTestRunner::class)
class ExternalizedStringFormattingTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun matchPercentHasExactXmlEscapingAndFormatsToSinglePercent() {
    val resourceId =
        context.resources.getIdentifier(
            "monument_detail_match_percent",
            "string",
            context.packageName,
        )

    assertEquals(
        "%1\$d%% match",
        xmlStringValue("monument_detail_match_percent"),
    )
    assertEquals("87% match", context.getString(resourceId, 87))
  }

  @Test
  fun factBulletFormatsWithBulletAndFact() {
    val resourceId =
        context.resources.getIdentifier(
            "monument_detail_fact_bullet",
            "string",
            context.packageName,
        )

    assertEquals("• Built in 1889", context.getString(resourceId, "Built in 1889"))
  }

  @Test
  fun removeInterestSubstitutesInterestWithoutBackticks() {
    val resourceId =
        context.resources.getIdentifier("settings_remove_interest", "string", context.packageName)

    assertEquals("Remove Architecture", context.getString(resourceId, "Architecture"))
    assertEquals(
        -1,
        context.getString(resourceId, "Architecture").indexOf('`'),
    )
  }

  private fun xmlStringValue(name: String): String {
    val sourceRoot = File(System.getProperty("user.dir") ?: ".")
    val stringsFile =
        listOf(
                sourceRoot.resolve("app/src/main/res/values/strings.xml"),
                sourceRoot.resolve("src/main/res/values/strings.xml"),
            )
            .firstOrNull(File::isFile)
    checkNotNull(stringsFile) { "Production strings.xml not found" }
    val line = stringsFile.readLines().first { it.contains("<string name=\"$name\">") }
    return line.substringAfter("\">").substringBefore("</string>")
  }
}
