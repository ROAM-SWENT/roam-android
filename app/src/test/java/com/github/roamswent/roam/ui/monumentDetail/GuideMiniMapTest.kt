// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.guide.ExploreUnavailableReason
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.explore.ExploreBitmapDescriptorShadow
import com.github.roamswent.roam.ui.explore.ExploreCameraUpdateShadow
import com.github.roamswent.roam.ui.explore.ExploreMapViewShadow
import com.github.roamswent.roam.ui.theme.GuideTypography
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    shadows =
        [
            ExploreMapViewShadow::class,
            ExploreCameraUpdateShadow::class,
            ExploreBitmapDescriptorShadow::class,
        ]
)
class GuideMiniMapTest {
  @get:Rule val compose = createComposeRule()

  private val location = Coordinates(48.85826, 2.29450)
  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.98,
          history = "The tower was built for the 1889 World's Fair. ".repeat(24),
          facts = listOf("The iron expands in summer."),
          culture = "A landmark in Paris.",
          language = "en",
          timeline = listOf(GuideMilestone(1889, "Opened.")),
          location = Coordinates(48.859, 2.293),
          monumentLocation = location,
      )
  private val map = mock<GoogleMap>()
  private val settings = mock<UiSettings>()

  @Before
  fun setUpMap() {
    whenever(map.uiSettings).thenReturn(settings)
    whenever(map.addMarker(any())).thenReturn(mock<Marker>())
    ExploreMapViewShadow.map = map
    ExploreBitmapDescriptorShadow.failDescriptorCreation = false
  }

  @Test
  fun scrollToTheFooterLoadsTheExactPinAndSwipesKeepScrollingTheGuide() {
    var opened: Coordinates? = null
    showGuide { opened = it }

    val probe = compose.onNodeWithTag(C.Tag.monument_detail_map_probe)
    probe.assertIsNotDisplayed()
    verifyNoInteractions(map)

    probe.performScrollTo().assertIsDisplayed()
    val inset = compose.onNodeWithTag(C.Tag.monument_detail_map)
    inset.assertDoesNotExist()
    compose.onNodeWithText("Explore nearby").assertDoesNotExist()
    val marker = argumentCaptor<MarkerOptions>()
    val loaded = argumentCaptor<GoogleMap.OnMapLoadedCallback>()
    compose.runOnIdle {
      verify(map).addMarker(marker.capture())
      assertEquals(LatLng(location.latitude, location.longitude), marker.firstValue.position)
      assertNotNull(marker.firstValue.icon)
      verify(settings).isScrollGesturesEnabled = false
      verify(settings).isZoomGesturesEnabled = false
      verify(map).setOnMapLoadedCallback(loaded.capture())
      loaded.firstValue.onMapLoaded()
    }
    probe.assertDoesNotExist()
    inset.performScrollTo().assertIsDisplayed()
    val layouts = mutableListOf<TextLayoutResult>()
    compose.onNodeWithText("Explore nearby").assertIsDisplayed().performSemanticsAction(
        SemanticsActions.GetTextLayoutResult
    ) {
      it(layouts)
    }
    val heading = layouts.single().layoutInput.style
    assertEquals(GuideTypography.titleSmall.fontFamily, heading.fontFamily)
    assertEquals(16.sp, heading.fontSize)
    assertEquals(22.sp, heading.lineHeight)
    assertEquals(FontWeight.SemiBold, heading.fontWeight)
    val timelineY =
        compose.onNodeWithTag(C.Tag.monument_detail_timeline).fetchSemanticsNode().positionInRoot.y
    val insetY = inset.fetchSemanticsNode().positionInRoot.y
    assertTrue(timelineY < insetY)

    inset.performTouchInput { swipeDown() }
    assertTrue(inset.fetchSemanticsNode().positionInRoot.y > insetY)
    assertNull(opened)

    inset.performScrollTo().performTouchInput { click() }
    assertEquals(location, opened)
  }

  @Test
  fun offlineGuideHasNoExploreFooter() {
    showGuide(isOnline = false, darkTheme = true) {}

    assertNoExploreFooter()
    verifyNoInteractions(map)
  }

  @Test
  fun aScanLocationAloneShowsAMissingMonumentLocationWarning() {
    showGuide(guide = guide.copy(monumentLocation = null)) {}

    compose.onNodeWithText("Monument location unavailable").performScrollTo().assertIsDisplayed()
    assertNoExploreFooter()
    verifyNoInteractions(map)
  }

  @Test
  fun galleryLocationMismatchHidesTheMapEvenWithKnownMonumentCoordinates() {
    showGuide(unavailableReason = ExploreUnavailableReason.TOO_FAR, isGalleryImage = true) {}

    compose.onNodeWithText("Can't be added to Explore").performScrollTo().assertIsDisplayed()
    compose
        .onNodeWithText("This photo doesn't seem to have been taken near the monument.")
        .performScrollTo()
        .assertIsDisplayed()
    assertNoExploreFooter()
    verifyNoInteractions(map)
  }

  @Test
  fun timedOutMapLeavesNoFooterAndIgnoresALateCallback() {
    showGuide {}
    compose.onNodeWithTag(C.Tag.monument_detail_map_probe).performScrollTo()
    val loaded = argumentCaptor<GoogleMap.OnMapLoadedCallback>()
    compose.runOnIdle { verify(map).setOnMapLoadedCallback(loaded.capture()) }

    compose.mainClock.advanceTimeBy(GUIDE_MAP_LOAD_TIMEOUT_MS + 100)
    assertNoExploreFooter()

    compose.runOnIdle { loaded.firstValue.onMapLoaded() }
    assertNoExploreFooter()
  }

  private fun assertNoExploreFooter() {
    compose.onNodeWithText("Explore nearby").assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.monument_detail_map).assertDoesNotExist()
    compose.onNodeWithTag(C.Tag.monument_detail_map_probe).assertDoesNotExist()
  }

  private fun showGuide(
      guide: MonumentGuide = this.guide,
      isOnline: Boolean = true,
      darkTheme: Boolean = false,
      unavailableReason: ExploreUnavailableReason? = null,
      isGalleryImage: Boolean = false,
      onOpen: (Coordinates) -> Unit,
  ) {
    compose.setContent {
      SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        MonumentDetailScreen(
            image = ColorPainter(Color.LightGray),
            guide = guide,
            onBackClick = {},
            isOnline = isOnline,
            onOpenInExplore = onOpen,
            exploreUnavailableReason = unavailableReason,
            isGalleryImage = isGalleryImage,
        )
      }
    }
  }
}
