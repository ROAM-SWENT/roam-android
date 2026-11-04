// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.scan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.camera.view.PreviewView
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.repository.CapturedImageManager
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.camera.CameraController
import com.github.roamswent.roam.ui.navigation.Routes
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

  @Test
  fun gallerySelectionNavigatesToReviewWithSelectedUri() {
    shadowOf(composeTestRule.activity).grantPermissions(android.Manifest.permission.CAMERA)
    val imageManager = mockk<CapturedImageManager>(relaxed = true)
    val registry = RecordingActivityResultRegistry()
    val registryOwner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry: ActivityResultRegistry = registry
        }
    val selectedUri = Uri.parse("content://gallery/images/travel%20photo.png")
    composeTestRule.activity.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        SampleAppTheme {
          val navController = rememberNavController()
          NavHost(navController = navController, startDestination = Routes.Scan) {
            composable(Routes.Scan) {
              ScanRoute(
                  navController = navController,
                  cameraControllerFactory = { _, _ -> FakeCameraController() },
                  capturedImageManagerFactory = { imageManager },
              )
            }
            composable("${Routes.ScanReview}?uri={uri}&gallery={gallery}") { entry ->
              GalleryReviewDestination(entry)
            }
          }
        }
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.scan_gallery_button).performClick()
    registry.deliverResult(Activity.RESULT_OK, Intent().setData(selectedUri))

    composeTestRule.onNodeWithText("true|${selectedUri}").assertIsDisplayed()
  }

  @androidx.compose.runtime.Composable
  private fun GalleryReviewDestination(entry: NavBackStackEntry) {
    Text(
        text = "${entry.arguments?.getString("gallery")}|${entry.arguments?.getString("uri")}",
        modifier = Modifier.testTag("gallery_review_destination"),
    )
  }

  private class RecordingActivityResultRegistry : ActivityResultRegistry() {
    private var requestCode: Int? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      this.requestCode = requestCode
    }

    fun deliverResult(resultCode: Int, intent: Intent?) {
      dispatchResult(checkNotNull(requestCode), resultCode, intent)
    }
  }

  private class FakeCameraController(private val events: MutableList<String> = mutableListOf()) :
      CameraController {
    var startCount = 0
    var stopCount = 0

    override val isRunning: Boolean = false

    override fun start(previewView: PreviewView) {
      startCount++
      events += "start"
    }

    override fun stop() {
      stopCount++
      events += "stop"
    }

    override fun captureTo(uri: Uri, onSaved: () -> Unit, onError: (Throwable) -> Unit) = Unit
  }
}
