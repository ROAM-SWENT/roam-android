package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.ui.camera.CameraController
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ScanRouteTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun cameraRestartsWhenScanRouteReenters() {
    shadowOf(composeTestRule.activity).grantPermissions(android.Manifest.permission.CAMERA)
    val controllers = mutableListOf<FakeCameraController>()
    val imageManager = mockk<CapturedImageManager>(relaxed = true)
    composeTestRule.activity.setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        var hasReentered by remember { mutableStateOf(false) }
        NavHost(navController = navController, startDestination = "scan") {
          composable("scan") {
            LaunchedEffect(Unit) {
              if (!hasReentered) {
                hasReentered = true
                navController.navigate("dummy")
              }
            }
            ScanRoute(
                navController = navController,
                cameraControllerFactory = { _, _ ->
                  FakeCameraController().also { controllers += it }
                },
                capturedImageManagerFactory = { imageManager },
            )
          }
          composable("dummy") { LaunchedEffect(Unit) { navController.popBackStack() } }
        }
      }
    }

    composeTestRule.waitUntil { controllers.size == 2 }

    assertEquals(2, controllers.sumOf { it.startCount })
    assertTrue(controllers.first().stopCount >= 1)
  }

  private class FakeCameraController : CameraController {
    var startCount = 0
    var stopCount = 0

    override val isRunning: Boolean = false

    override fun start(previewView: PreviewView) {
      startCount++
    }

    override fun stop() {
      stopCount++
    }

    override fun captureTo(uri: Uri, onSaved: () -> Unit, onError: (Throwable) -> Unit) = Unit
  }
}
