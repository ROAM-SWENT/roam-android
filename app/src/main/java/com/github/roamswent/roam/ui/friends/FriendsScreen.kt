/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.bottomdock.AppBottomDock
import com.github.roamswent.roam.ui.components.bottomdock.DockDestination
import com.github.roamswent.roam.ui.components.bottomdock.dockContentClearance
import com.github.roamswent.roam.ui.theme.HomeAccentGreen
import com.github.roamswent.roam.ui.theme.LoadingProgressAccent
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.isDark

/** UI data supplied by the friends feature while its repository is being implemented. */
data class FriendUser(
    val id: String,
    val username: String,
    val avatarUrl: String? = null,
)

/** An incoming request with its creation time, used to display the newest requests first. */
data class FriendRequest(
    val user: FriendUser,
    val requestedAtMillis: Long,
)

/**
 * Renders friends and incoming requests from injected UI data. Search and navigation callbacks are
 * placeholders for the future feature integration.
 */
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    friends: List<FriendUser> = emptyList(),
    friendRequests: List<FriendRequest> = emptyList(),
    searchResults: List<FriendUser> = emptyList(),
    profileName: String? = null,
    onProfileClicked: () -> Unit = {},
    onFriendClicked: (FriendUser) -> Unit = {},
    onUserSearchChanged: (String) -> Unit = {},
    showAddFriendsInitially: Boolean = false,
) {
  var friendsQuery by remember { mutableStateOf("") }
  var userQuery by remember { mutableStateOf("") }
  var isAddFriendsOpen by
      remember(showAddFriendsInitially) { mutableStateOf(showAddFriendsInitially) }
  val newestRequests =
      remember(friendRequests) {
        friendRequests
            .sortedByDescending(FriendRequest::requestedAtMillis)
            .take(MAX_VISIBLE_REQUESTS)
      }
  val filteredFriends =
      remember(friends, friendsQuery) {
        friends.filter { it.username.contains(friendsQuery.trim(), ignoreCase = true) }
      }

  Box(
      modifier =
          modifier.fillMaxSize().background(screenBackgroundColor()).semantics {
            testTag = C.Tag.friends_screen_container
          }
  ) {
    FriendsContent(
        friends = filteredFriends,
        friendRequests = newestRequests,
        pendingRequestCount = friendRequests.size,
        friendsQuery = friendsQuery,
        onFriendsQueryChanged = { friendsQuery = it },
        profileName = profileName,
        onAddFriendsClicked = { isAddFriendsOpen = true },
        onProfileClicked = onProfileClicked,
        onFriendClicked = onFriendClicked,
        modifier = Modifier.fillMaxSize().blur(if (isAddFriendsOpen) 8.dp else 0.dp),
    )

    if (isAddFriendsOpen) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                  .clickable(
                      interactionSource = remember { MutableInteractionSource() },
                      indication = null,
                      onClick = { isAddFriendsOpen = false },
                  )
                  .semantics { testTag = C.Tag.friends_add_dismiss }
      )
      AddFriendsPanel(
          query = userQuery,
          requests = friendRequests,
          searchResults = searchResults,
          onQueryChanged = {
            userQuery = it
            onUserSearchChanged(it)
          },
          modifier =
              Modifier.align(Alignment.Center).fillMaxWidth(0.9f).fillMaxHeight(0.78f).pointerInput(
                  Unit
              ) {
                awaitEachGesture {
                  awaitFirstDown(requireUnconsumed = false)
                  waitForUpOrCancellation()
                }
              },
      )
    }
  }
}

