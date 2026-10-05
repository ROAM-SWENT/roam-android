package com.github.roamswent.roam.ui.permission

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CameraAvailabilityTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun injectedUnavailableFeatureIsRespected() {
    var cameraAvailable = true
    composeTestRule.setContent { cameraAvailable = rememberCameraAvailability { false } }

    assertFalse(cameraAvailable)
  }

  @Test
  fun injectedAvailableFeatureIsRespected() {
    var cameraAvailable = false
    composeTestRule.setContent { cameraAvailable = rememberCameraAvailability { true } }

    assertTrue(cameraAvailable)
  }

  @Test
  fun defaultAvailabilityQueriesPackageManager() {
    val application: Application = ApplicationProvider.getApplicationContext()
    shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
    var cameraAvailable = false
    composeTestRule.setContent { cameraAvailable = rememberCameraAvailability() }

    assertTrue(cameraAvailable)
  }
}
