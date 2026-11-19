// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.permission.LocationPermissionState
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.UiSettings
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowViewGroup

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
class ExploreMapIntegrationTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val coordinates = Coordinates(48.85837, 2.29448)
  private val monument = ExploreMonument("eiffel", "Eiffel Tower", coordinates)
  private val grantedLocationPermission =
      LocationPermissionState(true, LocationPermissionDecision.GRANTED)
  private val markers = mutableListOf<RenderedMarker>()
  private lateinit var clickListener: GoogleMap.OnMarkerClickListener
  private val darkTheme = mutableStateOf(false)
  private val density = mutableStateOf(1f)
  private val requestedPhotoSizes = ConcurrentLinkedQueue<Size>()

  @Before
  fun connectMap() {
    val map = mock<GoogleMap>()
    val uiSettings = mock<UiSettings>()
    whenever(map.uiSettings).thenReturn(uiSettings)
    doAnswer {
          clickListener = it.getArgument(0)
          null
        }
        .whenever(map)
        .setOnMarkerClickListener(any())
    whenever(map.addMarker(any())).thenAnswer {
      val options = it.getArgument<MarkerOptions>(0)
      val rendered = RenderedMarker(options.title.orEmpty(), options.position, options.icon)
      doAnswer { invocation ->
            rendered.icon = invocation.getArgument(0)
            null
          }
          .whenever(rendered.marker)
          .setIcon(anyOrNull())
      markers += rendered
      rendered.marker
    }
    ExploreMapViewShadow.map = map
    ExploreBitmapDescriptorShadow.bitmaps.clear()
    ExploreBitmapDescriptorShadow.failDescriptorCreation = false
    Coil.setImageLoader(
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .eventListener(
                object : EventListener {
                  override fun resolveSizeEnd(request: ImageRequest, size: Size) {
                    requestedPhotoSizes.add(size)
                  }
                }
            )
            .build()
    )
  }

  @After fun resetImageLoader() = Coil.reset()

  @Test
  @Config(qualifiers = "w320dp-h480dp")
  fun mapUsesBlueDotAndShowsMonumentDetailsAboveScan() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    showExplore(monument)

    composeTestRule.runOnIdle {
      verify(ExploreMapViewShadow.map).isMyLocationEnabled = true
      assertEquals(listOf(monument.name), markers.map { it.title })
      assertEquals(LatLng(coordinates.latitude, coordinates.longitude), monumentMarker().position)
      assertNotNull(monumentMarker().icon)
      assertTrue(clickListener.onMarkerClick(monumentMarker().marker))
    }
    composeTestRule.onNodeWithText(monument.name).assertIsDisplayed()
    val card = composeTestRule.onNode(hasText(monument.name) and hasClickAction())
    val scan = composeTestRule.onNodeWithTag(C.Tag.dock_scan_button)
    assertTrue(card.getUnclippedBoundsInRoot().bottom <= scan.getUnclippedBoundsInRoot().top)
    composeTestRule.runOnIdle { assertNull(monumentMarker().icon) }

    card.performClick()
    val expandedCard = composeTestRule.onNode(hasScrollAction())
    assertTrue(
        expandedCard.getUnclippedBoundsInRoot().bottom <= scan.getUnclippedBoundsInRoot().top
    )
    composeTestRule.onNodeWithContentDescription("Minimize monument information").performClick()
    composeTestRule.onNodeWithContentDescription("Close monument information").performClick()

    composeTestRule.onNodeWithText(monument.name).assertDoesNotExist()
    composeTestRule.runOnIdle { assertNotNull(monumentMarker().icon) }
  }

  @Test
  fun blueDotStaysDisabledWithoutAndroidLocationPermission() {
    showExplore(monument)

    composeTestRule.runOnIdle {
      val map = ExploreMapViewShadow.map
      verify(map).isMyLocationEnabled = false
      verify(map, never()).isMyLocationEnabled = true
      assertEquals(listOf(monument.name), markers.map { it.title })
    }
  }

  @Test
  fun blueDotTurnsOffWhenExploreReceivesAPermissionDenial() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    val permissionState = MutableStateFlow(grantedLocationPermission)
    showExplore(monument, permissionState)

    composeTestRule.runOnIdle {
      verify(ExploreMapViewShadow.map).isMyLocationEnabled = true
      permissionState.value = LocationPermissionState(false, LocationPermissionDecision.DENIED)
    }

    composeTestRule.runOnIdle { verify(ExploreMapViewShadow.map).isMyLocationEnabled = false }
  }

  @Test
  fun placeholderIconTintAndBorderFollowThemeChanges() {
    showExplore(monument)
    composeTestRule.runOnIdle {
      val bitmap = monumentBitmap()
      assertEquals(
          "Light icon tint",
          lightColorScheme().onSurfaceVariant.toArgb(),
          bitmap.getPixel(32, 28),
      )
      assertEquals("Light border", Color.White.toArgb(), bitmap.getPixel(9, 28))
    }

    composeTestRule.runOnIdle { darkTheme.value = true }

    composeTestRule.runOnIdle {
      val bitmap = monumentBitmap()
      val colors = darkColorScheme()
      assertEquals("Dark icon tint", colors.onSurfaceVariant.toArgb(), bitmap.getPixel(32, 28))
      assertEquals("Dark border", colors.outline.toArgb(), bitmap.getPixel(9, 28))
      assertEquals("Dark placeholder fill", colors.surfaceVariant.toArgb(), bitmap.getPixel(14, 28))
    }
  }

  @Test
  fun decodedPhotoIsRenderedAgainAtTheNewDisplayDensity() {
    val photo = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val photoColor = 0xff19886a.toInt()
    photo.eraseColor(photoColor)
    val photoBytes =
        ByteArrayOutputStream()
            .apply { photo.compress(Bitmap.CompressFormat.PNG, 100, this) }
            .toByteArray()
    showExplore(monument.copy(photoBytes = photoBytes))
    waitForPhoto(width = 64, color = photoColor)

    composeTestRule.runOnIdle { density.value = 2f }

    waitForPhoto(width = 128, color = photoColor)
    assertEquals(listOf(Size(48, 48), Size(96, 96)), requestedPhotoSizes.toList())
  }

  @Test
  fun photoLoaderExceptionKeepsTheBuiltInPlaceholder() {
    val failure = IllegalStateException("Photo loader failed")
    val loader = mock<ImageLoader>()
    runBlocking { whenever(loader.execute(any())).thenThrow(failure) }
    Coil.setImageLoader(loader)
    showExplore(monument.copy(photoBytes = byteArrayOf(1, 2, 3)))

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      ShadowLog.getLogsForTag("ExploreMonumentMarker").any { it.throwable === failure }
    }

    composeTestRule.runOnIdle {
      assertNotNull(monumentMarker().icon)
      assertNotEquals(monumentBitmap().getPixel(32, 28), monumentBitmap().getPixel(14, 28))
    }
  }

  @Test
  fun markerRemainsSelectableWhenDescriptorCreationFails() {
    ExploreBitmapDescriptorShadow.failDescriptorCreation = true
    showExplore(monument)

    composeTestRule.runOnIdle {
      assertNull(monumentMarker().icon)
      assertTrue(clickListener.onMarkerClick(monumentMarker().marker))
    }
    composeTestRule.onNodeWithText(monument.name).assertIsDisplayed()
  }

  private fun showExplore(
      monument: ExploreMonument,
      permissionState: MutableStateFlow<LocationPermissionState> =
          MutableStateFlow(grantedLocationPermission),
  ) {
    val viewModel = mock<ExploreViewModel>()
    whenever(viewModel.uiState)
        .thenReturn(MutableStateFlow(ExploreUiState.LocationAvailable(coordinates)))
    whenever(viewModel.monuments).thenReturn(MutableStateFlow(listOf(monument)))
    whenever(viewModel.monumentsLoaded).thenReturn(MutableStateFlow(true))
    whenever(viewModel.offlineBaseMap).thenReturn(MutableStateFlow(emptyList()))
    val permission = mock<LocationPermissionController>()
    whenever(permission.state).thenReturn(permissionState)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme.value, dynamicColor = false) {
        CompositionLocalProvider(LocalDensity provides Density(density.value)) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.padding(bottom = dockContentClearance())) {
              ExploreScreen(
                  onProfileClicked = {},
                  profileName = "Alex",
                  exploreViewModel = viewModel,
                  locationPermissionController = permission,
              )
            }
            AppBottomDock(
                selected = DockDestination.EXPLORE,
                onDestinationSelected = {},
                onScanClick = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
          }
        }
      }
    }
  }

  private fun monumentMarker(): RenderedMarker = markers.single { it.title == monument.name }

  private fun monumentBitmap(): Bitmap =
      ExploreBitmapDescriptorShadow.bitmaps.getValue(monumentMarker().icon)

  private fun waitForPhoto(width: Int, color: Int) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.runOnIdle {
        val marker = markers.firstOrNull { it.title == monument.name }
        val bitmap = ExploreBitmapDescriptorShadow.bitmaps[marker?.icon]
        bitmap != null &&
            bitmap.width == width &&
            bitmap.getPixel(width / 2, bitmap.height * 2 / 5) == color
      }
    }
  }

  private class RenderedMarker(
      val title: String,
      val position: LatLng,
      var icon: BitmapDescriptor?,
  ) {
    val marker = mock<Marker>()
  }
}

