// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.ui.scan

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.github.roamswent.roam.data.FileCapturedImageManager
import com.github.roamswent.roam.data.GuideCaptureSender
import com.github.roamswent.roam.data.ScanLocationSource
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.FirebaseGeminiClient
import com.github.roamswent.roam.model.ai.GeminiImageScreen
import com.github.roamswent.roam.model.ai.GuideRepositoryImpl
import com.github.roamswent.roam.model.ai.ImagePreparer
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.guide.DefaultMonumentSharingProvider
import com.github.roamswent.roam.model.guide.MonumentSharingProvider
import com.github.roamswent.roam.model.guide.SavedGuideRepository
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.model.interests.SharedPreferencesUserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.leaderboard.FirestoreLeaderboardRepository
import com.github.roamswent.roam.model.leaderboard.LeaderboardEntry
import com.github.roamswent.roam.model.leaderboard.LeaderboardRepository
import com.github.roamswent.roam.model.leaderboard.MonumentKey
import com.github.roamswent.roam.model.leaderboard.VerifiedMonumentResult
import com.github.roamswent.roam.model.location.AndroidLocationProvider
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.ui.collection.CollectionSharingViewModel
import com.github.roamswent.roam.ui.loading.ContentResolverImageReader
import com.github.roamswent.roam.ui.loading.LoadingRoute
import com.github.roamswent.roam.ui.loading.LoadingViewModel
import com.github.roamswent.roam.ui.navigation.Routes
import java.util.Locale
import kotlinx.coroutines.Dispatchers

