/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.HomeAccentGreen
import com.github.roamswent.roam.ui.theme.LoadingProgressAccent
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.isDark

@Composable
internal fun FriendsContent(
    friends: List<FriendUser>,
    friendRequests: List<FriendRequest>,
    pendingRequestCount: Int,
    friendsQuery: String,
    onFriendsQueryChanged: (String) -> Unit,
    profileName: String?,
    onAddFriendsClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    onFriendClicked: (FriendUser) -> Unit,
    onRemoveFriend: (FriendUser) -> Unit,
    onAcceptRequest: (FriendRequest) -> Unit,
    onRejectRequest: (FriendRequest) -> Unit,
    isMutating: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.statusBarsPadding().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    FriendsHeader(
        title = stringResource(R.string.friends_title),
        pendingRequestCount = pendingRequestCount,
        profileName = profileName,
        onAddFriendsClicked = onAddFriendsClicked,
        onProfileClicked = onProfileClicked,
    )
    friendRequests.forEach { request ->
      FriendRequestCard(
          request = request,
          onAccept = { onAcceptRequest(request) },
          onReject = { onRejectRequest(request) },
          enabled = !isMutating,
          modifier = Modifier.fillMaxWidth(),
      )
    }
    FriendsSearchField(
        query = friendsQuery,
        onQueryChanged = onFriendsQueryChanged,
        modifier = Modifier.fillMaxWidth(),
    )
    if (isLoading && friends.isEmpty() && friendRequests.isEmpty()) {
      Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else {
      LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(friends, key = FriendUser::id) { friend ->
          FriendRow(
              friend = friend,
              onClick = { onFriendClicked(friend) },
              onRemove = { onRemoveFriend(friend) },
              enabled = !isMutating,
          )
        }
      }
    }
  }
}

@Composable
private fun FriendsHeader(
    title: String,
    pendingRequestCount: Int,
    profileName: String?,
    onAddFriendsClicked: () -> Unit,
    onProfileClicked: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    AddFriendsButton(
        pendingRequestCount = pendingRequestCount,
        onClick = onAddFriendsClicked,
    )
    Text(
        text = title,
        color =
            if (MaterialTheme.isDark) MaterialTheme.colorScheme.onBackground
            else LoadingTextPrimary,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { testTag = C.Tag.friends_title },
    )
    ProfileButton(name = profileName, onClick = onProfileClicked)
  }
}

