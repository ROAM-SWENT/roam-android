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
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.FirebaseGeminiClient
import com.github.roamswent.roam.model.ai.GeminiImageScreen
import com.github.roamswent.roam.model.ai.GuideRepositoryImpl
import com.github.roamswent.roam.model.ai.ImagePreparer
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.interests.SharedPreferencesUserInterestsRepository
import com.github.roamswent.roam.model.location.AndroidLocationProvider
import com.github.roamswent.roam.repository.CaptureSender
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
) {
  val context = LocalContext.current
  val capturedImageManager = remember(context) { FileCapturedImageManager(context, Dispatchers.IO) }
  val captureSender: CaptureSender =
      remember(context, captureSenderOverride) {
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
                  SharedPreferencesUserInterestsRepository(context),
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
  val loadingViewModel: LoadingViewModel =
      viewModel(key = uri.toString()) {
        LoadingViewModel(
            guideRepository,
            ContentResolverImageReader(context.contentResolver)::read,
            uri,
            Locale.getDefault().toLanguageTag(),
        )
      }
  val showLoading by loadingViewModel.showLoading.collectAsState()
  val discoveredGuide by loadingViewModel.discoveredGuide.collectAsState()

  LaunchedEffect(uri) { viewModel.setUri(uri) }
  LaunchedEffect(viewModel) {
    viewModel.navigationEvents.collect { event ->
      when (event) {
        ScanReviewNavigationEvent.BackToCapture ->
            navController.popBackStack(Routes.Scan, inclusive = false)
      }
    }
  }

  BackHandler(enabled = !showLoading) {
    if (discoveredGuide != null) {
      navController.popBackStack(Routes.Home, inclusive = false)
    } else {
      viewModel.onRetake()
    }
  }

  if (showLoading) {
    LoadingRoute(
        imageUri = uri,
        onCancel = { navController.popBackStack(Routes.Scan, inclusive = false) },
        onDiscoverySuccess = { guide, _ -> loadingViewModel.onDiscoverySuccess(guide) },
        repository = guideRepository,
        loadingViewModel = loadingViewModel,
    )
  } else {
    val currentState = discoveredGuide?.let { ScanReviewUiState.Sent(uri, it) } ?: state
    currentState?.let {
      ScanReviewScreen(
          state = it,
          onRetake = viewModel::onRetake,
          onRetakeConfirm = viewModel::confirmRetake,
          onRetakeCancel = viewModel::cancelRetake,
          onRetryDelete = viewModel::onRetryDelete,
          onDismissDeleteError = viewModel::onDismissDeleteError,
          onSend = loadingViewModel::beginLoading,
          onRetrySend = viewModel::onRetrySend,
          onDismissSendError = viewModel::onDismissSendError,
          onHomeClick = { navController.popBackStack(Routes.Home, inclusive = false) },
      )
    }
  }
}
