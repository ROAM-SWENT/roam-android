// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.accentIcon

@Composable
internal fun AddFriendsPanel(
    query: String,
    requests: List<FriendRequest>,
    searchResults: List<FriendSearchResult>,
    isSearching: Boolean,
    hasError: Boolean,
    isMutating: Boolean,
    onQueryChanged: (String) -> Unit,
    onAcceptRequest: (FriendRequest) -> Unit,
    onRejectRequest: (FriendRequest) -> Unit,
    onCancelRequest: (FriendRequest) -> Unit,
    onSendRequest: (FriendUser) -> Unit,
    modifier: Modifier = Modifier,
) {
  val isShowingRequests = query.isBlank()
  Surface(
      modifier = modifier,
      shape = RoundedCornerShape(24.dp),
      color = cardBackgroundColor(),
      shadowElevation = 12.dp,
  ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = stringResource(R.string.friends_add_title),
          color = textPrimaryColor(),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      OutlinedTextField(
          value = query,
          onValueChange = onQueryChanged,
          modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.friends_user_search },
          singleLine = true,
          placeholder = { Text(stringResource(R.string.friends_search_users_hint)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          shape = RoundedCornerShape(16.dp),
      )
      LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isShowingRequests) {
          items(requests, key = { "request-${it.user.id}" }) { request ->
            FriendRequestCard(
                request = request,
                onAccept = { onAcceptRequest(request) },
                onReject = { onRejectRequest(request) },
                enabled = !isMutating,
                modifier = Modifier.fillMaxWidth(),
            )
          }
        } else {
          if (isSearching) {
            item {
              Text(
                  stringResource(R.string.friends_searching),
                  color = textPrimaryColor(),
              )
            }
          } else if (searchResults.isEmpty() && !hasError) {
            item {
              Text(
                  text = stringResource(R.string.friends_no_users_found),
                  color = textPrimaryColor(),
              )
            }
          }
          items(searchResults, key = { it.user.id }) { result ->
            AddFriendUserRow(
                result = result,
                enabled = !isMutating,
                onSendRequest = { onSendRequest(result.user) },
                onCancelRequest = {
                  result.outgoingRequestId?.let { requestId ->
                    onCancelRequest(FriendRequest(result.user, id = requestId))
                  }
                },
                onAcceptRequest = {
                  result.incomingRequestId?.let { requestId ->
                    onAcceptRequest(FriendRequest(result.user, id = requestId))
                  }
                },
                onRejectRequest = {
                  result.incomingRequestId?.let { requestId ->
                    onRejectRequest(FriendRequest(result.user, id = requestId))
                  }
                },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AddFriendUserRow(
    result: FriendSearchResult,
    enabled: Boolean,
    onSendRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    onAcceptRequest: () -> Unit,
    onRejectRequest: () -> Unit,
) {
  val user = result.user
  val acceptRequestDescription = stringResource(R.string.friends_accept_request_description)
  val declineRequestDescription = stringResource(R.string.friends_decline_request_description)
  val cancelRequestDescription = stringResource(R.string.friends_cancel_request_description)
  val alreadyFriendsDescription = stringResource(R.string.friends_already_friends_description)
  val addUserDescription = stringResource(R.string.friends_add_user_description)
  Row(
      modifier =
          Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics {
            testTag = C.Tag.friends_add_user_row
          },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Avatar(name = user.username, avatarUrl = user.avatarUrl, modifier = Modifier.size(44.dp))
    Text(
        text = user.username,
        color = textPrimaryColor(),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    when {
      result.incomingRequestId != null -> {
        IconButton(
            onClick = onAcceptRequest,
            enabled = enabled,
            modifier = Modifier.semantics { testTag = C.Tag.friends_accept_request },
        ) {
          Icon(
              Icons.Default.Check,
              contentDescription = acceptRequestDescription,
              tint = MaterialTheme.accentIcon,
          )
        }
        IconButton(
            onClick = onRejectRequest,
            enabled = enabled,
            modifier = Modifier.semantics { testTag = C.Tag.friends_reject_request },
        ) {
          Icon(
              Icons.Default.Close,
              contentDescription = declineRequestDescription,
              tint = MaterialTheme.colorScheme.error,
          )
        }
      }
      result.outgoingRequestId != null -> {
        IconButton(
            onClick = onCancelRequest,
            enabled = enabled,
            modifier = Modifier.semantics { testTag = C.Tag.friends_cancel_request },
        ) {
          Icon(
              Icons.Default.Close,
              contentDescription = cancelRequestDescription,
              tint = MaterialTheme.colorScheme.error,
          )
        }
      }
      result.isFriend -> {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = alreadyFriendsDescription,
            tint = MaterialTheme.accentIcon,
        )
      }
      else -> {
        IconButton(
            onClick = onSendRequest,
            enabled = enabled,
            modifier = Modifier.semantics { testTag = C.Tag.friends_add_user_button },
        ) {
          Icon(
              imageVector = Icons.Default.Add,
              contentDescription = addUserDescription,
              tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}