@Composable
private fun AddFriendsButton(pendingRequestCount: Int, onClick: () -> Unit) {
  val addFriendsDescription = stringResource(R.string.friends_add_button_description)
  val pendingRequestsDescription =
      stringResource(R.string.friends_pending_requests_description, pendingRequestCount)
  Box {
    Surface(
        onClick = onClick,
        modifier =
            Modifier.size(48.dp).semantics {
              contentDescription = addFriendsDescription
              testTag = C.Tag.friends_add_button
            },
        shape = CircleShape,
        color = HomeAccentGreen,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            tint = Color.White,
        )
      }
    }
    if (pendingRequestCount > 0) {
      Surface(
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                  .semantics {
                    contentDescription = pendingRequestsDescription
                    testTag = C.Tag.friends_pending_request_count
                  },
          shape = CircleShape,
          color = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError,
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
          Text(
              text = pendingRequestCount.toString(),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun ProfileButton(name: String?, onClick: () -> Unit) {
  val description = stringResource(R.string.home_profile_button_description)
  Surface(
      onClick = onClick,
      modifier =
          Modifier.size(48.dp).semantics {
            contentDescription = description
            testTag = C.Tag.home_profile_button
          },
      shape = CircleShape,
      color = HomeAccentGreen,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
          text = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
          color = Color.White,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
internal fun FriendRequestCard(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
  val acceptRequestDescription = stringResource(R.string.friends_accept_request_description)
  val declineRequestDescription = stringResource(R.string.friends_decline_request_description)
  Card(
      modifier = modifier.semantics { testTag = C.Tag.friends_request_card },
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = cardBackgroundColor()),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Avatar(
          name = request.user.username,
          avatarUrl = request.user.avatarUrl,
          modifier = Modifier.size(48.dp),
      )
      Text(
          text = request.user.username,
          modifier = Modifier.weight(1f),
          color = textPrimaryColor(),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      IconButton(
          onClick = onAccept,
          enabled = enabled,
          modifier = Modifier.semantics { testTag = C.Tag.friends_accept_request },
      ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = acceptRequestDescription,
            tint = if (MaterialTheme.isDark) LoadingProgressAccent else HomeAccentGreen,
        )
      }
      IconButton(
          onClick = onReject,
          enabled = enabled,
          modifier = Modifier.semantics { testTag = C.Tag.friends_reject_request },
      ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = declineRequestDescription,
            tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun FriendsSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val searchHint = stringResource(R.string.friends_search_hint)
  OutlinedTextField(
      value = query,
      onValueChange = onQueryChanged,
      modifier = modifier.semantics { testTag = C.Tag.friends_search },
      singleLine = true,
      placeholder = { Text(searchHint) },
      leadingIcon = {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
        )
      },
      shape = RoundedCornerShape(16.dp),
  )
}

@Composable
private fun FriendRow(
    friend: FriendUser,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
  var isMenuExpanded by remember { mutableStateOf(false) }
  var showRemoveConfirmation by remember { mutableStateOf(false) }
  val moreOptionsDescription =
      stringResource(R.string.friends_more_options_description, friend.username)
  val deleteFriendLabel = stringResource(R.string.friends_delete_friend)
  val removeConfirmationTitle = stringResource(R.string.friends_remove_confirmation_title)
  val removeConfirmationMessage =
      stringResource(R.string.friends_remove_confirmation_message, friend.username)
  val cancelLabel = stringResource(R.string.friends_cancel_action)

  Box {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.friends_friend_row },
        shape = RoundedCornerShape(16.dp),
        color = cardBackgroundColor(),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Avatar(
            name = friend.username,
            avatarUrl = friend.avatarUrl,
            modifier = Modifier.size(52.dp),
        )
        Text(
            text = friend.username,
            modifier = Modifier.weight(1f),
            color = textPrimaryColor(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
          IconButton(
              onClick = { isMenuExpanded = true },
              enabled = enabled,
              modifier = Modifier.semantics { contentDescription = moreOptionsDescription },
          ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = textPrimaryColor(),
            )
          }
          DropdownMenu(
              expanded = isMenuExpanded,
              onDismissRequest = { isMenuExpanded = false },
          ) {
            DropdownMenuItem(
                text = { Text(deleteFriendLabel) },
                onClick = {
                  isMenuExpanded = false
                  showRemoveConfirmation = true
                },
                enabled = enabled,
                leadingIcon = {
                  Icon(
                      imageVector = Icons.Default.PersonRemove,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                  )
                },
                modifier = Modifier.semantics { testTag = C.Tag.friends_remove_friend },
            )
          }
        }
      }
    }
  }

  if (showRemoveConfirmation) {
    AlertDialog(
        onDismissRequest = { showRemoveConfirmation = false },
        title = { Text(removeConfirmationTitle) },
        text = { Text(removeConfirmationMessage) },
        confirmButton = {
          TextButton(
              onClick = {
                showRemoveConfirmation = false
                onRemove()
              },
              enabled = enabled,
          ) {
            Text(deleteFriendLabel, color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { showRemoveConfirmation = false }) { Text(cancelLabel) }
        },
    )
  }
}

@Composable
internal fun Avatar(name: String, avatarUrl: String?, modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
  ) {
    if (avatarUrl != null) {
      AsyncImage(
          model = avatarUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    } else {
      Text(
          text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
          color =
              if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
              else HomeAccentGreen,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
internal fun screenBackgroundColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground

@Composable
internal fun cardBackgroundColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface

@Composable
internal fun textPrimaryColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurface else LoadingTextPrimary
