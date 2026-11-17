/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfilePictureTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun profilePictureInitial_trimsNameAndUppercasesFirstCharacter() {
    assertEquals("A", profilePictureInitial("  alice  "))
  }

  @Test
  fun profilePictureInitial_returnsQuestionMarkForBlankName() {
    assertEquals("?", profilePictureInitial("   "))
  }

  @Test
  fun profilePicture_withoutAvatarUrl_showsInitial() {
    composeTestRule.setContent {
      SampleAppTheme {
        ProfilePicture(name = "Alice")
      }
    }

    composeTestRule.onNodeWithText("A").assertIsDisplayed()
  }

  @Test
  fun profilePicture_withAvatarUrl_isClickable() {
    var clicked = false

    composeTestRule.setContent {
      SampleAppTheme {
        ProfilePicture(
            name = "Alice",
            avatarUrl = "https://example.com/avatar.png",
            modifier = Modifier.testTag("profile-picture"),
            onClick = { clicked = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("profile-picture").performClick()

    assertTrue(clicked)
  }

  @Test
  fun profilePictureBitmap_create_returnsCircularBitmap() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val bitmap = ProfilePictureBitmap.create(context, "Alice")

    assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    assertTrue(bitmap.width > 0)
    assertEquals(bitmap.width, bitmap.height)
  }
}
