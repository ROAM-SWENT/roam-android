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
import org.junit.Assert.assertNotSame
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
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun scanEntryNavigatesToScanDestination() {
    shadowOf(composeTestRule.activity).grantPermissions(Manifest.permission.CAMERA)
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_root_stub)
    waitForGone(C.Tag.home_screen_container)
  }

  @Test
  fun denyShowsPermissionDialogWithoutNavigating() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    dispatchCameraPermissionResult()

    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_root_stub)
    composeTestRule.onNodeWithTag(C.Tag.camera_permission_settings_button).assertIsDisplayed()
  }

  @Test
  fun dismissingPermissionDialogLeavesHomeDisplayed() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    dispatchCameraPermissionResult()
    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()

    waitForGone(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_root_stub)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
  }

  @Test
  fun denialAfterDismissShowsDialogAgain() {
    waitFor(C.Tag.home_scan_button)
    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    val firstPermissionRequest = dispatchCameraPermissionResult()
    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_root_stub)

    composeTestRule.onNodeWithTag(C.Tag.camera_permission_dismiss_button).performClick()
    waitForGone(C.Tag.camera_permission_dialog)
    composeTestRule.onAllNodesWithTag(C.Tag.camera_permission_dialog).assertCountEquals(0)
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForGone(C.Tag.scan_root_stub)

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    val secondPermissionRequest = permissionRequest()
    assertNotSame(firstPermissionRequest, secondPermissionRequest)
    dispatchCameraPermissionResult(secondPermissionRequest)

    waitAndAssertDisplayed(C.Tag.camera_permission_dialog)
    waitForGone(C.Tag.scan_root_stub)
  }

  private fun permissionRequest(): org.robolectric.shadows.ShadowActivity.PermissionsRequest {
    return checkNotNull(shadowOf(composeTestRule.activity).lastRequestedPermission)
  }

  private fun dispatchCameraPermissionResult() = dispatchCameraPermissionResult(permissionRequest())

  private fun dispatchCameraPermissionResult(
      permissionRequest: org.robolectric.shadows.ShadowActivity.PermissionsRequest
  ): org.robolectric.shadows.ShadowActivity.PermissionsRequest {
    composeTestRule.activity.onRequestPermissionsResult(
        permissionRequest.requestCode,
        arrayOf(Manifest.permission.CAMERA),
        intArrayOf(PackageManager.PERMISSION_DENIED),
    )
    return permissionRequest
  }

  private fun waitAndAssertDisplayed(tag: String) {
    waitFor(tag)
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  private fun waitFor(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForGone(tag: String) {
    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
  }
}
