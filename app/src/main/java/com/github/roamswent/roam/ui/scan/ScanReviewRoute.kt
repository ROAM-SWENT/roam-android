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
import com.github.roamswent.roam.data.StubCaptureSender
import com.github.roamswent.roam.repository.CaptureSender
import com.github.roamswent.roam.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers

@Composable
fun ScanReviewRoute(navController: NavHostController, uri: Uri) {
  val context = LocalContext.current
  val capturedImageManager = remember(context) { FileCapturedImageManager(context, Dispatchers.IO) }
  val captureSender: CaptureSender = remember { StubCaptureSender() }
  val viewModel: ScanReviewViewModel = viewModel {
    ScanReviewViewModel(capturedImageManager, captureSender, Dispatchers.Main.immediate)
  }
  val state by viewModel.state.collectAsState()

  LaunchedEffect(uri) { viewModel.setUri(uri) }
  LaunchedEffect(viewModel) {
    viewModel.navigationEvents.collect { event ->
      when (event) {
        ScanReviewNavigationEvent.BackToCapture ->
            navController.popBackStack(Routes.Scan, inclusive = false)
      }
    }
  }
  BackHandler(enabled = true) {
    if (state !is ScanReviewUiState.Sending) {
      viewModel.onRetake()
    }
  }

  state?.let { currentState ->
    ScanReviewScreen(
        state = currentState,
        onRetake = viewModel::onRetake,
        onRetakeConfirm = viewModel::confirmRetake,
        onRetakeCancel = viewModel::cancelRetake,
        onRetryDelete = viewModel::onRetryDelete,
        onDismissDeleteError = viewModel::onDismissDeleteError,
        onSend = viewModel::onSend,
    )
  }
}
