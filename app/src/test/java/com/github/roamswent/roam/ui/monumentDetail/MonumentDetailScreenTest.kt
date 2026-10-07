package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.github.roamswent.roam.resources.C
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MonumentDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun monumentDetailScreenDisplaysImageAndTextCorrectly() {
    val textTest =
        """
        Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non risus. Suspendisse lectus tortor, 
        dignissim sit amet, adipiscing nec, ultricies sed, dolor. Cras elementum ultrices diam. Maecenas ligula massa, 
        varius a, semper congue, euismod non, mi. Proin porttitor, orci nec nonummy molestie, enim est eleifend mi, 
        non fermentum diam nisl sit amet erat. Duis semper. Duis arcu massa, scelerisque vitae, consequat in, pretium a, enim. 
        Pellentesque congue. Ut in risus volutpat libero pharetra tempor. Cras vestibulum bibendum augue. 
        Praesent egestas leo in pede. Praesent blandit odio eu enim. Pellentesque sed dui ut augue blandit sodales. 
        Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Aliquam nibh. 
        Mauris ac mauris sed pede pellentesque fermentum. Maecenas adipiscing ante non diam sodales hendrerit.
        """
            .trimIndent()
    val falseImage = ColorPainter(Color.LightGray)

    composeTestRule.setContent {
      MonumentDetailScreen(
          image = falseImage,
          text = textTest,
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_text).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_drag_handle).performTouchInput {
      swipeUp()
      swipeDown()
    }
  }
}
