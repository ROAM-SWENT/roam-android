// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.leaderboard.LeaderboardEntry
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.ProfileBubble
import com.github.roamswent.roam.ui.components.ProfilePicture
import com.github.roamswent.roam.ui.theme.HomeAccentGreen
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.OnBrand
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.accentContainer
import com.github.roamswent.roam.ui.theme.accentIcon
import com.github.roamswent.roam.ui.theme.isDark
import com.github.roamswent.roam.ui.theme.onAccentContainer
import com.github.roamswent.roam.ui.theme.secondaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsContent(
    friends: List<FriendUser>,
    friendRequests: List<FriendRequest>,
    pendingRequestCount: Int,
    friendsQuery: String,
    onFriendsQueryChanged: (String) -> Unit,
    profileName: String?,
    profileAvatarBytes: ByteArray?,
    onAddFriendsClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    onFriendClicked: (FriendUser) -> Unit,
    onFriendCollectionClicked: (FriendUser) -> Unit,
    onRemoveFriend: (FriendUser) -> Unit,
    onAcceptRequest: (FriendRequest) -> Unit,
    onRejectRequest: (FriendRequest) -> Unit,
    isMutating: Boolean,
    isLoading: Boolean,
    hasLoaded: Boolean,
    onRefresh: () -> Unit,
    selectedTab: FriendsTab,
    onTabSelected: (FriendsTab) -> Unit,
    leaderboard: List<LeaderboardEntry>,
    currentUserUid: String?,
    hasLeaderboardError: Boolean,
    isLeaderboardLoading: Boolean,
    hasLoadedLeaderboard: Boolean,
    modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val pullRefreshState = rememberPullToRefreshState()
  Column(
      modifier = modifier.statusBarsPadding().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    FriendsHeader(
        title = stringResource(R.string.friends_title),
        pendingRequestCount = pendingRequestCount,
        profileName = profileName,
        profileAvatarBytes = profileAvatarBytes,
        onAddFriendsClicked = onAddFriendsClicked,
        onProfileClicked = onProfileClicked,
    )
    FriendsTabSelector(selectedTab = selectedTab, onTabSelected = onTabSelected)
    // Spinner until the first leaderboard load ends, including the frame before it starts, so the
    // empty state never shows for a leaderboard that was not loaded yet.
    val isLeaderboardInitialLoading = !hasLoadedLeaderboard && !hasLeaderboardError
    // The indicator follows what a pull reloads: the friends, or the leaderboard alone. In the
    // leaderboard it shows for any load the spinner does not cover, such as a retry after a failed
    // first load.
    val isRefreshing =
        if (selectedTab == FriendsTab.Leaderboard) {
          isLeaderboardLoading && !isLeaderboardInitialLoading
        } else {
          isLoading && hasLoaded
        }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth().weight(1f),
        state = pullRefreshState,
        indicator = {
          val refreshDescription = stringResource(R.string.friends_refreshing)
          PullToRefreshDefaults.Indicator(
              state = pullRefreshState,
              isRefreshing = isRefreshing,
              modifier =
                  Modifier.align(Alignment.TopCenter).semantics {
                    contentDescription = refreshDescription
                    testTag = C.Tag.friends_refresh_indicator
                  },
          )
        },
    ) {
      if (selectedTab == FriendsTab.Leaderboard) {
        LeaderboardList(
            entries = leaderboard,
            currentUserUid = currentUserUid,
            hasError = hasLeaderboardError,
            isInitialLoading = isLeaderboardInitialLoading,
            onAddFriendsClicked = onAddFriendsClicked,
            modifier = Modifier.matchParentSize(),
        )
      } else {
        Column(
            modifier = Modifier.matchParentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
          if (isLoading && !hasLoaded && friends.isEmpty() && friendRequests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                  modifier = Modifier.semantics { testTag = C.Tag.friends_initial_loading }
              )
            }
          } else {
            LazyColumn(
                modifier =
                    Modifier.fillMaxWidth().weight(1f).semantics { testTag = C.Tag.friends_list },
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(friends, key = FriendUser::id) { friend ->
                FriendRow(
                    friend = friend,
                    onClick = { onFriendClicked(friend) },
                    onViewCollection = { onFriendCollectionClicked(friend) },
                    onRemove = { onRemoveFriend(friend) },
                    enabled = !isMutating,
                )
              }
            }
          }
        }
      }
    }
  }
}

/** The two views of the Friends tab. */
internal enum class FriendsTab {
  Friends,
  Leaderboard,
}

@Composable
private fun FriendsTabSelector(selectedTab: FriendsTab, onTabSelected: (FriendsTab) -> Unit) {
  val tabs =
      listOf(
          Triple(FriendsTab.Friends, R.string.friends_tab_friends, C.Tag.friends_tab_friends),
          Triple(
              FriendsTab.Leaderboard,
              R.string.friends_tab_leaderboard,
              C.Tag.friends_tab_leaderboard,
          ),
      )
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    tabs.forEachIndexed { index, (tab, labelRes, tag) ->
      SegmentedButton(
          selected = selectedTab == tab,
          onClick = { onTabSelected(tab) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
          modifier = Modifier.semantics { testTag = tag },
      ) {
        Text(stringResource(labelRes))
      }
    }
  }
}

/**
 * Shows [entries] as given, without re-sorting. The "no friends" prompt appears when no entry
 * belongs to someone other than [currentUserUid], which includes an empty list; it is replaced by
 * the error when nothing could be loaded, so a failure is not mistaken for having no friends.
 */
