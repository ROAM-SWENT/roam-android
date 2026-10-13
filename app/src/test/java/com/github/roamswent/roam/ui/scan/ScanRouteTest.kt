package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.camera.CameraController
import com.github.roamswent.roam.ui.loading.LoadingRoute
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
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
  fun capturedPhotoNavigatesToLoadingScreen() {
    shadowOf(composeTestRule.activity).grantPermissions(android.Manifest.permission.CAMERA)
    val uri = Uri.parse("content://example/capture.jpg")
    val imageManager = mockk<CapturedImageManager>()
    coEvery { imageManager.newCaptureUri() } returns uri
    val imagePayload = ImagePayload(byteArrayOf(1), "image/jpeg")
    val repository = mockk<MonumentGuideRepository>()
    coEvery { repository.guideFor(any()) } coAnswers { awaitCancellation() }
    var cameraController: FakeCameraController? = null

    composeTestRule.activity.setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.Home) {
          composable(Routes.Home) {
            Box(Modifier.fillMaxSize().semantics { testTag = C.Tag.home_screen_container }) {
              Button(
                  onClick = { navController.navigate(Routes.Scan) },
                  modifier = Modifier.semantics { testTag = C.Tag.home_scan_button },
              ) {
                Text("Scan")
              }
            }
          }
          composable(Routes.Scan) {
            ScanRoute(
                navController = navController,
                cameraControllerFactory = { _, _ ->
                  FakeCameraController().also { cameraController = it }
                },
                capturedImageManagerFactory = { imageManager },
            )
          }
          composable("${Routes.Loading}?uri={uri}") { backStackEntry ->
            val imageUri = Uri.parse(requireNotNull(backStackEntry.arguments?.getString("uri")))
            LoadingRoute(
                imageUri = imageUri,
                onCancel = { navController.popBackStack(Routes.Home, inclusive = false) },
                onDiscoverySuccess = { _, _ -> },
                onDiscoveryError = {},
                repository = repository,
                readImage = { imagePayload },
            )
          }
        }
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.home_scan_button).performClick()
    composeTestRule.waitUntil { cameraController != null }
    composeTestRule.onNodeWithTag(C.Tag.scan_shutter_button).performClick()
    composeTestRule.waitUntil { cameraController?.onCaptureSaved != null }
    requireNotNull(cameraController).onCaptureSaved?.invoke()

    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodesWithTag(C.Tag.loading_screen_title)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_image).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.home_screen_container).assertIsDisplayed()
  }

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

  @Test
  fun resumeWithoutPermissionRevokesCameraAndNeverStartsIt() {
    val events = mutableListOf<String>()
    val controllers = mutableListOf<FakeCameraController>()
    val imageManager = mockk<CapturedImageManager>(relaxed = true)
    composeTestRule.activity.setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "scan") {
          composable("scan") {
            ScanRoute(
                navController = navController,
                cameraControllerFactory = { _, _ ->
                  FakeCameraController(events).also { controllers += it }
                },
                capturedImageManagerFactory = { imageManager },
            )
          }
        }
      }
    }

    composeTestRule.waitUntil { controllers.size == 1 }
    assertEquals(0, controllers.single().startCount)

    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeTestRule.onNodeWithTag(C.Tag.scan_camera_revoked_container).assertIsDisplayed()
    assertEquals(listOf("stop"), events)
  }

  private class FakeCameraController(private val events: MutableList<String> = mutableListOf()) :
      CameraController {
    var startCount = 0
    var stopCount = 0
    var onCaptureSaved: (() -> Unit)? = null

    override val isRunning: Boolean = false

    override fun start(previewView: PreviewView) {
      startCount++
      events += "start"
    }

    override fun stop() {
      stopCount++
      events += "stop"
    }

    override fun captureTo(uri: Uri, onSaved: () -> Unit, onError: (Throwable) -> Unit) {
      onCaptureSaved = onSaved
    }
  }
}