@Composable
private fun FriendsContent(
    friends: List<FriendUser>,
    friendRequests: List<FriendRequest>,
    pendingRequestCount: Int,
    friendsQuery: String,
    onFriendsQueryChanged: (String) -> Unit,
    profileName: String?,
    onAddFriendsClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    onFriendClicked: (FriendUser) -> Unit,
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
      FriendRequestCard(request = request, modifier = Modifier.fillMaxWidth())
    }
    FriendsSearchField(
        query = friendsQuery,
        onQueryChanged = onFriendsQueryChanged,
        modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(friends, key = FriendUser::id) { friend ->
        FriendRow(friend = friend, onClick = { onFriendClicked(friend) })
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
  Box {
    Surface(
        onClick = onClick,
        modifier =
            Modifier.size(48.dp).semantics {
              contentDescription = "Add friends"
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
                    contentDescription = "$pendingRequestCount pending friend requests"
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
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun FriendRequestCard(request: FriendRequest, modifier: Modifier = Modifier) {
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
          onClick = {},
          modifier = Modifier.semantics { testTag = C.Tag.friends_accept_request },
      ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Accept friend request",
            tint = if (MaterialTheme.isDark) LoadingProgressAccent else HomeAccentGreen,
        )
      }
      IconButton(
          onClick = {},
          modifier = Modifier.semantics { testTag = C.Tag.friends_reject_request },
      ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Decline friend request",
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
  OutlinedTextField(
      value = query,
      onValueChange = onQueryChanged,
      modifier = modifier.semantics { testTag = C.Tag.friends_search },
      singleLine = true,
      placeholder = { Text("Search friends") },
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
private fun FriendRow(friend: FriendUser, onClick: () -> Unit) {
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
          color = textPrimaryColor(),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun AddFriendsPanel(
    query: String,
    requests: List<FriendRequest>,
    searchResults: List<FriendUser>,
    onQueryChanged: (String) -> Unit,
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
          text = "Add friends",
          color = textPrimaryColor(),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      OutlinedTextField(
          value = query,
          onValueChange = onQueryChanged,
          modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.friends_user_search },
          singleLine = true,
          placeholder = { Text("Search users") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          shape = RoundedCornerShape(16.dp),
      )
      LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isShowingRequests) {
          items(requests, key = { "request-${it.user.id}" }) { request ->
            FriendRequestCard(request = request, modifier = Modifier.fillMaxWidth())
          }
        } else {
          items(searchResults, key = FriendUser::id) { user -> AddFriendUserRow(user = user) }
        }
      }
    }
  }
}

@Composable
private fun AddFriendUserRow(user: FriendUser) {
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
    IconButton(
        onClick = {},
        modifier = Modifier.semantics { testTag = C.Tag.friends_add_user_button },
    ) {
      Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Add user",
          tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun Avatar(name: String, avatarUrl: String?, modifier: Modifier = Modifier) {
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
private fun screenBackgroundColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground

@Composable
private fun cardBackgroundColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface

@Composable
private fun textPrimaryColor() =
    if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurface else LoadingTextPrimary

@Preview(name = "Friends - light - with request", showBackground = true)
@Composable
private fun FriendsScreenLightWithRequestPreview() {
  FriendsScreenPreviewContent(darkTheme = false, showFriendRequest = true)
}

@Preview(name = "Friends - light - no request", showBackground = true)
@Composable
private fun FriendsScreenLightWithoutRequestPreview() {
  FriendsScreenPreviewContent(darkTheme = false, showFriendRequest = false)
}

@Preview(name = "Friends - dark - with request", showBackground = true)
@Composable
private fun FriendsScreenDarkWithRequestPreview() {
  FriendsScreenPreviewContent(darkTheme = true, showFriendRequest = true)
}

@Preview(name = "Friends - dark - no request", showBackground = true)
@Composable
private fun FriendsScreenDarkWithoutRequestPreview() {
  FriendsScreenPreviewContent(darkTheme = true, showFriendRequest = false)
}

@Preview(name = "Friends - light - add friends", showBackground = true)
@Composable
private fun FriendsScreenAddFriendsPanelPreview() {
  FriendsScreenPreviewContent(
      darkTheme = false,
      showFriendRequest = true,
      showAddFriendsPanel = true,
  )
}

/** Shares editable sample data and theme settings across the interactive previews. */
@Composable
private fun FriendsScreenPreviewContent(
    darkTheme: Boolean,
    showFriendRequest: Boolean,
    showAddFriendsPanel: Boolean = false,
) {
  var selectedDestination by remember { mutableStateOf(DockDestination.FRIENDS) }
  val friends =
      listOf(
          FriendUser(id = "preview-1", username = "Ada Lovelace"),
          FriendUser(id = "preview-2", username = "Grace Hopper"),
          FriendUser(id = "preview-3", username = "Katherine Johnson"),
      )
  val requests =
      if (showFriendRequest) {
        listOf(
            FriendRequest(
                user = FriendUser(id = "preview-request", username = "Alan Turing"),
                requestedAtMillis = 1L,
            )
        )
      } else {
        emptyList()
      }

  SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
    Box(modifier = Modifier.fillMaxSize()) {
      FriendsScreen(
          modifier = Modifier.fillMaxSize().padding(bottom = dockContentClearance()),
          friends = friends,
          friendRequests = requests,
          searchResults = friends,
          profileName = "Margaret Hamilton",
          showAddFriendsInitially = showAddFriendsPanel,
      )
      AppBottomDock(
          selected = selectedDestination,
          onDestinationSelected = { selectedDestination = it },
          onScanClick = {},
          modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

private const val MAX_VISIBLE_REQUESTS = 3
