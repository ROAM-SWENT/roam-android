// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.monumentDetail

import android.graphics.Bitmap
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.GuideSection
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.collection.CollectionFriend
import com.github.roamswent.roam.ui.loading.ExploreOptInUiState
import com.github.roamswent.roam.ui.loading.ExploreUnavailableReason
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.guideCalloutContainer
import com.github.roamswent.roam.ui.theme.guideEyebrow
import com.github.roamswent.roam.ui.theme.guidePanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MonumentDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.92,
          history =
              "Built in 1889 as the entrance arch for the World's Fair.\n\n" +
                  "Its iron framework took just over two years to assemble.",
          facts = listOf("330 meters tall", "- Repainted every seven years"),
          culture =
              "The globally recognized symbol of Paris and France.\n\n" +
                  "It hosts celebrations that bring Parisians together.",
          language = "en",
      )

  @Test
  fun monumentDetailScreenDisplaysImageAndStructuredGuideCorrectly() {
    val falseImage = ColorPainter(Color.LightGray)

    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = falseImage,
            guide = guide,
            onBackClick = {},
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_text).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_title)
        .assertIsDisplayed()
        .assertTextEquals(guide.monumentName)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_confidence)
        .assertIsDisplayed()
        .assertTextEquals("92% match")
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_history)
        .assertIsDisplayed()
        .assertTextEquals(guide.history)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_culture)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals(guide.culture)
    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_fact_item).assertCountEquals(2)
    composeTestRule.onNodeWithText("330 meters tall").performScrollTo().assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Repainted every seven years")
        .performScrollTo()
        .assertIsDisplayed()
  }

  private data object RejectingOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect() = error("The guide scroll area must disable edge effects")
  }

  @Test
  fun swipingScrollsTheGuideInsideAStationaryPanelWithoutOverscroll() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalOverscrollFactory provides RejectingOverscrollFactory) {
        SampleAppTheme {
          MonumentDetailScreen(
              image = ColorPainter(Color.LightGray),
              guide = guide.copy(history = guide.history.repeat(12)),
              onBackClick = {},
          )
        }
      }
    }

    val panel = composeTestRule.onNodeWithTag(C.Tag.monument_detail_panel)
    val scrollable = composeTestRule.onNodeWithTag(C.Tag.monument_detail_text)
    val picture = composeTestRule.onNodeWithTag(C.Tag.monument_detail_picture)
    val initialPanel = panel.fetchSemanticsNode().boundsInRoot
    val initialPicture = picture.fetchSemanticsNode().boundsInRoot
    val title = composeTestRule.onNodeWithTag(C.Tag.monument_detail_title)
    val initialTitle = title.fetchSemanticsNode().boundsInRoot

    scrollable.performTouchInput { swipeDown() }
    assertEquals(initialTitle, title.fetchSemanticsNode().boundsInRoot)

    scrollable.performTouchInput { swipeUp() }

    assertEquals(initialPanel, panel.fetchSemanticsNode().boundsInRoot)
    assertEquals(initialPicture, picture.fetchSemanticsNode().boundsInRoot)
    title.assertIsNotDisplayed()
  }

  @Test
  fun matchBadgeUsesThreeScoreBandsInBothThemes() {
    var confidence by mutableStateOf(0.49)
    var dark by mutableStateOf(false)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = dark, dynamicColor = false) {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide.copy(confidence = confidence),
            onBackClick = {},
        )
      }
    }

    val badge = composeTestRule.onNodeWithTag(C.Tag.monument_detail_confidence)
    for (darkTheme in listOf(false, true)) {
      composeTestRule.runOnIdle { dark = darkTheme }
      val colors =
          listOf(49, 50, 79, 80).map { score ->
            composeTestRule.runOnIdle { confidence = score / 100.0 }
            badge.assertTextEquals("$score% match")
            val bitmap = badge.captureToImage().asAndroidBitmap()
            bitmap.getPixel(bitmap.width / 2, 1)
          }
      assertEquals(colors[1], colors[2])
      assertEquals(3, colors.toSet().size)
    }
  }

  @Test
  fun backButtonInvokesCallback() {
    var backClicked = false
    composeTestRule.setContent {
      MonumentDetailScreen(
          image = ColorPainter(Color.LightGray),
          guide = guide,
          onBackClick = { backClicked = true },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertTrue(backClicked)
  }

  @Test
  fun optionalPrimitivesAppearAlongsideTheCoreGuide() {
    val sectionGuide =
        guide.copy(
            overview = "An iron landmark.",
            facts = listOf("The iron expands in summer."),
            timeline =
                listOf(
                    GuideMilestone(1964, "Listed as a historic monument."),
                    GuideMilestone(1889, "Opened to the public."),
                    GuideMilestone(1887, "Construction began."),
                ),
            sections =
                listOf(
                    GuideSection(
                        "Architecture",
                        "**Look up at the framework.**\n- Iron legs\n* Lattice bracing",
                    ),
                    GuideSection("Legends & Myths", "A legend."),
                ),
        )

    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = sectionGuide,
            onBackClick = {},
        )
      }
    }

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_overview)
        .assertIsDisplayed()
        .assertTextEquals("An iron landmark.")
    val milestones = composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_timeline_item)
    milestones.assertCountEquals(3)
    milestones[0].onChildren().onFirst().assertTextEquals("1887")
    milestones[1].onChildren().onFirst().assertTextEquals("1889")
    milestones[2].onChildren().onFirst().assertTextEquals("1964")

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_history)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_culture)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("Fun Fact").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_interest).assertCountEquals(2)
    composeTestRule
        .onNodeWithText("Look up at the framework.\n• Iron legs\n• Lattice bracing")
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("A legend.").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_timeline).performScrollTo()
    composeTestRule.onNodeWithText("Construction began.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Listed as a historic monument.").assertIsDisplayed()
  }

  @Test
  fun aSingleBceMilestoneDrawsAnUnconnectedMarkerOnTheRightInRtl() {
    var markerColor = 0
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true, dynamicColor = false) {
        markerColor = MaterialTheme.guideEyebrow.toArgb()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          MonumentDetailScreen(
              image = ColorPainter(Color.LightGray),
              guide =
                  guide.copy(
                      history = "",
                      culture = "",
                      facts = emptyList(),
                      timeline = listOf(GuideMilestone(-200, "The site was first settled.")),
                  ),
              onBackClick = {},
          )
        }
      }
    }

    val row = composeTestRule.onNodeWithTag(C.Tag.monument_detail_timeline_item).performScrollTo()
    composeTestRule.onNodeWithText("200 BCE").assertIsDisplayed()
    val bitmap = row.captureToImage().asAndroidBitmap()
    val edgeWidth = bitmap.width / 10
    assertTrue(
        containsColor(
            Bitmap.createBitmap(bitmap, bitmap.width - edgeWidth, 0, edgeWidth, bitmap.height / 2),
            markerColor,
        )
    )
    assertFalse(
        containsColor(
            Bitmap.createBitmap(bitmap, 0, bitmap.height / 2, bitmap.width, bitmap.height / 2),
            markerColor,
        )
    )
  }

  @Test
  fun absentContentDoesNotCreateEmptySections() {
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide =
                guide.copy(
                    overview = " ",
                    history = "",
                    culture = "",
                    facts = emptyList(),
                    sections =
                        listOf(GuideSection("Architecture", ""), GuideSection("", "A note.")),
                ),
            onBackClick = {},
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_title).assertIsDisplayed()
    listOf(
            C.Tag.monument_detail_overview,
            C.Tag.monument_detail_history,
            C.Tag.monument_detail_culture,
            C.Tag.monument_detail_timeline,
            C.Tag.monument_detail_facts,
            C.Tag.monument_detail_interest,
        )
        .forEach { tag -> composeTestRule.onNodeWithTag(tag).assertDoesNotExist() }
  }

  @Test
  @Config(qualifiers = "w320dp-h480dp-port")
  fun wrapsLongTitlesAndScrollsToTheLastFactAtLargeFontScale() {
    val longTitle = "The Historic Monument and Gardens of the Northern City"
    val facts = (1..20).map { "Fact $it about this historic monument." }
    composeTestRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.8f)) {
        SampleAppTheme {
          MonumentDetailScreen(
              image = ColorPainter(Color.LightGray),
              guide =
                  guide.copy(
                      monumentName = longTitle,
                      history = guide.history.repeat(25),
                      facts = facts,
                  ),
              onBackClick = {},
          )
        }
      }
    }

    val layouts = mutableListOf<TextLayoutResult>()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_title)
        .assertTextEquals(longTitle)
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertTrue(layouts.single().lineCount > 1)
    assertFalse(layouts.single().hasVisualOverflow)
    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_fact_item).assertCountEquals(20)
    composeTestRule.onNodeWithText(facts.last()).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).assertIsDisplayed()
  }

  @Test
  fun panelAndCalloutsUseTheirThemeColors() {
    val image = ColorPainter(Color.LightGray)
    var darkTheme by mutableStateOf(false)
    var lightSurface = 0
    var lightCallout = 0
    var darkSurface = 0
    var darkCallout = 0
    var darkOnSurface = 0
    var darkOnSurfaceVariant = 0

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        if (darkTheme) {
          darkSurface = MaterialTheme.colorScheme.surface.toArgb()
          darkOnSurface = MaterialTheme.colorScheme.onSurface.toArgb()
          darkOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
          darkCallout = MaterialTheme.colorScheme.surfaceVariant.toArgb()
        } else {
          lightSurface = MaterialTheme.guidePanel.toArgb()
          lightCallout = MaterialTheme.guideCalloutContainer.toArgb()
        }
        MonumentDetailScreen(
            image = image,
            guide = guide,
            onBackClick = {},
        )
      }
    }

    val lightPanel =
        composeTestRule
            .onNodeWithTag(C.Tag.monument_detail_panel)
            .captureToImage()
            .asAndroidBitmap()
    assertEquals(lightSurface, lightPanel.getPixel(0, lightPanel.height / 2))
    val callout = composeTestRule.onNodeWithTag(C.Tag.monument_detail_facts)
    callout.performScrollTo()
    assertTrue(containsColor(callout.captureToImage().asAndroidBitmap(), lightCallout))

    composeTestRule.runOnIdle { darkTheme = true }

    val darkPanel =
        composeTestRule
            .onNodeWithTag(C.Tag.monument_detail_panel)
            .captureToImage()
            .asAndroidBitmap()
    assertEquals(darkSurface, darkPanel.getPixel(0, darkPanel.height / 2))
    assertNotEquals(Color.White.toArgb(), darkPanel.getPixel(0, darkPanel.height / 2))
    assertTrue(containsColor(darkPanel, darkOnSurface))
    assertTrue(containsColor(darkPanel, darkOnSurfaceVariant))
    assertTrue(containsColor(callout.captureToImage().asAndroidBitmap(), darkCallout))
  }

  @Test
  fun favouriteButtonIsHiddenByDefault() {
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(image = ColorPainter(Color.LightGray), guide = guide, onBackClick = {})
      }
    }

    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_favorite_button).assertCountEquals(0)
  }

  @Test
  fun favouriteButtonReflectsStateAndInvokesCallback() {
    var isFavorite by mutableStateOf(false)
    var clicks = 0
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            isFavorite = isFavorite,
            onFavoriteClick = {
              clicks++
              isFavorite = !isFavorite
            },
        )
      }
    }
    val button = composeTestRule.onNodeWithTag(C.Tag.monument_detail_favorite_button)

    button.assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_favorite_button, useUnmergedTree = true)
        .onChildren()
        .onFirst()
        .assertContentDescriptionEquals("Add to favourites")

    button.performClick()

    assertEquals(1, clicks)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_favorite_button, useUnmergedTree = true)
        .onChildren()
        .onFirst()
        .assertContentDescriptionEquals("Remove from favourites")
  }

  @Test
  fun favouriteErrorShowsSnackbarAndReportsItShown() {
    var errorShown = false
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            isFavorite = false,
            showFavoriteError = true,
            onFavoriteErrorShown = { errorShown = true },
        )
      }
    }

    composeTestRule
        .onNodeWithText("Couldn’t update favourites. Please try again.")
        .assertIsDisplayed()
    composeTestRule.mainClock.advanceTimeBy(10_000)
    composeTestRule.waitForIdle()
    assertTrue(errorShown)
  }

  @Test
  fun privateGuideShowsShareActionAndPrivateStatus() {
    var clicked = false
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            onShareClick = { clicked = true },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_share_button).assertIsDisplayed()
    composeTestRule.onNodeWithText("Share with friends").assertIsDisplayed().performClick()
    composeTestRule
        .onNodeWithText("Not shared yet. Only you can see this monument.")
        .performScrollTo()
        .assertIsDisplayed()
    assertTrue(clicked)
  }

  @Test
  fun sharedGuideShowsRecipientNamesAndManageAction() {
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            sharingState =
                MonumentDetailSharingState(
                    settings =
                        MonumentShareSettings(
                            audience = ShareAudience.SELECTED_FRIENDS,
                            sharedWithUids = setOf("alice", "grace", "sam"),
                        ),
                    recipients =
                        listOf(
                            CollectionFriend("alice", "Ada"),
                            CollectionFriend("grace", "Grace"),
                            CollectionFriend("sam", "Sam"),
                        ),
                ),
        )
      }
    }

    composeTestRule.onNodeWithText("Manage sharing").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Shared with Ada, Grace and 1 other")
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_share_avatar + "_0")
        .assertCountEquals(1)
    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_share_avatar + "_1")
        .assertCountEquals(1)
  }

  @Test
  fun sharedGuideFormatsOneAndTwoRecipients() {
    var recipients by mutableStateOf(listOf(CollectionFriend("alice", "Ada")))
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            sharingState =
                MonumentDetailSharingState(
                    settings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
                    recipients = recipients,
                ),
        )
      }
    }

    composeTestRule.onNodeWithText("Shared with Ada").performScrollTo().assertIsDisplayed()
    recipients = listOf(CollectionFriend("alice", "Ada"), CollectionFriend("grace", "Grace"))
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText("Shared with Ada and Grace")
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun sharedGuideFormatsMoreThanThreeRecipients() {
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            sharingState =
                MonumentDetailSharingState(
                    settings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
                    recipients =
                        listOf(
                            CollectionFriend("alice", "Ada"),
                            CollectionFriend("grace", "Grace"),
                            CollectionFriend("sam", "Sam"),
                            CollectionFriend("lee", "Lee"),
                        ),
                ),
        )
      }
    }

    composeTestRule
        .onNodeWithText("Shared with Ada, Grace and 2 others")
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun tooFarGuideShowsContinueAndUnavailableMessage() {
    var backClicked = false
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = { backClicked = true },
            sharingState = MonumentDetailSharingState(unavailableBecauseTooFar = true),
        )
      }
    }

    composeTestRule.onNodeWithText("Continue").assertIsDisplayed().performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_share_unavailable)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            "This photo was taken too far from the Eiffel Tower. The guide stays in your collection, for you only."
        )
        .assertIsDisplayed()
    assertTrue(backClicked)
  }

  private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        if (bitmap.getPixel(x, y) == color) return true
      }
    }
    return false
  }

  private val scanLocation = Coordinates(48.8584, 2.2945)

  private fun setExploreContent(
      optIn: ExploreOptInUiState?,
      unavailableReason: ExploreUnavailableReason? = null,
      isGalleryImage: Boolean = false,
      onShowInExploreChange: (Boolean) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            exploreOptIn = optIn,
            onShowInExploreChange = onShowInExploreChange,
            exploreUnavailableReason = unavailableReason,
            isGalleryImage = isGalleryImage,
        )
      }
    }
  }

  @Test
  fun showInExploreIsNotOfferedWithoutOptIn() {
    setExploreContent(optIn = null)

    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_show_in_explore).assertCountEquals(0)
    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_explore_unavailable)
        .assertCountEquals(0)
  }

  private fun assertUnavailableMessage(
      reason: ExploreUnavailableReason,
      expected: String,
      isGalleryImage: Boolean = false,
  ) {
    setExploreContent(optIn = null, unavailableReason = reason, isGalleryImage = isGalleryImage)

    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_show_in_explore).assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_explore_unavailable, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(expected)
  }

  @Test
  fun missingObservedLocationIsExplainedInsteadOfTheSwitch() {
    assertUnavailableMessage(
        ExploreUnavailableReason.NO_OBSERVED_LOCATION,
        "Your location wasn't available during the scan, so this monument can't be added to " +
            "Explore.",
    )
  }

  @Test
  fun missingMonumentLocationIsExplainedInsteadOfTheSwitch() {
    assertUnavailableMessage(
        ExploreUnavailableReason.NO_MONUMENT_LOCATION,
        "We couldn't confirm where this monument is, so it can't be added to Explore.",
    )
  }

  @Test
  fun scanTooFarIsExplainedInsteadOfTheSwitch() {
    assertUnavailableMessage(
        ExploreUnavailableReason.TOO_FAR,
        "You don't seem to be near this monument, so it can't be added to Explore.",
    )
  }

  @Test
  fun galleryPhotoWithoutLocationIsExplainedAsMissingPhotoData() {
    assertUnavailableMessage(
        ExploreUnavailableReason.NO_OBSERVED_LOCATION,
        "This photo has no location data, so this monument can't be added to Explore.",
        isGalleryImage = true,
    )
  }

  @Test
  fun galleryPhotoTooFarIsExplainedForThePhoto() {
    assertUnavailableMessage(
        ExploreUnavailableReason.TOO_FAR,
        "This photo doesn't seem to have been taken near this monument, so it can't be added " +
            "to Explore.",
        isGalleryImage = true,
    )
  }

  @Test
  fun galleryPhotoWithoutMonumentLocationUsesTheSharedMessage() {
    assertUnavailableMessage(
        ExploreUnavailableReason.NO_MONUMENT_LOCATION,
        "We couldn't confirm where this monument is, so it can't be added to Explore.",
        isGalleryImage = true,
    )
  }

  @Test
  fun scanSwitchSaysThePinIsWhereItWasScanned() {
    setExploreContent(ExploreOptInUiState(scanLocation = scanLocation))

    composeTestRule
        .onNodeWithText(
            "Pin this monument on the map where you scanned it.",
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  @Test
  fun gallerySwitchSaysThePinIsWhereThePhotoWasTaken() {
    setExploreContent(ExploreOptInUiState(scanLocation = scanLocation), isGalleryImage = true)

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            "Pin this monument on the map where the photo was taken.",
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  @Test
  fun switchTakesPrecedenceOverAnUnavailableReason() {
    setExploreContent(
        optIn = ExploreOptInUiState(scanLocation = scanLocation),
        unavailableReason = ExploreUnavailableReason.TOO_FAR,
    )

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_explore_unavailable)
        .assertCountEquals(0)
  }

  @Test
  fun showInExploreIsOffByDefaultAndRequestsOptIn() {
    val requests = mutableListOf<Boolean>()
    setExploreContent(ExploreOptInUiState(scanLocation = scanLocation)) { requests += it }

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_show_in_explore)
        .assertIsDisplayed()
        .assertIsOff()
        .performClick()

    assertEquals(listOf(true), requests)
  }

  @Test
  fun showInExploreReflectsSavedLocationAndRequestsOptOut() {
    val requests = mutableListOf<Boolean>()
    setExploreContent(
        ExploreOptInUiState(scanLocation = scanLocation, savedLocation = scanLocation)
    ) {
      requests += it
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).assertIsOn().performClick()

    assertEquals(listOf(false), requests)
    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_show_in_explore_error)
        .assertCountEquals(0)
  }

  @Test
  fun failedExploreUpdateShowsAnError() {
    setExploreContent(
        ExploreOptInUiState(scanLocation = scanLocation, error = IllegalStateException("offline"))
    )

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).assertIsOff()
    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_show_in_explore_error, useUnmergedTree = true)
        .assertTextEquals("Could not update Explore. Try again.")
  }
}
