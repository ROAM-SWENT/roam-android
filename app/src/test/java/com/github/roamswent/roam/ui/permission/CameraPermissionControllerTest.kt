package com.github.roamswent.roam.ui.permission

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CameraPermissionControllerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var activity: Activity
  private lateinit var controller: CameraPermissionController
  private var permissionResults = mutableListOf<Boolean>()

  @Before
  fun setUp() {
    composeTestRule.setContent {
      activity = LocalActivity.current!!
      controller = rememberCameraPermissionController { granted -> permissionResults += granted }
    }
  }

  @After
  fun tearDown() {
    if (::activity.isInitialized) activity.finish()
  }

  @Test
  fun freshStateHasNoCameraPermission() {
    assertFalse(controller.hasPermission())
    assertEquals(CameraPermissionState.NotDetermined, controller.state.value)
  }

  @Test
  fun grantedPermissionShortCircuitsRequestAndCallsBack() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

    controller.requestPermission()

    assertTrue(controller.hasPermission())
    assertEquals(CameraPermissionState.Granted, controller.state.value)
    assertEquals(listOf(true), permissionResults)
    assertNull(shadowOf(activity).lastRequestedPermission)
  }

  @Test
  fun grantResultUpdatesStateAndCallsBack() {
    dispatchPermissionResult(PackageManager.PERMISSION_GRANTED)

    assertEquals(CameraPermissionState.Granted, controller.state.value)
    assertEquals(listOf(true), permissionResults)
  }

  @Test
  fun denyResultUpdatesStateAndCallsBack() {
    dispatchPermissionResult(PackageManager.PERMISSION_DENIED)

    assertEquals(CameraPermissionState.Denied, controller.state.value)
    assertEquals(listOf(false), permissionResults)
  }

  @Test
  fun openAppSettingsStartsApplicationDetailsIntent() {
    controller.openAppSettings()

    val intent = shadowOf(activity).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals(activity.packageName, intent.data?.schemeSpecificPart)
  }

  private fun dispatchPermissionResult(result: Int) {
    controller.requestPermission()
    val permissionRequest = shadowOf(activity).lastRequestedPermission
    assertNotNull(permissionRequest)
    activity.onRequestPermissionsResult(
        permissionRequest!!.requestCode,
        arrayOf(Manifest.permission.CAMERA),
        intArrayOf(result),
    )
  }
}
