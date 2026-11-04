/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C

enum class OfflineAction {
  OPEN_SCAN,
  START_DISCOVERY,
  OPEN_PROFILE,
  RETURN_HOME,
  OPEN_COLLECTION,
}

// Keep the actions requiring network access in one place so offline behavior is easy to adjust.
internal fun OfflineAction.requiresInternet(): Boolean =
    this == OfflineAction.OPEN_SCAN || this == OfflineAction.START_DISCOVERY

@Composable
internal fun rememberOfflineActionGate(isOnline: Boolean): (OfflineAction, () -> Unit) -> Unit {
  var blockedAction by remember { mutableStateOf<OfflineAction?>(null) }

  val runAction: (OfflineAction, () -> Unit) -> Unit = { action, proceed ->
    if (isOnline || !action.requiresInternet()) {
      proceed()
    } else {
      blockedAction = action
    }
  }

  if (blockedAction != null) {
    AlertDialog(
        onDismissRequest = { blockedAction = null },
        title = { Text(stringResource(R.string.offline_action_required_title)) },
        text = { Text(stringResource(R.string.offline_action_required_message)) },
        confirmButton = {
          TextButton(
              onClick = { blockedAction = null },
              modifier =
                  androidx.compose.ui.Modifier.semantics {
                    testTag = C.Tag.offline_action_dialog_dismiss
                  },
          ) {
            Text(stringResource(R.string.offline_action_required_dismiss))
          }
        },
        modifier = androidx.compose.ui.Modifier.semantics { testTag = C.Tag.offline_action_dialog },
    )
  }

  return runAction
}
