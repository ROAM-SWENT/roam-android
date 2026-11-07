// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.interests.UserInterestCategories
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.monumentDetail.MonumentDetailScreen
import com.github.roamswent.roam.ui.scan.components.ExpressiveFloatingButton
import com.github.roamswent.roam.ui.theme.ShutterWhite

@Composable
fun ScanReviewScreen(
    state: ScanReviewUiState,
    onRetake: () -> Unit,
    onRetakeConfirm: () -> Unit,
    onRetakeCancel: () -> Unit,
    onRetryDelete: () -> Unit = {},
    onDismissDeleteError: () -> Unit = {},
    onSend: () -> Unit,
    onRetrySend: () -> Unit = {},
    onDismissSendError: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    showInterestsPrompt: Boolean = false,
    onSaveInterests: (List<String>) -> Unit = {},
    onSkipInterests: () -> Unit = {},
    isGalleryImage: Boolean = false,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
) {
  if (state is ScanReviewUiState.Sent) {
    MonumentDetailScreen(
        image = rememberAsyncImagePainter(state.uri),
        guide = state.guide,
        onBackClick = onHomeClick,
        isDarkTheme = isDarkTheme,
    )
    return
  }

  val context = LocalContext.current
  val uri =
      when (state) {
        is ScanReviewUiState.Reviewing -> state.uri
        is ScanReviewUiState.ConfirmRetake -> state.uri
        is ScanReviewUiState.Deleting -> state.uri
        is ScanReviewUiState.DeleteFailed -> state.uri
        is ScanReviewUiState.Sending -> state.uri
        is ScanReviewUiState.SendFailed -> state.uri
        is ScanReviewUiState.Sent -> state.uri
      }
  val locked =
      state is ScanReviewUiState.Deleting ||
          state is ScanReviewUiState.DeleteFailed ||
          state is ScanReviewUiState.Sending ||
          state is ScanReviewUiState.SendFailed ||
          showInterestsPrompt
  Box(modifier.fillMaxSize().semantics { testTag = C.Tag.scan_review_screen }) {
    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(uri)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.scan_review_photo },
    )
    Box(
        modifier =
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(top = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
      Surface(
          shape = RoundedCornerShape(percent = 50),
          color = Color.Black.copy(alpha = 0.45f),
          modifier = Modifier.semantics { testTag = C.Tag.scan_review_prompt_pill },
      ) {
        Text(
            stringResource(R.string.scan_review_photo_title),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
    }
    Row(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 56.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      ExpressiveFloatingButton(
          onClick = onRetake,
          enabled = !locked,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.size(80.dp).semantics { testTag = C.Tag.scan_retake_button },
      ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription =
                if (isGalleryImage) stringResource(R.string.scan_review_choose_another_photo)
                else stringResource(R.string.scan_review_retake),
            tint = ShutterWhite,
        )
      }
      ExpressiveFloatingButton(
          onClick = onSend,
          enabled = !locked,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.size(80.dp).semantics { testTag = C.Tag.scan_validate_button },
      ) {
        Icon(
            Icons.Default.Check,
            contentDescription = stringResource(R.string.scan_review_validate),
            tint = ShutterWhite,
        )
      }
    }
  }
  if (state is ScanReviewUiState.ConfirmRetake) {
    RetakeWarningDialog(onConfirm = onRetakeConfirm, onCancel = onRetakeCancel)
  }
  if (state is ScanReviewUiState.DeleteFailed) {
    DeleteFailedDialog(onRetry = onRetryDelete, onStay = onDismissDeleteError)
  }
  if (state is ScanReviewUiState.Sending) {
    SendingDialog()
  }
  if (state is ScanReviewUiState.SendFailed) {
    SendFailedDialog(message = state.message, onRetry = onRetrySend, onDismiss = onDismissSendError)
  }
  if (showInterestsPrompt) {
    BackHandler {}
    InterestsPromptDialog(onSave = onSaveInterests, onSkip = onSkipInterests)
  }
}

