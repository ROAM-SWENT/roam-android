package com.github.roamswent.roam.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutesTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun guideDetailKeepsAFirestoreStyleIdAsIs() {
    assertEquals("guide_detail/aB3dE5fG7hJ9kL1mN2oP", Routes.guideDetail("aB3dE5fG7hJ9kL1mN2oP"))
  }

  @Test
  fun guideDetailEncodesCharactersThatWouldBreakThePath() {
    assertEquals("guide_detail/a%2Fb%20c", Routes.guideDetail("a/b c"))
    assertEquals("guide_detail/a%3Fb%23c%25d", Routes.guideDetail("a?b#c%d"))
    assertEquals("guide_detail/a%2Bb", Routes.guideDetail("a+b"))
  }

  @Test
  fun destinationReceivesAFirestoreStyleId() {
    assertEquals("aB3dE5fG7hJ9kL1mN2oP", guideIdReceivedAfterNavigatingTo("aB3dE5fG7hJ9kL1mN2oP"))
  }

  @Test
  fun destinationReceivesAnIdWithASlashAndASpace() {
    assertEquals("a/b c", guideIdReceivedAfterNavigatingTo("a/b c"))
  }

  @Test
  fun destinationReceivesAnIdWithQueryFragmentAndPercentCharacters() {
    assertEquals("a?b#c%d", guideIdReceivedAfterNavigatingTo("a?b#c%d"))
  }

  @Test
  fun destinationReceivesAnIdWithAPlusAndAccents() {
    assertEquals("a+b Família", guideIdReceivedAfterNavigatingTo("a+b Família"))
  }

  private fun guideIdReceivedAfterNavigatingTo(guideId: String): String? {
    lateinit var navController: NavHostController
    var received: String? = null
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {}
        composable(Routes.GuideDetail) { backStackEntry ->
          received = backStackEntry.arguments?.getString(Routes.GuideIdArg)
        }
      }
    }

    composeTestRule.runOnIdle { navController.navigate(Routes.guideDetail(guideId)) }
    composeTestRule.waitForIdle()
    return received
  }
}
