package com.github.roamswent.roam.ui.scan

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.espresso.Espresso.pressBack
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanReviewScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun reviewPlaceholderRemainsAfterBackPress() {
    composeTestRule.activity.setContent { SampleAppTheme { ScanReviewScreen() } }
    composeTestRule.onNodeWithTag(C.Tag.scan_review_placeholder).assertIsDisplayed()

    pressBack()

    composeTestRule.onNodeWithTag(C.Tag.scan_review_placeholder).assertIsDisplayed()
  }
}