@Composable
fun ScanReviewRoute(
    navController: NavHostController,
    uri: Uri,
    captureSenderOverride: CaptureSender? = null,
    userInterestsRepositoryOverride: UserInterestsRepository? = null,
    savedGuideRepository: SavedGuideRepository,
    leaderboardRepositoryOverride: LeaderboardRepository? = null,
    isGalleryImage: Boolean = false,
    fromCollection: Boolean = false,
    friendRepository: FriendRepository? = null,
    publicProfileRepository: PublicProfileRepository? = null,
    monumentSharingProvider: MonumentSharingProvider? = null,
    onStartDiscovery: (() -> Unit) -> Unit = { proceed -> proceed() },
) {
  val context = LocalContext.current
  val capturedImageManager = remember(context) { FileCapturedImageManager(context, Dispatchers.IO) }
  val userInterestsRepository =
      remember(context.applicationContext, userInterestsRepositoryOverride) {
        userInterestsRepositoryOverride
            ?: SharedPreferencesUserInterestsRepository(context.applicationContext)
      }
  val captureSender: CaptureSender =
      remember(context, captureSenderOverride, isGalleryImage) {
        captureSenderOverride
            ?: run {
              val geminiClient = FirebaseGeminiClient(context)
              val guideRepository =
                  GuideRepositoryImpl(
                      imagePreparer = ImagePreparer(),
                      imageScreen = GeminiImageScreen(geminiClient),
                      geminiClient = geminiClient,
                      locationProvider = AndroidLocationProvider(context),
                  )
              GuideCaptureSender(
                  context,
                  guideRepository,
                  userInterestsRepository,
                  // A gallery photo is located only by its own metadata, never by the device.
                  locationSource = ScanLocationSource.forScan(isGalleryImage),
              )
            }
      }
  val viewModel: ScanReviewViewModel = viewModel {
    ScanReviewViewModel(capturedImageManager, captureSender, Dispatchers.Main.immediate)
  }
  val state by viewModel.state.collectAsState()
  val guideRepository =
      remember(captureSender, uri) {
        object : MonumentGuideRepository {
          override suspend fun guideFor(request: DiscoveryRequest): MonumentGuide =
              captureSender.send(uri).getOrThrow()
        }
      }
  val leaderboardRepository: LeaderboardRepository =
      remember(leaderboardRepositoryOverride) {
        leaderboardRepositoryOverride
            ?: run {
              // Created on the first verified visit, not with the screen, which often records none.
              val firestoreRepository by lazy { FirestoreLeaderboardRepository() }
              object : LeaderboardRepository {
                override suspend fun recordVerifiedMonument(
                    monument: MonumentKey
                ): VerifiedMonumentResult = firestoreRepository.recordVerifiedMonument(monument)

                override suspend fun getFriendsLeaderboard(): List<LeaderboardEntry> =
                    firestoreRepository.getFriendsLeaderboard()
              }
            }
      }
  val loadingViewModel: LoadingViewModel =
      viewModel(key = uri.toString()) {
        LoadingViewModel(
            guideRepository,
            ContentResolverImageReader(context.contentResolver)::read,
            uri,
            Locale.getDefault().toLanguageTag(),
            userInterestsRepository,
            savedGuideRepository,
            leaderboardRepository,
            sharingRepository =
                (monumentSharingProvider ?: DefaultMonumentSharingProvider()).repository(),
        )
      }
  val resolvedFriendRepository =
      remember(friendRepository) { friendRepository ?: FirestoreFriendRepository() }
  val resolvedProfileRepository =
      remember(publicProfileRepository) {
        publicProfileRepository ?: FirestorePublicProfileRepository()
      }
  val sharingViewModel: CollectionSharingViewModel =
      viewModel(key = "scan-sharing-$uri") {
        CollectionSharingViewModel(resolvedFriendRepository, resolvedProfileRepository)
      }
  val showLoading by loadingViewModel.showLoading.collectAsState()
  val showInterestsPrompt by loadingViewModel.showInterestsPrompt.collectAsState()
  val discoveredGuide by loadingViewModel.discoveredGuide.collectAsState()
  val saveError by loadingViewModel.saveError.collectAsState()
  val isFavorite by loadingViewModel.isFavorite.collectAsState()
  val favoriteError by loadingViewModel.favoriteError.collectAsState()
  val exploreOptIn by loadingViewModel.exploreOptIn.collectAsState()
  val exploreUnavailableReason by loadingViewModel.exploreUnavailableReason.collectAsState()
  val shareSettings by loadingViewModel.shareSettings.collectAsState()
  val savedGuideId by loadingViewModel.savedGuideId.collectAsState()
  val sharingError by loadingViewModel.sharingError.collectAsState()
  val isSharing by loadingViewModel.isSharing.collectAsState()
  val sharingFriends by sharingViewModel.uiState.collectAsState()
  val sharingRecipients =
      when (shareSettings.audience) {
        ShareAudience.ALL_FRIENDS,
        ShareAudience.ALL_FOLLOWERS -> sharingFriends.friends
        ShareAudience.SELECTED_FRIENDS ->
            sharingFriends.friends.filter { it.uid in shareSettings.sharedWithUids }
        ShareAudience.NONE -> emptyList()
      }

  val returnToGalleryOrigin = {
    navController.popBackStack(
        if (fromCollection) Routes.Collection else Routes.Scan,
        inclusive = false,
    )
    Unit
  }
  LaunchedEffect(uri) { viewModel.setUri(uri) }
  LaunchedEffect(viewModel) {
    viewModel.navigationEvents.collect { event ->
      when (event) {
        ScanReviewNavigationEvent.BackToCapture -> returnToGalleryOrigin()
      }
    }
  }

  BackHandler(enabled = !showLoading && !showInterestsPrompt) {
    if (discoveredGuide != null) {
      if (fromCollection) {
        returnToGalleryOrigin()
      } else {
        navController.popBackStack(Routes.Home, inclusive = false)
      }
    } else if (isGalleryImage) {
      returnToGalleryOrigin()
    } else {
      viewModel.onRetake()
    }
  }

  if (showLoading) {
    LoadingRoute(
        imageUri = uri,
        onCancel = { returnToGalleryOrigin() },
        onDiscoverySuccess = { guide, _ -> loadingViewModel.onDiscoverySuccess(guide) },
        loadingViewModel = loadingViewModel,
    )
  } else {
    val currentState = discoveredGuide?.let { ScanReviewUiState.Sent(uri, it) } ?: state
    currentState?.let {
      ScanReviewScreen(
          state = it,
          onRetake = if (isGalleryImage) returnToGalleryOrigin else viewModel::onRetake,
          onRetakeConfirm = viewModel::confirmRetake,
          onRetakeCancel = viewModel::cancelRetake,
          onRetryDelete = viewModel::onRetryDelete,
          onDismissDeleteError = viewModel::onDismissDeleteError,
          onSend = { onStartDiscovery(loadingViewModel::beginLoading) },
          onRetrySend = { onStartDiscovery(viewModel::onRetrySend) },
          onDismissSendError = viewModel::onDismissSendError,
          onHomeClick = {
            if (fromCollection) {
              returnToGalleryOrigin()
            } else {
              navController.popBackStack(Routes.Home, inclusive = false)
            }
          },
          showInterestsPrompt = showInterestsPrompt,
          isGalleryImage = isGalleryImage,
          onSaveInterests = loadingViewModel::onSaveInterests,
          onSkipInterests = loadingViewModel::onSkipInterests,
          // A guide that could not be saved is not in the collection, so it cannot be a favourite.
          isFavorite = isFavorite.takeIf { saveError == null },
          onFavoriteClick = loadingViewModel::toggleFavorite,
          showFavoriteError = favoriteError != null,
          onFavoriteErrorShown = loadingViewModel::clearFavoriteError,
          // A gallery photo is checked against its own EXIF location, never the device's.
          exploreOptIn = exploreOptIn,
          exploreUnavailableReason = exploreUnavailableReason,
          onShowInExploreChange = loadingViewModel::setShownInExplore,
          shareSettings = shareSettings,
          sharingRecipients = sharingRecipients,
          sharingFriends = sharingFriends.friends,
          savedGuideId = savedGuideId,
          isSharing = isSharing,
          isLoadingFriends = sharingFriends.isLoadingFriends,
          friendsError = sharingFriends.friendsError,
          onOpenSharing = sharingViewModel::loadFriends,
          onRetryLoadingFriends = sharingViewModel::retryLoadingFriends,
          onSetShareAudience = loadingViewModel::setShareAudience,
          showSharingError = sharingError != null,
          onSharingErrorShown = loadingViewModel::clearSharingError,
      )
    }
  }
}
