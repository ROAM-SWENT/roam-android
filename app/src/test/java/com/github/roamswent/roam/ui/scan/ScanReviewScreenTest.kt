// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.guide.FakeMonumentSharingRepository
import com.github.roamswent.roam.model.guide.FakeSavedGuideRepository
import com.github.roamswent.roam.model.guide.MonumentSharingProvider
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestsState
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.collection.CollectionFriend
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ScanReviewScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController
  private lateinit var photoUri: Uri
  private var photoFile: File? = null
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @After
  fun deleteTemporaryPhoto() {
    photoFile?.delete()
  }

  @Test
  fun reviewingRendersPhotoAndEnabledActions() {
    setContent(ScanReviewUiState.Reviewing(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_review_photo).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.scan_review_photo_title))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsEnabled()
  }

  @Test
  fun confirmRetakeRendersDialogAndInvokesActions() {
    var confirmed = false
    var cancelled = false
    setContent(
        ScanReviewUiState.ConfirmRetake(uri),
        onRetakeConfirm = { confirmed = true },
        onRetakeCancel = { cancelled = true },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_dialog).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.scan_review_retake_title))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.scan_review_retake_message))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_confirm).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_cancel).performClick()

    assertEquals(true, confirmed)
    assertEquals(true, cancelled)
  }

  @Test
  fun sendingRendersNonDismissibleDialogAndDisablesActions() {
    setContent(ScanReviewUiState.Sending(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_sending_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()
    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    composeTestRule.onNodeWithTag(C.Tag.scan_sending_dialog).assertIsDisplayed()
  }

  @Test
  fun deletingRendersLockedReviewWithoutDialog() {
    setContent(ScanReviewUiState.Deleting(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_review_photo).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()
    composeTestRule.onAllNodesWithTag(C.Tag.scan_retake_dialog).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.scan_sending_dialog).assertCountEquals(0)
  }

  @Test
  fun interestsPromptDisplaysExternalizedTitleAndSubtitle() {
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Reviewing(uri),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            showInterestsPrompt = true,
        )
      }
    }

    composeTestRule
        .onNodeWithText(context.getString(R.string.scan_review_personalize_title))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.scan_review_personalize_subtitle))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("History").assertIsDisplayed()
    composeTestRule.onNodeWithText("Food & Drink").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "fr")
  fun interestsPrompt_underFrenchQualifier_rendersFrenchLabels_regressionProvesDecoupling() {
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Reviewing(uri),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            showInterestsPrompt = true,
        )
      }
    }

    composeTestRule.onNodeWithText("Histoire").assertIsDisplayed()
    composeTestRule.onNodeWithText("Gastronomie & Boissons").assertIsDisplayed()
  }

  @Test
  fun deleteFailedRendersDialogAndInvokesActions() {
    var retried = false
    var stayed = false
    setContent(
        ScanReviewUiState.DeleteFailed(uri),
        onRetryDelete = { retried = true },
        onDismissDeleteError = { stayed = true },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_delete_failed_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_delete_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_delete_stay).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()

    assertEquals(true, retried)
    assertEquals(true, stayed)
  }

  @Test
  fun reviewingActionClicksInvokeLambdas() {
    var retakeClicks = 0
    var sendClicks = 0
    setContent(
        ScanReviewUiState.Reviewing(uri),
        onRetake = { retakeClicks++ },
        onSend = { sendClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()

    assertEquals(1, retakeClicks)
    assertEquals(1, sendClicks)
  }

  @Test
  fun reviewRouteRendersScreenAndBackOpensRetakeDialog() {
    composeTestRule.activity.setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.Home) {
          composable(Routes.Home) {
            LaunchedEffect(Unit) {
              navController.navigate("${Routes.ScanReview}?uri=${Uri.encode(uri.toString())}")
            }
          }
          composable("${Routes.ScanReview}?uri={uri}") { entry ->
            ScanReviewRoute(
                navController = navController,
                uri = Uri.parse(checkNotNull(entry.arguments?.getString("uri"))),
                savedGuideRepository = FakeSavedGuideRepository(),
            )
          }
        }
      }
    }

    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(C.Tag.scan_review_screen).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.scan_review_screen).assertIsDisplayed()
    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_dialog).assertIsDisplayed()
  }

  @Test
  fun galleryReviewRetakeReturnsToScanWithoutDeletingImage() {
    showReviewRoute(
        captureSender =
            object : CaptureSender {
              override suspend fun send(uri: Uri): Result<MonumentGuide> =
                  Result.failure(UnsupportedOperationException())
            },
        isGalleryImage = true,
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
  }

  @Test
  fun galleryReviewBackReturnsToCollectionWhenOpenedFromCollection() {
    showReviewRoute(
        captureSender =
            object : CaptureSender {
              override suspend fun send(uri: Uri): Result<MonumentGuide> =
                  Result.failure(UnsupportedOperationException())
            },
        isGalleryImage = true,
        fromCollection = true,
    )

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    waitAndAssertDisplayed(C.Tag.collection_screen)
  }

  @Test
  fun reviewRouteCancelDuringLoadingReturnsToCaptureAndCancelsRequest() {
    val requestStarted = AtomicBoolean(false)
    val requestCancelled = AtomicBoolean(false)
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            requestStarted.set(true)
            return try {
              awaitCancellation()
            } finally {
              requestCancelled.set(true)
            }
          }
        }
    showReviewRoute(sender)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.loading_screen_progress)
    composeTestRule.waitUntil(timeoutMillis = 60_000) {
      shadowOf(Looper.getMainLooper()).idle()
      requestStarted.get()
    }
    composeTestRule.onNodeWithTag(C.Tag.loading_screen_cancel_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_camera_screen)
    waitForDestination(Routes.Scan)
    composeTestRule.waitUntil(timeoutMillis = 60_000) {
      shadowOf(Looper.getMainLooper()).idle()
      requestCancelled.get()
    }
  }

  @Test
  fun firstSendShowsInterestsPromptWithoutStartingGuideGeneration() {
    val sendCount = AtomicInteger()
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            sendCount.incrementAndGet()
            return Result.success(guide)
          }
        }
    showReviewRoute(sender, FakeUserInterestsRepository(hasBeenAsked = false))

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()

    waitAndAssertDisplayed(C.Tag.scan_interests_prompt)
    composeTestRule.onAllNodesWithTag(C.Tag.loading_screen_progress).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.scan_interests_chip).assertCountEquals(8)
    assertEquals(0, sendCount.get())
  }

  @Test
  fun selectedInterestsAreRestoredAfterSavedStateRestore() {
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Reviewing(uri),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            showInterestsPrompt = true,
        )
      }
    }

    composeTestRule.onNodeWithText("Architecture").performClick()
    composeTestRule.onNodeWithText("History").performClick()
    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithText("Architecture").assertIsSelected()
    composeTestRule.onNodeWithText("History").assertIsSelected()
    composeTestRule.onNodeWithText("Nature").assertIsNotSelected()
  }

  @Test
  fun savingInterestsContinuesGuideGeneration() {
    val interestsRepository = FakeUserInterestsRepository(hasBeenAsked = false)
    val sendCount = AtomicInteger()
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            sendCount.incrementAndGet()
            return Result.success(guide)
          }
        }
    showReviewRoute(sender, interestsRepository)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_interests_prompt)
    composeTestRule.onNodeWithTag(C.Tag.scan_interests_save).assertIsNotEnabled()
    composeTestRule
        .onNodeWithText("Architecture")
        .assertIsNotSelected()
        .performClick()
        .assertIsSelected()
    composeTestRule
        .onNodeWithText("History")
        .assertIsNotSelected()
        .performClick()
        .assertIsSelected()
    composeTestRule.onNodeWithText("Nature").assertIsNotSelected()
    composeTestRule.onNodeWithTag(C.Tag.scan_interests_save).performClick()

    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    assertEquals(
        UserInterestsState(hasBeenAsked = true, interests = listOf("Architecture", "History")),
        interestsRepository.getUserInterests(),
    )
    assertEquals(1, sendCount.get())
  }

  @Test
  fun skippingInterestsContinuesGuideGeneration() {
    val interestsRepository = FakeUserInterestsRepository(hasBeenAsked = false)
    val sendCount = AtomicInteger()
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            sendCount.incrementAndGet()
            return Result.success(guide)
          }
        }
    showReviewRoute(sender, interestsRepository)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_interests_prompt)
    composeTestRule.onNodeWithTag(C.Tag.scan_interests_skip).performClick()

    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    assertEquals(
        UserInterestsState(hasBeenAsked = true, interests = emptyList()),
        interestsRepository.getUserInterests(),
    )
    assertEquals(1, sendCount.get())
  }

  @Test
  fun laterSendDoesNotShowInterestsPromptAgain() {
    val interestsRepository = FakeUserInterestsRepository(hasBeenAsked = false)
    val sendCount = AtomicInteger()
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            sendCount.incrementAndGet()
            return Result.success(guide)
          }
        }
    showReviewRoute(sender, interestsRepository)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.scan_interests_prompt)
    composeTestRule.onNodeWithTag(C.Tag.scan_interests_skip).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()
    waitAndAssertDisplayed(C.Tag.home_screen_container)
    composeTestRule.runOnIdle {
      navController.navigate(Routes.Scan)
      navController.navigate("${Routes.ScanReview}?uri=${Uri.encode(photoUri.toString())}")
    }

    waitAndAssertDisplayed(C.Tag.scan_review_screen)
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)

    composeTestRule.onAllNodesWithTag(C.Tag.scan_interests_prompt).assertCountEquals(0)
    assertEquals(2, sendCount.get())
  }

  @Test
  fun reviewRouteSuccessShowsGuideAndGuideButtonReturnsHome() {
    val sentUri = AtomicReference<Uri>()
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> {
            sentUri.set(uri)
            return Result.success(guide)
          }
        }
    showReviewRoute(sender)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()

    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithText(guide.monumentName).assertIsDisplayed()
    assertEquals(photoUri, sentUri.get())

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForDestination(Routes.Home)
  }

  @Test
  fun reviewRouteSharingAllFriendsUpdatesRecipients() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(guide.copy(photo = ImagePayload(byteArrayOf(1), "image/jpeg")))
        }
    val sharing = FakeMonumentSharingRepository()
    val friends = mockk<FriendRepository>()
    val profiles = mockk<PublicProfileRepository>()
    coEvery { friends.getFriends() } returns listOf(Friendship("friendship", "alice"))
    coEvery { profiles.getProfile("alice") } returns PublicProfile("alice", "Alice")
    showReviewRoute(
        captureSender = sender,
        savedGuideRepository = FakeSavedGuideRepository(),
        friendRepository = friends,
        publicProfileRepository = profiles,
        monumentSharingProvider = { sharing },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_share_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_audience_all_friends).performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()

    composeTestRule.waitUntil {
      sharing.shareSettings.value.values.any { it.audience == ShareAudience.ALL_FRIENDS }
    }
    composeTestRule.onNodeWithText("Shared with Alice").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun reviewRouteSharingSelectedFriendUpdatesRecipients() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(guide.copy(photo = ImagePayload(byteArrayOf(1), "image/jpeg")))
        }
    val sharing = FakeMonumentSharingRepository()
    val friends = mockk<FriendRepository>()
    val profiles = mockk<PublicProfileRepository>()
    coEvery { friends.getFriends() } returns listOf(Friendship("friendship", "alice"))
    coEvery { profiles.getProfile("alice") } returns PublicProfile("alice", "Alice")
    showReviewRoute(
        captureSender = sender,
        savedGuideRepository = FakeSavedGuideRepository(),
        friendRepository = friends,
        publicProfileRepository = profiles,
        monumentSharingProvider = { sharing },
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_share_button).performClick()
    composeTestRule.onNodeWithText("Alice").performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()

    composeTestRule.waitUntil {
      sharing.shareSettings.value.values.any {
        it.audience == ShareAudience.ALL_FRIENDS && it.sharedWithUids.isEmpty()
      }
    }
    composeTestRule.onNodeWithText("Shared with Alice").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun reviewRouteSuccessSavesDiscoveryToCollection() {
    // The pipeline returns the downscaled photo it sent to the AI along with the guide.
    val photo = ImagePayload(byteArrayOf(4, 5, 6), "image/jpeg")
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(guide.copy(photo = photo))
        }
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(sender, savedGuideRepository = savedGuides)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()

    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)
    val saved = savedGuides.guides.value.single()
    assertEquals(guide.monumentName, saved.monumentName)
    assertArrayEquals(photo.bytes, saved.photoBytes)
  }

  @Test
  fun reviewRouteFavouriteButtonAddsTheSavedGuideToFavourites() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(guide.copy(photo = ImagePayload(byteArrayOf(1), "image/jpeg")))
        }
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(sender, savedGuideRepository = savedGuides)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_favorite_button)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_favorite_button).performClick()

    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      savedGuides.guides.value.singleOrNull()?.isFavorite == true
    }
  }

  @Test
  fun reviewRouteHidesFavouriteButtonWhenTheGuideCouldNotBeSaved() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(guide.copy(photo = ImagePayload(byteArrayOf(1), "image/jpeg")))
        }
    val savedGuides = FakeSavedGuideRepository().apply { addError = IllegalStateException() }
    showReviewRoute(sender, savedGuideRepository = savedGuides)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)

    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_favorite_button).assertCountEquals(0)
  }

  @Test
  fun sentPassesFavouriteStateToMonumentDetail() {
    var favouriteClicks = 0
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Sent(uri, guide),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            isFavorite = false,
            onFavoriteClick = { favouriteClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_favorite_button).performClick()

    assertEquals(1, favouriteClicks)
  }

  @Test
  fun sentGuideOpensSharingDialogWhenItWasSaved() {
    var sharingOpened = false
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Sent(uri, guide),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            savedGuideId = "saved-guide",
            onOpenSharing = { sharingOpened = true },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_share_button).performClick()

    assertEquals(true, sharingOpened)
    composeTestRule.onNodeWithTag(C.Tag.collection_share_dialog).assertIsDisplayed()
  }

  @Test
  fun sentGuideWithoutSavedIdDoesNotRenderSharingDialog() {
    var sharingOpened = false
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Sent(uri, guide),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            onOpenSharing = { sharingOpened = true },
        )
      }
    }

    composeTestRule.onAllNodesWithTag(C.Tag.monument_detail_share_button).assertCountEquals(0)
    assertEquals(false, sharingOpened)
    composeTestRule.onAllNodesWithTag(C.Tag.collection_share_dialog).assertCountEquals(0)
  }

  @Test
  fun sentGuideSharingConfirmationReportsAudienceAndRecipients() {
    var selectedAudience: ShareAudience? = null
    var selectedFriends = emptySet<String>()
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = ScanReviewUiState.Sent(uri, guide),
            onRetake = {},
            onRetakeConfirm = {},
            onRetakeCancel = {},
            onSend = {},
            savedGuideId = "saved-guide",
            sharingFriends = listOf(CollectionFriend("alice", "Alice")),
            onOpenSharing = {},
            onSetShareAudience = { audience, friends ->
              selectedAudience = audience
              selectedFriends = friends
            },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_share_button).performClick()
    composeTestRule.onNodeWithText("Alice").performClick()
    composeTestRule.onNodeWithTag(C.Tag.collection_share_confirm).performClick()

    assertEquals(ShareAudience.ALL_FRIENDS, selectedAudience)
    assertEquals(emptySet<String>(), selectedFriends)
  }

  @Test
  fun routeSharingDependenciesCanChangeAcrossRecomposition() {
    val friendRepository = mutableStateOf<FriendRepository?>(null)
    val profileRepository = mutableStateOf<PublicProfileRepository?>(null)
    val sharingProvider = mutableStateOf<MonumentSharingProvider?>(null)
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> = Result.success(guide)
        }

    composeTestRule.activity.setContent {
      SampleAppTheme {
        val controller = rememberNavController()
        NavHost(navController = controller, startDestination = Routes.Home) {
          composable(Routes.Home) {
            LaunchedEffect(Unit) {
              controller.navigate("${Routes.ScanReview}?uri=${Uri.encode(uri.toString())}")
            }
          }
          composable("${Routes.ScanReview}?uri={uri}") { entry ->
            ScanReviewRoute(
                navController = controller,
                uri = Uri.parse(checkNotNull(entry.arguments?.getString("uri"))),
                captureSenderOverride = sender,
                savedGuideRepository = FakeSavedGuideRepository(),
                friendRepository = friendRepository.value,
                publicProfileRepository = profileRepository.value,
                monumentSharingProvider = sharingProvider.value,
            )
          }
        }
      }
    }

    composeTestRule.waitUntil {
      composeTestRule.onAllNodesWithTag(C.Tag.scan_review_screen).fetchSemanticsNodes().isNotEmpty()
    }
    friendRepository.value = mockk()
    profileRepository.value = mockk()
    sharingProvider.value = { FakeMonumentSharingRepository() }
    composeTestRule.waitForIdle()
    friendRepository.value = null
    profileRepository.value = null
    sharingProvider.value = null
    composeTestRule.waitForIdle()
  }

  /** A gallery photo whose guide carries [photoLocation] and [monumentLocation]. */
  private fun galleryGuideSender(
      photoLocation: Coordinates?,
      monumentLocation: Coordinates?,
  ): CaptureSender =
      object : CaptureSender {
        override suspend fun send(uri: Uri): Result<MonumentGuide> =
            Result.success(
                guide.copy(
                    location = photoLocation,
                    monumentLocation = monumentLocation,
                    photo = ImagePayload(byteArrayOf(4, 5, 6), "image/jpeg"),
                )
            )
      }

  @Test
  fun reviewRouteShowInExplorePinsTheGalleryPhotoLocation() {
    // Where the photo was taken per its EXIF metadata, a few metres from the tower.
    val photoLocation = Coordinates(48.8583, 2.2944)
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(
        galleryGuideSender(photoLocation, monumentLocation = Coordinates(48.8584, 2.2945)),
        isGalleryImage = true,
        savedGuideRepository = savedGuides,
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_show_in_explore)
    composeTestRule
        .onNodeWithText(
            context.getString(R.string.monument_detail_show_in_explore_supporting_photo),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
    val savedId = savedGuides.guides.value.single().id
    assertEquals(null, savedGuides.guides.value.single().location)

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_show_in_explore)
        .assertIsOff()
        .performClick()
    composeTestRule.waitUntil(5_000) { savedGuides.guides.value.single().location != null }

    assertEquals(listOf(savedId to photoLocation), savedGuides.locationUpdates)
    assertEquals(photoLocation, savedGuides.guides.value.single().location)
  }

  @Test
  fun reviewRouteExplainsThatAGalleryPhotoWithoutLocationIsNotOnExplore() {
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(
        galleryGuideSender(photoLocation = null, monumentLocation = Coordinates(48.8584, 2.2945)),
        isGalleryImage = true,
        savedGuideRepository = savedGuides,
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_explore_unavailable)

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_show_in_explore).assertDoesNotExist()
    composeTestRule
        .onNodeWithText(
            context.getString(R.string.monument_detail_explore_unavailable_no_photo_location),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
    // The guide is still saved to the collection, just not put on Explore.
    assertEquals(null, savedGuides.guides.value.single().location)
    assertEquals(emptyList<Any>(), savedGuides.locationUpdates)
  }

  @Test
  fun reviewRouteExplainsThatAGalleryPhotoTakenFarAwayIsNotOnExplore() {
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(
        // Taken at the Louvre, about 3 km from the Eiffel Tower.
        galleryGuideSender(
            photoLocation = Coordinates(48.8606, 2.3376),
            monumentLocation = Coordinates(48.8584, 2.2945),
        ),
        isGalleryImage = true,
        savedGuideRepository = savedGuides,
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_share_unavailable)

    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_explore_unavailable)
        .assertCountEquals(0)
    composeTestRule
        .onNodeWithText(
            context.getString(R.string.monument_detail_share_unavailable_message, "Eiffel Tower"),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
    assertEquals(null, savedGuides.guides.value.single().location)
    assertEquals(emptyList<Any>(), savedGuides.locationUpdates)
  }

  @Test
  fun reviewRouteExplainsWhyAScanFarFromTheMonumentIsNotOnExplore() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(
                  guide.copy(
                      // Scanned at the Louvre, about 3 km from the Eiffel Tower.
                      location = Coordinates(48.8606, 2.3376),
                      monumentLocation = Coordinates(48.8584, 2.2945),
                      photo = ImagePayload(byteArrayOf(4, 5, 6), "image/jpeg"),
                  )
              )
        }
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(sender, savedGuideRepository = savedGuides)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_share_unavailable)

    composeTestRule
        .onAllNodesWithTag(C.Tag.monument_detail_explore_unavailable)
        .assertCountEquals(0)
    // The guide is still saved to the collection, just not put on Explore.
    assertEquals(null, savedGuides.guides.value.single().location)
    assertEquals(emptyList<Any>(), savedGuides.locationUpdates)
  }

  @Test
  fun reviewRouteShowInExploreSetsScanLocationOnTheSavedGuide() {
    val scanLocation = Coordinates(48.8584, 2.2945)
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> =
              Result.success(
                  guide.copy(
                      location = scanLocation,
                      // The tower's own position per the AI, a few metres from the scan.
                      monumentLocation = Coordinates(48.8583, 2.2944),
                      photo = ImagePayload(byteArrayOf(4, 5, 6), "image/jpeg"),
                  )
              )
        }
    val savedGuides = FakeSavedGuideRepository()
    showReviewRoute(sender, savedGuideRepository = savedGuides)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_show_in_explore)
    val savedId = savedGuides.guides.value.single().id
    assertEquals(null, savedGuides.guides.value.single().location)

    composeTestRule
        .onNodeWithTag(C.Tag.monument_detail_show_in_explore)
        .assertIsOff()
        .performClick()
    composeTestRule.waitUntil(5_000) { savedGuides.guides.value.single().location != null }

    assertEquals(listOf(savedId to scanLocation), savedGuides.locationUpdates)
    assertEquals(scanLocation, savedGuides.guides.value.single().location)
  }

  @Test
  fun reviewRouteSystemBackFromGuideReturnsHome() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> = Result.success(guide)
        }
    showReviewRoute(sender)

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    waitAndAssertDisplayed(C.Tag.home_screen_container)
    waitForDestination(Routes.Home)
  }

  @Test
  fun reviewingShowsPromptPillWithEnabledActions() {
    setContent(ScanReviewUiState.Reviewing(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun sendingShowsPromptPill() {
    setContent(ScanReviewUiState.Sending(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun confirmRetakeShowsPromptPill() {
    setContent(ScanReviewUiState.ConfirmRetake(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun deletingShowsPromptPill() {
    setContent(ScanReviewUiState.Deleting(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  @Test
  fun deleteFailedShowsPromptPill() {
    setContent(ScanReviewUiState.DeleteFailed(uri))

    composeTestRule.onNodeWithTag(C.Tag.scan_review_prompt_pill).assertIsDisplayed()
  }

  private val uri = Uri.parse("content://example/capture.jpg")
  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.95,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "Symbol of Paris.",
          language = "en",
      )

  private fun showReviewRoute(
      captureSender: CaptureSender,
      userInterestsRepository: UserInterestsRepository = FakeUserInterestsRepository(),
      isGalleryImage: Boolean = false,
      fromCollection: Boolean = false,
      savedGuideRepository: SavedGuideRepository = FakeSavedGuideRepository(),
      friendRepository: FriendRepository? = null,
      publicProfileRepository: PublicProfileRepository? = null,
      monumentSharingProvider: MonumentSharingProvider? = null,
  ) {
    val photo =
        File.createTempFile(
            "scan-review-route",
            ".jpg",
            ApplicationProvider.getApplicationContext<Context>().cacheDir,
        )
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }

    bitmap.recycle()
    photoFile = photo
    photoUri = Uri.fromFile(photo)
    composeTestRule.activity.setContent {
      SampleAppTheme {
        val controller = rememberNavController()
        SideEffect { navController = controller }
        NavHost(navController = controller, startDestination = Routes.Home) {
          composable(Routes.Home) {
            Box(Modifier.fillMaxSize().testTag(C.Tag.home_screen_container))
          }
          composable(Routes.Scan) { Box(Modifier.fillMaxSize().testTag(C.Tag.scan_camera_screen)) }
          composable(Routes.Collection) {
            Box(Modifier.fillMaxSize().testTag(C.Tag.collection_screen))
          }
          composable(
              "${Routes.ScanReview}?uri={uri}&gallery={gallery}&fromCollection={fromCollection}",
              arguments =
                  listOf(
                      navArgument("gallery") {
                        type = NavType.BoolType
                        defaultValue = false
                      },
                      navArgument("fromCollection") {
                        type = NavType.BoolType
                        defaultValue = false
                      },
                  ),
          ) { entry ->
            ScanReviewRoute(
                navController = controller,
                uri = Uri.parse(checkNotNull(entry.arguments?.getString("uri"))),
                captureSenderOverride = captureSender,
                userInterestsRepositoryOverride = userInterestsRepository,
                savedGuideRepository = savedGuideRepository,
                friendRepository = friendRepository,
                publicProfileRepository = publicProfileRepository,
                monumentSharingProvider = monumentSharingProvider,
                isGalleryImage = entry.arguments?.getBoolean("gallery") ?: false,
                fromCollection = entry.arguments?.getBoolean("fromCollection") ?: false,
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      navController.navigate(if (fromCollection) Routes.Collection else Routes.Scan)
      navController.navigate(
          "${Routes.ScanReview}?uri=${Uri.encode(photoUri.toString())}&gallery=$isGalleryImage&fromCollection=$fromCollection",
      )
    }
    waitAndAssertDisplayed(C.Tag.scan_review_screen)
  }

  private class FakeUserInterestsRepository(
      private var hasBeenAsked: Boolean = true,
      private var interests: List<String> = emptyList(),
  ) : UserInterestsRepository {
    override fun getUserInterests() = UserInterestsState(hasBeenAsked, interests)

    override fun updateInterests(interests: List<String>) {
      this.interests = interests
    }

    override fun saveInterests(interests: List<String>) {
      hasBeenAsked = true
      this.interests = interests
    }

    override fun skipInterests() {
      hasBeenAsked = true
      interests = emptyList()
    }
  }

  private fun waitForDestination(route: String) {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      navController.currentDestination?.route == route
    }
  }

  private fun waitAndAssertDisplayed(tag: String) {
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
  }

  @Test
  fun sentRendersMonumentDetailScreen() {
    var homeClicked = false
    setContent(ScanReviewUiState.Sent(uri, guide), onHomeClick = { homeClicked = true })

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_screen_container).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.scan_review_screen).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    assertEquals(true, homeClicked)
  }

  @Test
  fun sendFailedRendersDialogAndInvokesActions() {
    var retried = false
    var dismissed = false
    setContent(
        ScanReviewUiState.SendFailed(uri, R.string.scan_review_error_not_identified),
        onRetrySend = { retried = true },
        onDismissSendError = { dismissed = true },
    )

    composeTestRule.onAllNodesWithTag(C.Tag.loading_screen_progress).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.loading_screen_title).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.scan_retake_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_retry).performClick()
    composeTestRule.onNodeWithTag(C.Tag.scan_send_failed_dismiss).performClick()

    assertEquals(true, retried)
    assertEquals(true, dismissed)
  }

  @Test
  fun reviewRouteSystemBackFromGuideReturnsCollectionWhenOpenedFromCollection() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> = Result.success(guide)
        }

    showReviewRoute(
        captureSender = sender,
        isGalleryImage = true,
        fromCollection = true,
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)

    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

    waitAndAssertDisplayed(C.Tag.collection_screen)
    waitForDestination(Routes.Collection)
  }

  @Test
  fun reviewRouteGuideBackButtonReturnsCollectionWhenOpenedFromCollection() {
    val sender =
        object : CaptureSender {
          override suspend fun send(uri: Uri): Result<MonumentGuide> = Result.success(guide)
        }

    showReviewRoute(
        captureSender = sender,
        isGalleryImage = true,
        fromCollection = true,
    )

    composeTestRule.onNodeWithTag(C.Tag.scan_validate_button).performClick()
    waitAndAssertDisplayed(C.Tag.monument_detail_screen_container)

    composeTestRule.onNodeWithTag(C.Tag.monument_detail_back_button).performClick()

    waitAndAssertDisplayed(C.Tag.collection_screen)
    waitForDestination(Routes.Collection)
  }

  private fun setContent(
      state: ScanReviewUiState,
      onRetake: () -> Unit = {},
      onRetakeConfirm: () -> Unit = {},
      onRetakeCancel: () -> Unit = {},
      onRetryDelete: () -> Unit = {},
      onDismissDeleteError: () -> Unit = {},
      onSend: () -> Unit = {},
      onRetrySend: () -> Unit = {},
      onDismissSendError: () -> Unit = {},
      onHomeClick: () -> Unit = {},
  ) {
    composeTestRule.activity.setContent {
      SampleAppTheme {
        ScanReviewScreen(
            state = state,
            onRetake = onRetake,
            onRetakeConfirm = onRetakeConfirm,
            onRetakeCancel = onRetakeCancel,
            onRetryDelete = onRetryDelete,
            onDismissDeleteError = onDismissDeleteError,
            onSend = onSend,
            onRetrySend = onRetrySend,
            onDismissSendError = onDismissSendError,
            onHomeClick = onHomeClick,
        )
      }
    }
  }
}
