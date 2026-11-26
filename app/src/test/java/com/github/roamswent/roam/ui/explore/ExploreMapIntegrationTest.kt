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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.size.Size
import com.github.roamswent.roam.RoamApp
import com.github.roamswent.roam.RoamAppTestOverrides
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.NotificationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
import com.github.roamswent.roam.model.permission.SharedPreferencesNotificationPermissionDecisionRepository
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.authentication.AuthUIState
import com.github.roamswent.roam.ui.authentication.SignInViewModel
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
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
      val rendered =
          RenderedMarker(
              options.title.orEmpty(),
              options.position,
              options.icon,
              Offset(options.anchorU, options.anchorV),
          )
      doAnswer { invocation ->
            rendered.icon = invocation.getArgument(0)
            null
          }
          .whenever(rendered.marker)
          .setIcon(anyOrNull())
      doAnswer { invocation ->
            rendered.position = invocation.getArgument(0)
            null
          }
          .whenever(rendered.marker)
          .setPosition(any())
      doAnswer { invocation ->
            rendered.title = invocation.getArgument(0)
            null
          }
          .whenever(rendered.marker)
          .setTitle(any())
      doAnswer {
            markers.remove(rendered)
            null
          }
          .whenever(rendered.marker)
          .remove()
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

  @Test
  fun tappingSharedPinOpensItsDetailThroughAppNavigation() {
    val guide =
        Guide(
            id = "shared-guide",
            photoBytes = byteArrayOf(),
            monumentName = "Shared tower",
            confidence = 1.0,
            history = "A shared landmark",
            facts = emptyList(),
            culture = "",
            discoveryDate = null,
            location = coordinates,
            shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
        )
    val profile = PublicProfile("viewer", "Viewer")
    val friends = mock<FriendRepository>()
    val profiles = mock<PublicProfileRepository>()
    runBlocking {
      whenever(friends.getFriends()).thenReturn(listOf(Friendship("friendship", "owner")))
      whenever(profiles.getCurrentProfile()).thenReturn(profile)
      whenever(profiles.getProfile("owner")).thenReturn(PublicProfile("owner", "Alice"))
    }
    val auth = mock<SignInViewModel>()
    whenever(auth.uiState).thenReturn(MutableStateFlow(AuthUIState(user = mock())))
    val permission = mock<LocationPermissionController>()
    whenever(permission.state).thenReturn(MutableStateFlow(grantedLocationPermission))
    val overrides =
        RoamAppTestOverrides(
            isOnline = true,
            savedGuideRepository = FakeSavedGuideRepository(),
            friendRepository = friends,
            publicProfileRepository = profiles,
            monumentSharingRepository =
                FakeMonumentSharingRepository(mapOf("owner" to listOf(guide))),
            exploreLocationProvider = mock(),
            exploreLocationPermissionController = permission,
            notificationInstallationRepository = mock(),
        )
    SharedPreferencesLocationPermissionDecisionRepository(
            ApplicationProvider.getApplicationContext()
        )
        .saveDecision(LocationPermissionDecision.DENIED)
    SharedPreferencesNotificationPermissionDecisionRepository(
            ApplicationProvider.getApplicationContext()
        )
        .saveDecision(NotificationPermissionDecision.DENIED)
    composeTestRule.setContent { SampleAppTheme { RoamApp(auth, testOverrides = overrides) } }
    val explore = composeTestRule.onNodeWithTag(C.Tag.dock_tab_explore)
    waitForTag(C.Tag.dock_tab_explore)
    explore.performClick()
    composeTestRule.waitUntil(5_000) {
      composeTestRule.runOnIdle { markers.any { it.title == guide.monumentName } }
    }
    composeTestRule.runOnIdle {
      assertTrue(clickListener.onMarkerClick(marker(guide.monumentName).marker))
    }
    val title = composeTestRule.onNodeWithTag(C.Tag.monument_detail_title)
    waitForTag(C.Tag.monument_detail_title)
    title.assertTextEquals(guide.monumentName)
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun sharedPinsUseTheirOwnersAvatarAndOpenTheCorrectGuideEvenWhenIdsOverlap() {
    val photo = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val avatarColor = 0xff19886a.toInt()
    photo.eraseColor(avatarColor)
    val avatar =
        ByteArrayOutputStream()
            .apply { photo.compress(Bitmap.CompressFormat.PNG, 100, this) }
            .toByteArray()
    val alice = PublicProfile("alice", "Alice", avatarBytes = avatar)
    val first = monument.copy(name = "Shared Eiffel Tower")
    val second = ExploreMonument("arc", "Arc de Triomphe", Coordinates(48.874, 2.295))
    val otherOwner = first.copy(name = "Another Eiffel Tower")
    val shares =
        MutableStateFlow(
            listOf(
                ExploreFriendMonuments("alice", alice, listOf(first, second)),
                ExploreFriendMonuments("bob", PublicProfile("bob", "Bob"), listOf(otherOwner)),
            )
        )
    val opened = mutableListOf<Pair<String, String>>()
    showExplore(monument, friendPins = shares) { owner, guide -> opened += owner to guide }
    waitForPhoto(64, avatarColor, first.name)

    composeTestRule.runOnIdle {
      assertEquals(
          setOf(monument.name, first.name, second.name, otherOwner.name),
          markers.map { it.title }.toSet(),
      )
      assertEquals(4, markers.size)
      assertEquals(marker(first.name).icon, marker(second.name).icon)
      assertEquals(Offset(0.5f, 1f), marker(first.name).anchor)
      assertHasInitial(bitmap(otherOwner.name))
      assertTrue(clickListener.onMarkerClick(monumentMarker().marker))
    }
    composeTestRule.onNodeWithText(monument.name).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertTrue(clickListener.onMarkerClick(marker(first.name).marker))
      assertTrue(clickListener.onMarkerClick(marker(otherOwner.name).marker))
      assertEquals(listOf("alice" to first.guideId, "bob" to first.guideId), opened)
      shares.value =
          listOf(
              ExploreFriendMonuments("alice", alice, listOf(second.copy(coordinates = coordinates)))
          )
    }
    composeTestRule.onNodeWithText(monument.name).assertDoesNotExist()
    composeTestRule.runOnIdle {
      assertNotNull(monumentMarker().icon)
      assertEquals(setOf(monument.name, second.name), markers.map { it.title }.toSet())
      assertEquals(
          LatLng(coordinates.latitude, coordinates.longitude),
          marker(second.name).position,
      )
      assertEquals(avatarColor, bitmap(second.name).getPixel(32, 28))
      assertEquals(listOf(Size(48, 48)), requestedPhotoSizes.toList())
    }
  }

  @Test
  fun addingFourthFriendShowsOverflowAndTheirGuideRemainsSelectable() {
    val colors =
        listOf(0xffbb4422.toInt(), 0xff227799.toInt(), 0xff8866bb.toInt(), 0xff519143.toInt())
    val friends =
        listOf("Alice", "Bob", "Cora", "Dora").mapIndexed { index, name ->
          val photo = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
          photo.eraseColor(colors[index])
          val avatar =
              ByteArrayOutputStream()
                  .apply { photo.compress(Bitmap.CompressFormat.PNG, 100, this) }
                  .toByteArray()
          val shared = monument.copy(name = "Shared tower")
          ExploreFriendMonuments(
              name,
              PublicProfile(name, name, avatarBytes = avatar),
              listOf(shared),
          )
        }
    val shares = MutableStateFlow(friends.take(3))
    val opened = mutableListOf<Pair<String, String>>()
    showExplore(monument, friendPins = shares) { owner, guide -> opened += owner to guide }
    val trioTitle = "Shared tower (3 shared guides)"
    val overflowTitle = "Shared tower (4 shared guides)"
    val avatarCenters = listOf(24, 50, 76)
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.runOnIdle {
        val icon = markers.firstOrNull { it.title == trioTitle }?.icon
        val image = ExploreBitmapDescriptorShadow.bitmaps[icon]
        image != null && avatarCenters.map { image.getPixel(it, 28) } == colors.take(3)
      }
    }
    val trioWidth = composeTestRule.runOnIdle {
      assertEquals(setOf(monument.name, trioTitle), markers.map { it.title }.toSet())
      assertEquals(2, markers.size)
      assertEquals(Offset(0.5f, 1f), marker(trioTitle).anchor)
      shares.value = friends
      bitmap(trioTitle).width
    }
    composeTestRule.runOnIdle {
      assertEquals(2, markers.size)
      val overflow = bitmap(overflowTitle)
      assertTrue(overflow.width > trioWidth)
      assertNotEquals(0, overflow.getPixel(overflow.width - 4, 28))
      assertEquals(colors.take(3), avatarCenters.map { overflow.getPixel(it, 28) })
      assertEquals(List(3) { Size(48, 48) }, requestedPhotoSizes.toList())
      assertEquals(Color.White.toArgb(), overflow.getPixel(1, 28))
      darkTheme.value = true
    }
    composeTestRule.runOnIdle {
      assertEquals(darkColorScheme().outline.toArgb(), bitmap(overflowTitle).getPixel(1, 28))
      assertTrue(clickListener.onMarkerClick(marker(overflowTitle).marker))
      assertTrue(opened.isEmpty())
    }
    composeTestRule.onNodeWithText("Shared monuments").assertIsDisplayed()
    composeTestRule.onNodeWithText("Dora").performClick()
    composeTestRule.onNodeWithText("Shared monuments").assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(listOf("Dora" to monument.guideId), opened) }
  }

  @Test
  fun groupChooserSurvivesAnchorChangeAndClosesWhenSharesAreRemoved() {
    val first = monument.copy(name = " Shared Tower ")
    val second = first.copy(name = "shared tower", coordinates = Coordinates(48.85850, 2.29448))
    val alice = ExploreFriendMonuments("alice", null, listOf(first))
    val bob = ExploreFriendMonuments("bob", PublicProfile("bob", ""), listOf(second))
    val moved = second.copy(coordinates = Coordinates(48.85825, 2.29448))
    val movedBob = bob.copy(monuments = listOf(moved))
    val bridge = first.copy(name = "Old bridge", coordinates = Coordinates(48.85, 2.31))
    val otherGroup =
        listOf("cora", "dora").map { owner -> ExploreFriendMonuments(owner, null, listOf(bridge)) }
    val shares = MutableStateFlow(otherGroup + listOf(alice, bob))
    val opened = mutableListOf<Pair<String, String>>()
    showExplore(monument, friendPins = shares) { owner, guide -> opened += owner to guide }
    composeTestRule.runOnIdle {
      assertTrue(clickListener.onMarkerClick(marker(" Shared Tower  (2 shared guides)").marker))
    }
    composeTestRule.onNodeWithText("Friend 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Friend 2").assertIsDisplayed()
    composeTestRule.onNodeWithText("Done").performClick()
    composeTestRule.onNodeWithText("Shared monuments").assertDoesNotExist()
    composeTestRule.runOnIdle {
      assertTrue(opened.isEmpty())
      assertTrue(clickListener.onMarkerClick(marker(" Shared Tower  (2 shared guides)").marker))
      shares.value =
          otherGroup + listOf(movedBob, alice.copy(profile = PublicProfile("alice", "Alice")))
    }
    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(
          LatLng(moved.coordinates.latitude, moved.coordinates.longitude),
          marker("shared tower (2 shared guides)").position,
      )
      shares.value = otherGroup + movedBob
    }
    composeTestRule.onNodeWithText("Shared monuments").assertDoesNotExist()
    composeTestRule.runOnIdle {
      assertEquals(
          setOf(monument.name, second.name, "Old bridge (2 shared guides)"),
          markers.map { it.title }.toSet(),
      )
      assertTrue(clickListener.onMarkerClick(marker(second.name).marker))
      assertEquals(listOf("bob" to second.guideId), opened)
    }
  }

  @Test
  fun groupWithoutCustomMarkerStillOpensTheSelectedGuide() {
    ExploreBitmapDescriptorShadow.failDescriptorCreation = true
    val shared = monument.copy(name = "Shared tower")
    val friends =
        listOf("Alice", "Bob").map { owner ->
          ExploreFriendMonuments(owner, PublicProfile(owner, owner), listOf(shared))
        }
    val opened = mutableListOf<Pair<String, String>>()
    showExplore(monument, friendPins = MutableStateFlow(friends)) { owner, guide ->
      opened += owner to guide
    }
    composeTestRule.runOnIdle {
      val group = marker("Shared tower (2 shared guides)")
      assertNull(group.icon)
      assertTrue(clickListener.onMarkerClick(group.marker))
    }
    composeTestRule.onNodeWithText("Bob").performClick()
    composeTestRule.runOnIdle { assertEquals(listOf("Bob" to shared.guideId), opened) }
  }

  @Test
  fun missingAndUnreadableAvatarsDisplayAnInitialInBothThemes() {
    val imageLoadFailed = AtomicBoolean()
    val loader = mock<ImageLoader>()
    runBlocking {
      whenever(loader.execute(any())).thenAnswer { call ->
        ErrorResult(null, call.getArgument(0), IllegalArgumentException("Unrecognized image"))
            .also { imageLoadFailed.set(true) }
      }
    }
    Coil.setImageLoader(loader)
    val profiles =
        mapOf(
            "none" to PublicProfile("none", "Alice"),
            "empty" to PublicProfile("empty", "Alice", avatarBytes = byteArrayOf()),
            "corrupt" to PublicProfile("corrupt", "Alice", avatarBytes = byteArrayOf(1, 2, 3)),
            "unknown" to null,
        )
    val shares =
        MutableStateFlow(
            profiles.map { (owner, profile) ->
              ExploreFriendMonuments(
                  owner,
                  profile,
                  listOf(monument.copy(guideId = owner, name = owner)),
              )
            }
        )
    showExplore(monument, friendPins = shares)
    composeTestRule.waitUntil(5_000) { imageLoadFailed.get() }
    composeTestRule.runOnIdle {
      val initial = bitmap("none")
      assertHasInitial(initial)
      assertTrue(initial.sameAs(bitmap("empty")))
      assertTrue(initial.sameAs(bitmap("corrupt")))
      assertHasInitial(bitmap("unknown"))
      assertTrue(!initial.sameAs(bitmap("unknown")))
      darkTheme.value = true
    }
    composeTestRule.runOnIdle {
      val dark = bitmap("none")
      assertHasInitial(dark)
      assertEquals(darkColorScheme().outline.toArgb(), dark.getPixel(9, 28))
      assertTrue(dark.sameAs(bitmap("corrupt")))
    }
  }

  @Test
  fun avatarLoaderExceptionKeepsTheInitialAndPinNavigation() {
    val failure = IllegalStateException("Avatar loader unavailable")
    val loader = mock<ImageLoader>()
    runBlocking { whenever(loader.execute(any())).thenThrow(failure) }
    Coil.setImageLoader(loader)
    val profile = PublicProfile("alice", "Alice", avatarBytes = byteArrayOf(1))
    val shared = monument.copy(name = "Shared monument")
    val shares = MutableStateFlow(listOf(ExploreFriendMonuments("alice", profile, listOf(shared))))
    val opened = mutableListOf<Pair<String, String>>()
    showExplore(monument, friendPins = shares) { owner, guide -> opened += owner to guide }
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      ShadowLog.getLogsForTag("ExploreMonumentMarker").any { it.throwable === failure }
    }

    composeTestRule.runOnIdle {
      assertHasInitial(bitmap(shared.name))
      assertTrue(clickListener.onMarkerClick(marker(shared.name).marker))
      assertEquals(listOf("alice" to shared.guideId), opened)
    }
  }

  @Test
  fun sharedPinRetainsNavigationWhenItsDescriptorCannotBeCreated() {
    ExploreBitmapDescriptorShadow.failDescriptorCreation = true
    val shared = monument.copy(name = "Shared monument")
    val shares = MutableStateFlow(listOf(ExploreFriendMonuments("alice", null, listOf(shared))))
    val opened = mutableListOf<Pair<String, String>>()
    showExplore(monument, friendPins = shares) { owner, guide -> opened += owner to guide }

    composeTestRule.runOnIdle {
      assertNull(marker(shared.name).icon)
      assertTrue(clickListener.onMarkerClick(marker(shared.name).marker))
      assertEquals(listOf("alice" to shared.guideId), opened)
    }
  }

  @Test
  fun removingSharedPinCancelsItsPendingAvatarRequest() {
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    val loader =
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .components {
              add(
                  object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                      started.complete(Unit)
                      try {
                        awaitCancellation()
                      } finally {
                        cancelled.complete(Unit)
                      }
                    }
                  }
              )
            }
            .build()
    Coil.setImageLoader(loader)
    val shared = monument.copy(name = "Shared monument")
    val profile = PublicProfile("alice", "Alice", avatarBytes = byteArrayOf(1))
    val shares = MutableStateFlow(listOf(ExploreFriendMonuments("alice", profile, listOf(shared))))
    showExplore(monument, friendPins = shares)
    composeTestRule.waitUntil(5_000) { started.isCompleted }
    composeTestRule.runOnIdle {
      assertHasInitial(bitmap(shared.name))
      shares.value = emptyList()
    }
    composeTestRule.waitUntil(5_000) { cancelled.isCompleted }

    composeTestRule.runOnIdle { assertEquals(listOf(monument.name), markers.map { it.title }) }
  }

  private fun showExplore(
      monument: ExploreMonument,
      permissionState: MutableStateFlow<LocationPermissionState> =
          MutableStateFlow(grantedLocationPermission),
      friendPins: MutableStateFlow<List<ExploreFriendMonuments>> = MutableStateFlow(emptyList()),
      onSharedMonumentClick: (String, String) -> Unit = { _, _ -> },
  ) {
    val viewModel = mock<ExploreViewModel>()
    whenever(viewModel.friendMonuments).thenReturn(friendPins)
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
                  onSharedMonumentClick = onSharedMonumentClick,
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

  private fun marker(name: String): RenderedMarker = markers.single { it.title == name }

  private fun monumentMarker(): RenderedMarker = marker(monument.name)

  private fun bitmap(name: String): Bitmap =
      ExploreBitmapDescriptorShadow.bitmaps.getValue(marker(name).icon)

  private fun monumentBitmap(): Bitmap = bitmap(monument.name)

  private fun assertHasInitial(bitmap: Bitmap) {
    val background = bitmap.getPixel(14, 28)
    assertTrue((24..40).any { x -> (20..36).any { y -> bitmap.getPixel(x, y) != background } })
  }

  private fun waitForPhoto(width: Int, color: Int, name: String = monument.name) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.runOnIdle {
        val marker = markers.firstOrNull { it.title == name }
        val bitmap = ExploreBitmapDescriptorShadow.bitmaps[marker?.icon]
        bitmap != null &&
            bitmap.width == width &&
            bitmap.getPixel(width / 2, bitmap.height * 2 / 5) == color
      }
    }
  }

  private class RenderedMarker(
      var title: String,
      var position: LatLng,
      var icon: BitmapDescriptor?,
      val anchor: Offset,
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
