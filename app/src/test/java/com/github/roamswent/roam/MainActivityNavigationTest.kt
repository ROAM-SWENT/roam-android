package com.github.roamswent.roam

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.resources.C
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun revokeCameraPermission() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application).denyPermissions(Manifest.permission.CAMERA)
  }

  @Test
  fun startsAtHome() {
    waitFor(C.Tag.home_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
  }

  @Test
  fun scanEntryNavigatesToScanDestination() {
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.CAMERA)
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    waitFor(C.Tag.scan_root_stub)
    composeTestRule.onNodeWithTag(C.Tag.scan_root_stub).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.home_screen_container).assertCountEquals(0)
  }

  @Test
  fun denyShowsPermissionDialogWithoutNavigating() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    dispatchCameraPermissionResult(PackageManager.PERMISSION_DENIED)

    waitFor(C.Tag.camera_permission_dialog)
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dialog).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.scan_root_stub).assertCountEquals(0)
  }

  @Test
  fun dismissingPermissionDialogLeavesHomeDisplayed() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    dispatchCameraPermissionResult(PackageManager.PERMISSION_DENIED)
    waitFor(C.Tag.camera_permission_dialog)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()

    composeTestRule.onAllNodesWithTag(C.Tag.camera_permission_dialog).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.scan_root_stub).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
  }

  private fun dispatchCameraPermissionResult(result: Int) {
    val permissionRequest = shadowOf(composeTestRule.activity).lastRequestedPermission
    checkNotNull(permissionRequest)
    composeTestRule.activity.onRequestPermissionsResult(
        permissionRequest.requestCode,
        arrayOf(Manifest.permission.CAMERA),
        intArrayOf(result),
    )
  }

  private fun waitFor(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
