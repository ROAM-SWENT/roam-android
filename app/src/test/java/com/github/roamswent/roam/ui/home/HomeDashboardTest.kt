/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeDashboardTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greetingHeaderDisplaysDefaultTexts() {
    composeTestRule.setContent { SampleAppTheme { HomeGreetingHeader() } }

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello Victor,")
    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_title, useUnmergedTree = true)
        .assertTextEquals("Ready to explore?")
  }

  @Test
  fun greetingHeaderDisplaysCustomUserName() {
    composeTestRule.setContent { SampleAppTheme { HomeGreetingHeader(userName = "Amélie Dupont") } }

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello Amélie Dupont,")
  }

  @Test
  fun blankUserNameFallsBackToDefault() {
    val userName = mutableStateOf("")
    composeTestRule.setContent { SampleAppTheme { HomeGreetingHeader(userName = userName.value) } }

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello Victor,")

    userName.value = " "

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello Victor,")
  }

  @Test
  fun scanPromptCardDisplaysTitleAndBody() {
    composeTestRule.setContent { SampleAppTheme { ScanPromptCard(onPromptClick = {}) } }

    composeTestRule
        .onNodeWithTag(C.Tag.home_scan_card_title, useUnmergedTree = true)
        .assertTextEquals("A monument in front of you?")
    composeTestRule
        .onNodeWithTag(C.Tag.home_scan_card_body, useUnmergedTree = true)
        .assertTextEquals("Tap the scan button to discover what you're looking at.")
  }

  @Test
  fun scanPromptCardClickInvokesInjectedCallback() {
    var clicks = 0
    composeTestRule.setContent { SampleAppTheme { ScanPromptCard(onPromptClick = { clicks++ }) } }

    val card = composeTestRule.onNodeWithTag(C.Tag.home_scan_card)
    card.performClick()
    assertEquals(1, clicks)
    card.performClick()
    assertEquals(2, clicks)
  }

  @Test
  fun scanCardIconIsDisplayed() {
    composeTestRule.setContent { SampleAppTheme { ScanPromptCard(onPromptClick = {}) } }

    composeTestRule
        .onNodeWithTag(C.Tag.home_scan_card_icon, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Monument")
  }

  @Test
  fun homeScreenShowsDashboardAboveBottomBar() {
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(onScanClicked = {}, userName = "Test User") }
    }

    composeTestRule
        .onNodeWithTag(C.Tag.home_greeting_subtitle, useUnmergedTree = true)
        .assertTextEquals("Hello Test User,")
    composeTestRule.onNodeWithTag(C.Tag.home_scan_card).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.home_bottom_bar).assertIsDisplayed()
  }
}