@Composable
private fun LeaderboardList(
    entries: List<LeaderboardEntry>,
    currentUserUid: String?,
    hasError: Boolean,
    isInitialLoading: Boolean,
    onAddFriendsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (isInitialLoading) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      CircularProgressIndicator(
          modifier = Modifier.semantics { testTag = C.Tag.friends_initial_loading }
      )
    }
    return
  }
  val hasFriends = entries.any { it.uid != currentUserUid }
  LazyColumn(
      modifier = modifier.semantics { testTag = C.Tag.friends_leaderboard_list },
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (hasError) {
      item(key = LEADERBOARD_ERROR_KEY) { LeaderboardErrorNotice() }
    }
    items(entries, key = LeaderboardEntry::uid) { entry ->
      LeaderboardRow(entry = entry, isCurrentUser = entry.uid == currentUserUid)
    }
    if (!hasFriends && !(hasError && entries.isEmpty())) {
      item(key = LEADERBOARD_EMPTY_KEY) { LeaderboardEmptyState(onAddFriendsClicked) }
    }
  }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, isCurrentUser: Boolean) {
  val rankDescription = stringResource(R.string.friends_leaderboard_rank_description, entry.rank)
  Surface(
      modifier =
          Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            testTag =
                if (isCurrentUser) C.Tag.friends_leaderboard_current_user_row
                else C.Tag.friends_leaderboard_row
          },
      shape = RoundedCornerShape(16.dp),
      color = cardBackgroundColor(),
      border = if (isCurrentUser) BorderStroke(2.dp, MaterialTheme.accentIcon) else null,
  ) {
    // LeaderboardEntry carries no avatar yet, so the row shows the username's initial.
    UserCardRow(
        username = entry.username,
        avatarBytes = null,
        leading = {
          Text(
              text = stringResource(R.string.friends_leaderboard_rank, entry.rank),
              modifier =
                  Modifier.widthIn(min = 36.dp).semantics { contentDescription = rankDescription },
              color = MaterialTheme.accentIcon,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
          )
        },
    ) {
      if (isCurrentUser) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.accentContainer,
            contentColor = MaterialTheme.onAccentContainer,
        ) {
          Text(
              text = stringResource(R.string.friends_leaderboard_you),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
          )
        }
      }
      Text(
          text =
              pluralStringResource(
                  R.plurals.friends_leaderboard_monument_count,
                  entry.verifiedMonumentCount,
                  entry.verifiedMonumentCount,
              ),
          color = MaterialTheme.secondaryText,
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun LeaderboardEmptyState(onAddFriendsClicked: () -> Unit) {
  Card(
      modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.friends_leaderboard_empty },
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = cardBackgroundColor()),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = stringResource(R.string.friends_leaderboard_empty_title),
          color = textPrimaryColor(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
      )
      Text(
          text = stringResource(R.string.friends_leaderboard_empty_message),
          color = MaterialTheme.secondaryText,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
      )
      Button(
          onClick = onAddFriendsClicked,
          modifier = Modifier.semantics { testTag = C.Tag.friends_leaderboard_add_friends },
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.accentContainer,
                  contentColor = MaterialTheme.onAccentContainer,
              ),
      ) {
        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
        Text(
            text = stringResource(R.string.friends_leaderboard_add_friends_action),
            modifier = Modifier.padding(start = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun LeaderboardErrorNotice() {
  Surface(
      modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.friends_leaderboard_error },
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
  ) {
    Text(
        text = stringResource(R.string.friends_leaderboard_error),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}

/** Avatar and username of a user card, between optional [leading] and [trailing] content. */
@Composable
private fun UserCardRow(
    username: String,
    avatarBytes: ByteArray?,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    leading()
    ProfilePicture(
        name = username,
        avatarBytes = avatarBytes,
        modifier = Modifier.size(52.dp),
    )
    Text(
        text = username,
        modifier = Modifier.weight(1f),
        color = textPrimaryColor(),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    trailing()
  }
}

@Composable
private fun FriendsHeader(
    title: String,
    pendingRequestCount: Int,
    profileName: String?,
    profileAvatarBytes: ByteArray?,
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
    ProfileBubble(
        name = profileName.orEmpty(),
        avatarBytes = profileAvatarBytes,
        onClick = onProfileClicked,
        testTag = C.Tag.home_profile_button,
    )
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
            tint = OnBrand,
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
      ProfilePicture(
          name = request.user.username,
          avatarBytes = request.user.avatarBytes,
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
            tint = MaterialTheme.accentIcon,
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
internal fun FriendsSearchField(
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
    onViewCollection: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
  var isMenuExpanded by remember { mutableStateOf(false) }
  var showRemoveConfirmation by remember { mutableStateOf(false) }
  val moreOptionsDescription =
      stringResource(R.string.friends_more_options_description, friend.username)
  val viewCollectionDescription =
      stringResource(R.string.friends_view_collection_description, friend.username)
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
      UserCardRow(username = friend.username, avatarBytes = friend.avatarBytes) {
        IconButton(
            onClick = onViewCollection,
            modifier =
                Modifier.semantics {
                  contentDescription = viewCollectionDescription
                  testTag = C.Tag.friends_view_collection
                },
        ) {
          Icon(
              imageVector = Icons.Default.AccountBalance,
              contentDescription = null,
              tint = MaterialTheme.accentIcon,
          )
        }
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
internal fun screenBackgroundColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground

@Composable
internal fun cardBackgroundColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface

@Composable
internal fun textPrimaryColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurface else LoadingTextPrimary

private const val LEADERBOARD_ERROR_KEY = "leaderboard_error"
private const val LEADERBOARD_EMPTY_KEY = "leaderboard_empty"