@Composable
private fun InterestsPromptDialog(onSave: (List<String>) -> Unit, onSkip: () -> Unit) {
  val selectedInterestsSaver =
      listSaver<Set<String>, String>(
          save = { it.toList() },
          restore = { it.toSet() },
      )
  var selectedInterests by
      rememberSaveable(stateSaver = selectedInterestsSaver) { mutableStateOf(emptySet<String>()) }

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = 0.32f))
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
              ) {}
              .semantics { testTag = C.Tag.scan_interests_prompt },
      contentAlignment = Alignment.Center,
  ) {
    Surface(
        modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 24.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
      Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            stringResource(R.string.scan_review_personalize_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.scan_review_personalize_subtitle))
        Spacer(Modifier.size(12.dp))
        InterestCategoryChips(selectedInterests) { selectedInterests = it }
        Spacer(Modifier.size(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(
              onClick = onSkip,
              modifier = Modifier.semantics { testTag = C.Tag.scan_interests_skip },
          ) {
            Text(stringResource(R.string.scan_review_skip))
          }
          TextButton(
              onClick = { onSave(UserInterestCategories.all.filter(selectedInterests::contains)) },
              enabled = selectedInterests.isNotEmpty(),
              modifier = Modifier.semantics { testTag = C.Tag.scan_interests_save },
          ) {
            Text(stringResource(R.string.scan_review_save))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestCategoryChips(
    selectedInterests: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    UserInterestCategories.all.forEach { category ->
      FilterChip(
          selected = category in selectedInterests,
          onClick = {
            onSelectionChange(
                if (category in selectedInterests) selectedInterests - category
                else selectedInterests + category
            )
          },
          label = { Text(category) },
          modifier = Modifier.semantics { testTag = C.Tag.scan_interests_chip },
      )
    }
  }
}

@Composable
private fun DeleteFailedDialog(onRetry: () -> Unit, onStay: () -> Unit) {
  AlertDialog(
      onDismissRequest = onStay,
      title = { Text(stringResource(R.string.scan_review_delete_failed_title)) },
      text = { Text(stringResource(R.string.scan_review_delete_failed_message)) },
      confirmButton = {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_delete_retry },
        ) {
          Text(stringResource(R.string.scan_review_retry))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onStay,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_delete_stay },
        ) {
          Text(stringResource(R.string.scan_review_stay))
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.scan_retake_delete_failed_dialog },
  )
}

@Composable
private fun SendFailedDialog(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.scan_review_send_failed_title)) },
      text = { Text(message) },
      confirmButton = {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.semantics { testTag = C.Tag.scan_send_failed_retry },
        ) {
          Text(stringResource(R.string.scan_review_retry))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = C.Tag.scan_send_failed_dismiss },
        ) {
          Text(stringResource(R.string.scan_review_dismiss))
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.scan_send_failed_dialog },
  )
}

@Composable
private fun RetakeWarningDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
  AlertDialog(
      onDismissRequest = onCancel,
      title = { Text(stringResource(R.string.scan_review_retake_title)) },
      text = { Text(stringResource(R.string.scan_review_retake_message)) },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_confirm },
        ) {
          Text(stringResource(R.string.scan_review_agree))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.semantics { testTag = C.Tag.scan_retake_cancel },
        ) {
          Text(stringResource(R.string.scan_review_cancel))
        }
      },
      modifier = Modifier.semantics { testTag = C.Tag.scan_retake_dialog },
  )
}

@Composable
private fun SendingDialog() {
  AlertDialog(
      onDismissRequest = {},
      title = { Text(stringResource(R.string.scan_review_sending_title)) },
      text = { Text(stringResource(R.string.scan_review_sending_message)) },
      confirmButton = {},
      modifier = Modifier.semantics { testTag = C.Tag.scan_sending_dialog },
  )
}