// The Maps renderer requires Play Services. Supply its SDK boundary so Maps Compose still creates
// and updates its real marker nodes and dispatches its real marker-click listener.
@Implements(MapView::class)
class ExploreMapViewShadow : ShadowViewGroup() {
  @Implementation fun getMapAsync(callback: OnMapReadyCallback) = callback.onMapReady(map)

  @Implementation fun onCreate(savedInstanceState: Bundle?) {}

  @Implementation fun onStart() {}

  @Implementation fun onResume() {}

  @Implementation fun onPause() {}

  @Implementation fun onStop() {}

  @Implementation fun onDestroy() {}

  companion object {
    lateinit var map: GoogleMap
  }
}

@Implements(CameraUpdateFactory::class)
class ExploreCameraUpdateShadow {
  companion object {
    @JvmStatic
    @Implementation
    fun newCameraPosition(position: CameraPosition): CameraUpdate = mock()
  }
}

@Implements(BitmapDescriptorFactory::class)
class ExploreBitmapDescriptorShadow {
  companion object {
    val bitmaps = mutableMapOf<BitmapDescriptor?, Bitmap>()
    var failDescriptorCreation = false

    @JvmStatic
    @Implementation
    fun fromBitmap(bitmap: Bitmap): BitmapDescriptor {
      check(!failDescriptorCreation)
      return mock<BitmapDescriptor>().also { bitmaps[it] = bitmap }
    }
  }
}
