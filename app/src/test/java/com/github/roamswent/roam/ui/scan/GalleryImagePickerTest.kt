// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.scan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryImagePickerTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val registry = RecordingActivityResultRegistry()
  private val registryOwner =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
      }

  @Test
  fun launchesOfficialPickerWithImagesOnlyWhenInvoked() {
    setContent(onImagePicked = {})

    assertNull(registry.launchedContract)
    clickPicker()

    assertTrue(registry.launchedContract is ActivityResultContracts.PickVisualMedia)
    val request = registry.launchedInput as PickVisualMediaRequest
    assertEquals(ActivityResultContracts.PickVisualMedia.ImageOnly, request.mediaType)
  }

  @Test
  fun selectedImageUriIsDeliveredUnchanged() {
    var selectedUri: Uri? = null
    setContent(onImagePicked = { selectedUri = it })
    clickPicker()

    val expectedUri = Uri.parse("content://media/external/images/media/120%20with%20space")
    registry.dispatchResult(Activity.RESULT_OK, Intent().setData(expectedUri))
    composeTestRule.waitForIdle()

    assertEquals(expectedUri, selectedUri)
  }

  @Test
  fun cancellationDeliversNull() {
    var callbackCount = 0
    var selectedUri: Uri? = Uri.parse("content://previous/image")
    setContent(
        onImagePicked = {
          callbackCount++
          selectedUri = it
        },
    )
    clickPicker()

    registry.dispatchResult(Activity.RESULT_CANCELED, null)
    composeTestRule.waitForIdle()

    assertEquals(1, callbackCount)
    assertNull(selectedUri)
  }

  private fun setContent(onImagePicked: (Uri?) -> Unit) {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
        PickerTestContent(onImagePicked)
      }
    }
  }

  private fun clickPicker() {
    composeTestRule.onNodeWithTag(PICKER_BUTTON_TAG).performClick()
  }

  @Composable
  private fun PickerTestContent(onImagePicked: (Uri?) -> Unit) {
    val launchPicker = rememberGalleryImagePicker(onImagePicked)
    Button(
        onClick = launchPicker,
        modifier = Modifier.fillMaxSize().testTag(PICKER_BUTTON_TAG),
    ) {
      Text("Pick image")
    }
  }

  private class RecordingActivityResultRegistry : ActivityResultRegistry() {
    var launchedRequestCode: Int? = null
      private set

    var launchedContract: ActivityResultContract<*, *>? = null
      private set

    var launchedInput: Any? = null
      private set

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launchedRequestCode = requestCode
      launchedContract = contract
      launchedInput = input
    }

    fun dispatchResult(resultCode: Int, intent: Intent?) {
      dispatchResult(checkNotNull(launchedRequestCode), resultCode, intent)
    }
  }

  private companion object {
    const val PICKER_BUTTON_TAG = "gallery_picker_button"
  }
}
