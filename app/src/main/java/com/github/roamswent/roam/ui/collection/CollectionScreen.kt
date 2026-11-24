// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.ProfileBubble
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.home.HomeViewModel
import com.github.roamswent.roam.ui.theme.CollectionPlaceholder
import com.github.roamswent.roam.ui.theme.CollectionPlaceholderIcon
import com.github.roamswent.roam.ui.theme.LoadingErrorText
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.LoadingTextSecondary
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.accentContainer
import com.github.roamswent.roam.ui.theme.accentIcon
import com.github.roamswent.roam.ui.theme.cardContainer
import com.github.roamswent.roam.ui.theme.isDark
import com.github.roamswent.roam.ui.theme.onAccentContainer
import com.github.roamswent.roam.ui.theme.photoButtonContainer
import com.github.roamswent.roam.ui.theme.secondaryText
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.apache.commons.text.similarity.LevenshteinDistance

/**
 * Shows the user's saved guides as a two-column grid of cards, newest first. Guides already loaded
 * stay visible while the collection reloads or fails to refresh. Each card has a heart that calls
 * [onToggleFavorite], a delete button that calls [onDeleteGuide], and the All/Favourites chips call
 * [onShowFavoritesOnlyChange].
 *
 * Unliking a guide while only favourites are shown hides its card, so a snackbar offers to undo it
 * through [onRestoreFavorite]. A [SavedGuidesUiState.favoriteError] is shown in the same snackbar,
 * then reported through [onFavoriteErrorShown] so it is not shown again.
 */
@Composable
fun CollectionScreen(
    state: SavedGuidesUiState,
    modifier: Modifier = Modifier,
    onGuideClick: (String) -> Unit = {},
    profileName: String = HomeViewModel.DEFAULT_USER_NAME,
    profileAvatarBytes: ByteArray? = null,
    onProfileClicked: () -> Unit = {},
    onDeleteGuide: (String) -> Unit = {},
    onDeleteErrorShown: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onRestoreFavorite: (String) -> Unit = {},
    onFavoriteErrorShown: () -> Unit = {},
    onShowFavoritesOnlyChange: (Boolean) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    title: String? = null,
    readOnly: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onRetryGuides: (() -> Unit)? = null,
    onOpenSharing: () -> Unit = {},
    friends: List<CollectionFriend> = emptyList(),
    isLoadingFriends: Boolean = false,
    friendsError: Exception? = null,
    onRetryLoadingFriends: () -> Unit = {},
    onSetShareAudience: (String, ShareAudience, Set<String>) -> Unit = { _, _, _ -> },
    onSharingErrorShown: () -> Unit = {},
    onPickFromGallery: () -> Unit = {},
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val removedMessage = stringResource(R.string.collection_favorite_removed)
  val undoLabel = stringResource(R.string.collection_favorite_undo)
  val errorMessage = stringResource(R.string.collection_favorite_error)
  val deleteErrorMessage = stringResource(R.string.collection_delete_error)
  val sharingErrorMessage = stringResource(R.string.collection_share_error)

  // Showing a message replaces the current one, so taps never queue up stale snackbars.
  fun showMessage(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      val result = snackbarHostState.showSnackbar(message, actionLabel = actionLabel)
      if (result == SnackbarResult.ActionPerformed) onAction()
    }
  }

  LaunchedEffect(state.favoriteError) {
    if (state.favoriteError != null) {
      showMessage(errorMessage)
      // Cleared at once so the error is not shown again after a rotation or a tab switch.
      onFavoriteErrorShown()
    }
  }

  LaunchedEffect(state.deleteError) {
    if (state.deleteError != null) {
      showMessage(deleteErrorMessage)
      onDeleteErrorShown()
    }
  }

  LaunchedEffect(state.sharingError) {
    if (state.sharingError != null) {
      showMessage(sharingErrorMessage)
      onSharingErrorShown()
    }
  }

  val toggleFavorite: (Guide) -> Unit = { guide ->
    onToggleFavorite(guide.id)
    // In All mode the heart stays visible and can simply be tapped again.
    if (state.showFavoritesOnly && guide.isFavorite) {
      showMessage(removedMessage, undoLabel) { onRestoreFavorite(guide.id) }
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize().semantics { testTag = C.Tag.collection_screen },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.background else ScreenBackground,
  ) { contentPadding ->
    Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onBackClick != null) {
          IconButton(
              onClick = onBackClick,
              modifier = Modifier.semantics { testTag = C.Tag.collection_back_button },
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.collection_back_description),
                tint =
                    if (MaterialTheme.isDark) MaterialTheme.colorScheme.onBackground
                    else LoadingTextPrimary,
            )
          }
        }
        Text(
            text = title ?: stringResource(R.string.collection_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color =
                if (MaterialTheme.isDark) MaterialTheme.colorScheme.onBackground
                else LoadingTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.weight(1f).padding(vertical = 8.dp).semantics {
                  testTag = C.Tag.collection_title
                },
        )
        ProfileBubble(
            name = profileName,
            avatarBytes = profileAvatarBytes,
            onClick = onProfileClicked,
            testTag = C.Tag.collection_profile_button,
        )
      }
      if (state.guides.isNotEmpty() && state.guidesError != null && onRetryGuides != null) {
        CollectionRetryError(onRetryGuides, Modifier.padding(horizontal = 20.dp))
      }
      when {
        state.guides.isNotEmpty() -> {
          CollectionSearchBar(
              query = state.searchQuery,
              onQueryChange = onSearchQueryChange,
              readOnly = readOnly,
              modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
          )
          if (!readOnly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
              CollectionFilterChips(state.showFavoritesOnly, onShowFavoritesOnlyChange)
              Spacer(Modifier.weight(1f))
              CollectionGalleryButton(onPickFromGallery)
            }
          }
          val guides = visibleGuides(state.guides, state.showFavoritesOnly, state.searchQuery)
          when {
            guides.isEmpty() && state.searchQuery.isNotBlank() -> {
              CollectionMessage(C.Tag.collection_search_no_matches) { CollectionSearchNoMatches() }
            }
            guides.isEmpty() -> {
              CollectionMessage(C.Tag.collection_favorites_empty) { FavoritesEmpty() }
            }
            else -> {
              GuideGrid(
                  guides = guides,
                  onGuideClick = onGuideClick,
                  onDeleteGuide = onDeleteGuide,
                  deletingGuideIds = state.deletingGuideIds,
                  onToggleFavorite = toggleFavorite,
                  readOnly = readOnly,
                  sharingGuideIds = state.sharingGuideIds,
                  friends = friends,
                  isLoadingFriends = isLoadingFriends,
                  friendsError = friendsError,
                  onRetryLoadingFriends = onRetryLoadingFriends,
                  onOpenSharing = onOpenSharing,
                  onSetShareAudience = onSetShareAudience,
              )
            }
          }
        }
        state.isLoadingGuides -> CollectionLoading()
        state.guidesError != null ->
            CollectionMessage(C.Tag.collection_error) {
              if (onRetryGuides != null) CollectionRetryError(onRetryGuides) else CollectionError()
            }
        else -> {
          if (!readOnly) {
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
              CollectionGalleryButton(onPickFromGallery)
            }
          }
          CollectionMessage(C.Tag.collection_empty_state) { CollectionEmpty(readOnly) }
        }
      }
    }
  }
}

@Composable
private fun CollectionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
  val isDark = MaterialTheme.isDark
  val containerColor = MaterialTheme.cardContainer
  val textColor = if (isDark) MaterialTheme.colorScheme.onSurface else LoadingTextPrimary
  val hintColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else LoadingTextSecondary

  TextField(
      value = query,
      onValueChange = { if (it.length <= 40) onQueryChange(it) },
      modifier = modifier.fillMaxWidth().semantics { testTag = C.Tag.collection_search_bar },
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyMedium,
      placeholder = {
        Text(
            text =
                stringResource(
                    if (readOnly) R.string.friend_collection_search_hint
                    else R.string.collection_search_hint
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
      },
      leadingIcon = {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
        )
      },
      trailingIcon =
          if (query.isNotEmpty()) {
            {
              IconButton(
                  onClick = { onQueryChange("") },
                  modifier = Modifier.semantics { testTag = C.Tag.collection_search_clear_button },
              ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription =
                        stringResource(R.string.collection_search_clear_description),
                )
              }
            }
          } else {
            null
          },
      shape = RoundedCornerShape(16.dp),
      colors =
          TextFieldDefaults.colors(
              focusedContainerColor = containerColor,
              unfocusedContainerColor = containerColor,
              disabledContainerColor = containerColor,
              focusedTextColor = textColor,
              unfocusedTextColor = textColor,
              focusedLeadingIconColor = hintColor,
              unfocusedLeadingIconColor = hintColor,
              focusedTrailingIconColor = hintColor,
              unfocusedTrailingIconColor = hintColor,
              focusedPlaceholderColor = hintColor,
              unfocusedPlaceholderColor = hintColor,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
          ),
  )
}

/** The All and Favourites chips; Favourites is selected when [favoritesOnly] is true. */
@Composable
private fun CollectionFilterChips(
    favoritesOnly: Boolean,
    onChange: (Boolean) -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CollectionFilterChip(
        label = stringResource(R.string.collection_filter_all),
        selected = !favoritesOnly,
        onClick = { onChange(false) },
        tag = C.Tag.collection_filter_all,
    )
    CollectionFilterChip(
        label = stringResource(R.string.collection_filter_favorites),
        selected = favoritesOnly,
        onClick = { onChange(true) },
        tag = C.Tag.collection_filter_favorites,
        showHeart = true,
    )
  }
}

@Composable
private fun CollectionGalleryButton(onPickFromGallery: () -> Unit) {
  IconButton(
      onClick = onPickFromGallery,
      modifier = Modifier.semantics { testTag = C.Tag.collection_gallery_button },
  ) {
    Icon(
        imageVector = Icons.Rounded.AddPhotoAlternate,
        contentDescription = stringResource(R.string.scan_gallery_description),
        tint = MaterialTheme.accentIcon,
    )
  }
}

@Composable
private fun CollectionFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    showHeart: Boolean = false,
) {
  val isDark = MaterialTheme.isDark
  val selectedContainer = MaterialTheme.accentContainer
  val selectedContent = MaterialTheme.onAccentContainer
  val container = MaterialTheme.cardContainer
  val content = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else LoadingTextSecondary
  FilterChip(
      selected = selected,
      onClick = onClick,
      label = {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
      },
      leadingIcon =
          if (showHeart) {
            {
              Icon(
                  imageVector = Icons.Outlined.FavoriteBorder,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
              )
            }
          } else {
            null
          },
      shape = RoundedCornerShape(50),
      colors =
          FilterChipDefaults.filterChipColors(
              containerColor = container,
              labelColor = content,
              iconColor = content,
              selectedContainerColor = selectedContainer,
              selectedLabelColor = selectedContent,
              selectedLeadingIconColor = selectedContent,
          ),
      border = null,
      modifier = Modifier.semantics { testTag = tag },
  )
}

@Composable
private fun GuideGrid(
    guides: List<Guide>,
    onGuideClick: (String) -> Unit,
    onDeleteGuide: (String) -> Unit,
    deletingGuideIds: Set<String>,
    onToggleFavorite: (Guide) -> Unit,
    readOnly: Boolean,
    sharingGuideIds: Set<String>,
    friends: List<CollectionFriend>,
    isLoadingFriends: Boolean,
    friendsError: Exception?,
    onRetryLoadingFriends: () -> Unit,
    onOpenSharing: () -> Unit,
    onSetShareAudience: (String, ShareAudience, Set<String>) -> Unit,
) {
  LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.collection_grid },
  ) {
    items(guides, key = { it.id }) { guide ->
      GuideCard(
          guide,
          onClick = { onGuideClick(guide.id) },
          onDelete = { onDeleteGuide(guide.id) },
          isDeleting = guide.id in deletingGuideIds,
          onToggleFavorite = { onToggleFavorite(guide) },
          readOnly = readOnly,
          isSharing = guide.id in sharingGuideIds,
          friends = friends,
          isLoadingFriends = isLoadingFriends,
          friendsError = friendsError,
          onRetryLoadingFriends = onRetryLoadingFriends,
          onOpenSharing = onOpenSharing,
          onSetShareAudience = { audience, friendUids ->
            onSetShareAudience(guide.id, audience, friendUids)
          },
      )
    }
  }
}

@Composable
private fun GuideCard(
    guide: Guide,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isDeleting: Boolean,
    onToggleFavorite: () -> Unit,
    readOnly: Boolean,
    isSharing: Boolean,
    friends: List<CollectionFriend>,
    isLoadingFriends: Boolean,
    friendsError: Exception?,
    onRetryLoadingFriends: () -> Unit,
    onOpenSharing: () -> Unit,
    onSetShareAudience: (ShareAudience, Set<String>) -> Unit,
) {
  var isShareDialogOpen by remember(guide.id) { mutableStateOf(false) }
  Card(
      onClick = onClick,
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.cardContainer),
      modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.collection_card },
  ) {
    Box {
      GuidePhoto(guide)
      if (!readOnly) {
        DeleteButton(
            onClick = onDelete,
            enabled = !isDeleting,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
        FavoriteButton(
            isFavorite = guide.isFavorite,
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
        ShareButton(
            isSharing = isSharing,
            onClick = {
              isShareDialogOpen = true
              onOpenSharing()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
      }
    }
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
          text = guide.monumentName,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color =
              if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurface else LoadingTextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.semantics { testTag = C.Tag.collection_card_name },
      )
      guide.discoveryDate?.let { date ->
        Text(
            text = formatDiscoveryDate(date),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
                else LoadingTextSecondary,
            modifier =
                Modifier.padding(top = 4.dp).semantics { testTag = C.Tag.collection_card_date },
        )
      }
      if (!readOnly) {
        Text(
            text = shareStatusText(guide.shareSettings),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.secondaryText,
            modifier =
                Modifier.padding(top = 4.dp).semantics { testTag = C.Tag.collection_share_status },
        )
      }
    }
  }
  if (!readOnly && isShareDialogOpen) {
    GuideSharingDialog(
        guide = guide,
        friends = friends,
        isLoadingFriends = isLoadingFriends,
        friendsError = friendsError,
        isSaving = isSharing,
        onRetryLoadingFriends = onRetryLoadingFriends,
        onDismiss = { isShareDialogOpen = false },
        onConfirm = { audience, friendUids ->
          onSetShareAudience(audience, friendUids)
          isShareDialogOpen = false
        },
    )
  }
}

@Composable
private fun shareStatusText(settings: MonumentShareSettings): String =
    when (settings.audience) {
      ShareAudience.NONE -> stringResource(R.string.collection_share_private)
      ShareAudience.ALL_FRIENDS -> stringResource(R.string.collection_share_all_friends)
      ShareAudience.ALL_FOLLOWERS -> stringResource(R.string.collection_share_all_followers)
      ShareAudience.SELECTED_FRIENDS ->
          stringResource(R.string.collection_share_status, settings.sharedWithUids.size)
    }

@Composable
private fun ShareButton(isSharing: Boolean, onClick: () -> Unit, modifier: Modifier) {
  IconButton(
      onClick = onClick,
      enabled = !isSharing,
      modifier =
          modifier
              .size(36.dp)
              .background(MaterialTheme.photoButtonContainer, CircleShape)
              .semantics { testTag = C.Tag.collection_card_share },
  ) {
    if (isSharing) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
      Icon(
          imageVector = Icons.Filled.Share,
          contentDescription = stringResource(R.string.collection_share_button),
          tint = MaterialTheme.accentIcon,
          modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun GuideSharingDialog(
    guide: Guide,
    friends: List<CollectionFriend>,
    isLoadingFriends: Boolean,
    friendsError: Exception?,
    isSaving: Boolean,
    onRetryLoadingFriends: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (ShareAudience, Set<String>) -> Unit,
) {
  var audience by remember(guide.id) { mutableStateOf(guide.shareSettings.audience) }
  val selectedFriendUids =
      remember(guide.id) {
        mutableStateSetOf<String>().apply { addAll(guide.shareSettings.sharedWithUids) }
      }
  val needsFriends = audience == ShareAudience.SELECTED_FRIENDS
  val recipients =
      if (needsFriends) {
        selectedFriendUids.filter { selectedUid -> friends.any { it.uid == selectedUid } }.toSet()
      } else {
        emptySet()
      }
  AlertDialog(
      modifier = Modifier.semantics { testTag = C.Tag.collection_share_dialog },
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.collection_share_title, guide.monumentName)) },
      text = {
        Column {
          Text(
              text = stringResource(R.string.collection_share_message),
              style = MaterialTheme.typography.bodyMedium,
          )
          ShareAudienceOption(
              label = stringResource(R.string.collection_share_audience_none),
              selected = audience == ShareAudience.NONE,
              tag = C.Tag.collection_share_audience_none,
              onSelect = { audience = ShareAudience.NONE },
          )
          ShareAudienceOption(
              label = stringResource(R.string.collection_share_audience_all_friends),
              selected = audience == ShareAudience.ALL_FRIENDS,
              tag = C.Tag.collection_share_audience_all_friends,
              onSelect = { audience = ShareAudience.ALL_FRIENDS },
          )
          ShareAudienceOption(
              label = stringResource(R.string.collection_share_audience_selected),
              selected = needsFriends,
              tag = C.Tag.collection_share_audience_selected,
              onSelect = { audience = ShareAudience.SELECTED_FRIENDS },
          )
          when {
            !needsFriends -> Unit
            isLoadingFriends -> {
              CircularProgressIndicator(
                  modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
              )
            }
            friendsError != null -> {
              Column {
                Text(stringResource(R.string.collection_share_friends_error))
                TextButton(onClick = onRetryLoadingFriends) {
                  Text(stringResource(R.string.collection_share_retry))
                }
              }
            }
            friends.isEmpty() -> {
              Text(
                  text = stringResource(R.string.collection_share_no_friends),
                  modifier = Modifier.padding(top = 12.dp),
              )
            }
            else -> {
              Column(
                  modifier =
                      Modifier.padding(top = 8.dp)
                          .heightIn(max = 280.dp)
                          .verticalScroll(rememberScrollState()),
              ) {
                friends.forEach { friend ->
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier =
                          Modifier.fillMaxWidth()
                              .toggleable(
                                  value = friend.uid in selectedFriendUids,
                                  role = Role.Checkbox,
                                  onValueChange = { checked ->
                                    if (checked) selectedFriendUids.add(friend.uid)
                                    else selectedFriendUids.remove(friend.uid)
                                  },
                              )
                              .semantics { testTag = C.Tag.collection_share_friend },
                  ) {
                    Checkbox(
                        checked = friend.uid in selectedFriendUids,
                        onCheckedChange = null,
                    )
                    Text(
                        text = friend.username,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
            onClick = { onConfirm(audience, recipients) },
            enabled =
                !isSaving &&
                    (!needsFriends ||
                        (!isLoadingFriends && friendsError == null && recipients.isNotEmpty())),
            modifier = Modifier.semantics { testTag = C.Tag.collection_share_confirm },
        ) {
          Text(stringResource(R.string.collection_share_confirm))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            enabled = !isSaving,
            modifier = Modifier.semantics { testTag = C.Tag.collection_share_cancel },
        ) {
          Text(stringResource(R.string.collection_share_cancel))
        }
      },
  )
}

@Composable
private fun ShareAudienceOption(
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
              .semantics { testTag = tag },
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}

/**
 * Shows the guide's stored photo, with a spinner while it decodes. A guide without a photo, or
 * whose photo cannot be decoded, shows a placeholder instead.
 */
@Composable
private fun GuidePhoto(guide: Guide) {
  val photoModifier = Modifier.fillMaxWidth().aspectRatio(1f)
  if (guide.photoBytes.isEmpty()) {
    PhotoPlaceholder(photoModifier, C.Tag.collection_card_photo_placeholder)
    return
  }
  // Every Firestore snapshot hands out a new array for the same photo, so the photo is identified
  // by
  // its contents. Keying on the array itself would restart the decode (and the spinner) each time.
  val photoKey = remember(guide.photoBytes) { "${guide.id}:${guide.photoBytes.sha256Hex()}" }
  val context = LocalContext.current
  val request =
      remember(photoKey) {
        ImageRequest.Builder(context).data(guide.photoBytes).memoryCacheKey(photoKey).build()
      }
  var photoState by
      remember(photoKey) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
  Box(contentAlignment = Alignment.Center, modifier = photoModifier) {
    AsyncImage(
        model = request,
        contentDescription =
            stringResource(R.string.collection_photo_description, guide.monumentName),
        contentScale = ContentScale.Crop,
        onState = { photoState = it },
        modifier =
            Modifier.matchParentSize()
                .background(
                    if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface
                    else CollectionPlaceholder
                )
                .semantics { testTag = C.Tag.collection_card_photo },
    )
    when (photoState) {
      is AsyncImagePainter.State.Empty,
      is AsyncImagePainter.State.Loading ->
          CircularProgressIndicator(
              modifier =
                  Modifier.size(32.dp).semantics { testTag = C.Tag.collection_card_photo_loading }
          )
      is AsyncImagePainter.State.Error ->
          PhotoPlaceholder(
              Modifier.matchParentSize(),
              C.Tag.collection_card_photo_error,
          )
      is AsyncImagePainter.State.Success -> Unit
    }
  }
}

/** Trash button over the card photo. */
@Composable
private fun DeleteButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
  IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier =
          modifier
              .size(36.dp)
              .background(MaterialTheme.photoButtonContainer, CircleShape)
              .semantics { testTag = C.Tag.collection_card_delete },
  ) {
    Icon(
        imageVector = Icons.Filled.Delete,
        contentDescription = stringResource(R.string.collection_delete),
        tint = MaterialTheme.accentIcon,
        modifier = Modifier.size(20.dp),
    )
  }
}

/** Heart over the card photo: filled when [isFavorite], outlined otherwise. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier
              .size(36.dp)
              .background(MaterialTheme.photoButtonContainer, CircleShape)
              .semantics { testTag = C.Tag.collection_card_favorite },
  ) {
    Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription =
            stringResource(
                if (isFavorite) R.string.collection_favorite_remove
                else R.string.collection_favorite_add
            ),
        tint = MaterialTheme.accentIcon,
        modifier = Modifier.size(20.dp),
    )
  }
}

private fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

@Composable
private fun PhotoPlaceholder(modifier: Modifier, tag: String) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          modifier
              .background(
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface
                  else CollectionPlaceholder
              )
              .semantics { testTag = tag },
  ) {
    Icon(
        imageVector = Icons.Filled.AccountBalance,
        contentDescription = null,
        tint =
            if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
            else CollectionPlaceholderIcon,
        modifier = Modifier.size(40.dp),
    )
  }
}

@Composable
private fun CollectionLoading() {
  Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { testTag = C.Tag.collection_loading },
    )
  }
}

@Composable
private fun CollectionMessage(tag: String, content: @Composable () -> Unit) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize().padding(32.dp).semantics { testTag = tag },
  ) {
    content()
  }
}

@Composable
private fun CollectionEmpty(readOnly: Boolean = false) {
  Icon(
      imageVector = Icons.Filled.AccountBalance,
      contentDescription = null,
      tint =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
          else CollectionPlaceholderIcon,
      modifier = Modifier.size(56.dp),
  )
  Text(
      text =
          stringResource(
              if (readOnly) R.string.friend_collection_empty_title
              else R.string.collection_empty_title
          ),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onBackground else LoadingTextPrimary,
      modifier = Modifier.padding(top = 16.dp),
  )
  Text(
      text =
          stringResource(
              if (readOnly) R.string.friend_collection_empty_subtitle
              else R.string.collection_empty_subtitle
          ),
      style = MaterialTheme.typography.bodySmall,
      color =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
          else LoadingTextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun FavoritesEmpty() {
  Icon(
      imageVector = Icons.Outlined.FavoriteBorder,
      contentDescription = null,
      tint =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
          else CollectionPlaceholderIcon,
      modifier = Modifier.size(56.dp),
  )
  Text(
      text = stringResource(R.string.collection_favorites_empty_title),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onBackground else LoadingTextPrimary,
      modifier = Modifier.padding(top = 16.dp),
  )
  Text(
      text = stringResource(R.string.collection_favorites_empty_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
          else LoadingTextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun CollectionSearchNoMatches() {
  Icon(
      imageVector = Icons.Default.Search,
      contentDescription = null,
      tint =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
          else CollectionPlaceholderIcon,
      modifier = Modifier.size(56.dp),
  )
  Text(
      text = stringResource(R.string.collection_search_no_matches_title),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onBackground else LoadingTextPrimary,
      modifier = Modifier.padding(top = 16.dp),
  )
  Text(
      text = stringResource(R.string.collection_search_no_matches_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color =
          if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
          else LoadingTextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun CollectionError(readOnly: Boolean = false) {
  Text(
      text =
          stringResource(
              if (readOnly) R.string.friend_collection_error else R.string.collection_error
          ),
      style = MaterialTheme.typography.bodySmall,
      color = if (MaterialTheme.isDark) MaterialTheme.colorScheme.error else LoadingErrorText,
      textAlign = TextAlign.Center,
  )
}

@Composable
private fun CollectionRetryError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.semantics { testTag = C.Tag.collection_retry_error },
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CollectionError(readOnly = true)
    Button(onClick = onRetry, modifier = Modifier.semantics { testTag = C.Tag.collection_retry }) {
      Text(stringResource(R.string.saved_monument_detail_retry))
    }
  }
}

private val discoveryDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

/** Formats [epochMillis] like "12 Oct 2026", in [zone] and [locale]. */
internal fun formatDiscoveryDate(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String =
    discoveryDateFormatter.withLocale(locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

private val levenshtein = LevenshteinDistance.getDefaultInstance()

internal fun matchesTier2Word(query: String, word: String): Boolean {
  val cleanQuery = query.trim().lowercase()
  if (cleanQuery.length < 3) return false
  if (cleanQuery.any { it.isDigit() }) return false
  val maxDistance = if (cleanQuery.length <= 4) 1 else 2
  val cleanWord = word.trim { !it.isLetterOrDigit() }.lowercase()
  if (cleanWord.isEmpty()) return false
  val distance = levenshtein.apply(cleanQuery, cleanWord)
  return distance in 0..maxDistance
}

private fun guideSearchableText(guide: Guide): String {
  val formattedDate = guide.discoveryDate?.let { formatDiscoveryDate(it) }.orEmpty()
  val joinedFacts = guide.facts.joinToString(" ")
  return listOf(guide.monumentName, formattedDate, guide.history, guide.culture, joinedFacts)
      .joinToString(" ")
}

private fun matchesGuide(guide: Guide, query: String): Boolean {
  if (query.isBlank()) return true
  val searchableText = guideSearchableText(guide)
  val trimmedQuery = query.trim()
  if (searchableText.contains(trimmedQuery, ignoreCase = true)) return true
  val words = searchableText.split("\\s+".toRegex())
  return words.any { matchesTier2Word(trimmedQuery, it) }
}

/** The guides to show: only favourites when [favoritesOnly], newest first. */
internal fun visibleGuides(
    guides: List<Guide>,
    favoritesOnly: Boolean,
    query: String = "",
): List<Guide> {
  val favoriteFiltered = if (favoritesOnly) guides.filter { it.isFavorite } else guides
  val queryFiltered =
      if (query.isBlank()) favoriteFiltered else favoriteFiltered.filter { matchesGuide(it, query) }
  return newestFirst(queryFiltered)
}

/** Sorts [guides] by discovery date, most recent first; guides without a date come last. */
internal fun newestFirst(guides: List<Guide>): List<Guide> =
    guides.sortedWith(compareByDescending(nullsFirst()) { it.discoveryDate })

private val previewGuides: List<Guide> =
    listOf("Eiffel Tower", "Colosseum", "Sagrada Família", "Big Ben").mapIndexed { index, name ->
      Guide(
          id = "preview-$index",
          photoBytes = ByteArray(0),
          monumentName = name,
          confidence = 0.9,
          history = "",
          facts = emptyList(),
          culture = "",
          discoveryDate = PREVIEW_LATEST_DISCOVERY - index * DAY_MILLIS,
      )
    }

/** 12 Oct 2026, 12:00 UTC. */
private const val PREVIEW_LATEST_DISCOVERY = 1_791_806_400_000L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private val previewSelectedSharing =
    MonumentShareSettings(audience = ShareAudience.SELECTED_FRIENDS)

private val previewSharingFriends =
    listOf(
        CollectionFriend("preview-alice", "Alice"),
        CollectionFriend("preview-bob", "Bob"),
        CollectionFriend("preview-charlie", "Charlie"),
    )

@Preview(showBackground = true)
@Composable
fun PreviewCollectionScreen() {
  MaterialTheme { CollectionScreen(state = SavedGuidesUiState(guides = previewGuides)) }
}

@Preview(showBackground = true)
@Composable
fun PreviewCollectionScreenEmpty() {
  MaterialTheme { CollectionScreen(state = SavedGuidesUiState()) }
}

@Preview(name = "Collection - Dark", showBackground = true)
@Composable
fun PreviewCollectionScreenDark() {
  SampleAppTheme(darkTheme = true, dynamicColor = false) {
    CollectionScreen(state = SavedGuidesUiState(guides = previewGuides))
  }
}

@Preview(name = "Collection Empty - Dark", showBackground = true)
@Composable
fun PreviewCollectionScreenEmptyDark() {
  SampleAppTheme(darkTheme = true, dynamicColor = false) {
    CollectionScreen(state = SavedGuidesUiState())
  }
}

@Preview(name = "Sharing - Select friends", showBackground = true)
@Composable
fun PreviewCollectionSharingSelection() {
  SampleAppTheme {
    GuideSharingDialog(
        guide =
            previewGuides
                .first()
                .copy(
                    shareSettings =
                        MonumentShareSettings(
                            audience = ShareAudience.SELECTED_FRIENDS,
                            sharedWithUids = setOf("preview-alice"),
                        )
                ),
        friends = previewSharingFriends,
        isLoadingFriends = false,
        friendsError = null,
        isSaving = false,
        onRetryLoadingFriends = {},
        onDismiss = {},
        onConfirm = { _, _ -> },
    )
  }
}

@Preview(name = "Sharing - Loading friends", showBackground = true)
@Composable
fun PreviewCollectionSharingLoading() {
  SampleAppTheme {
    GuideSharingDialog(
        guide = previewGuides.first().copy(shareSettings = previewSelectedSharing),
        friends = emptyList(),
        isLoadingFriends = true,
        friendsError = null,
        isSaving = false,
        onRetryLoadingFriends = {},
        onDismiss = {},
        onConfirm = { _, _ -> },
    )
  }
}

@Preview(name = "Sharing - Friends unavailable", showBackground = true)
@Composable
fun PreviewCollectionSharingFriendsError() {
  SampleAppTheme {
    GuideSharingDialog(
        guide = previewGuides.first().copy(shareSettings = previewSelectedSharing),
        friends = emptyList(),
        isLoadingFriends = false,
        friendsError = IllegalStateException(),
        isSaving = false,
        onRetryLoadingFriends = {},
        onDismiss = {},
        onConfirm = { _, _ -> },
    )
  }
}

@Preview(name = "Sharing - No friends", showBackground = true)
@Composable
fun PreviewCollectionSharingNoFriends() {
  SampleAppTheme {
    GuideSharingDialog(
        guide = previewGuides.first().copy(shareSettings = previewSelectedSharing),
        friends = emptyList(),
        isLoadingFriends = false,
        friendsError = null,
        isSaving = false,
        onRetryLoadingFriends = {},
        onDismiss = {},
        onConfirm = { _, _ -> },
    )
  }
}

@Preview(name = "Sharing - Saved", showBackground = true)
@Composable
fun PreviewCollectionSharingSaved() {
  SampleAppTheme {
    CollectionScreen(
        state =
            SavedGuidesUiState(
                guides =
                    listOf(
                        previewGuides
                            .first()
                            .copy(
                                shareSettings =
                                    MonumentShareSettings(
                                        audience = ShareAudience.SELECTED_FRIENDS,
                                        sharedWithUids = setOf("preview-alice", "preview-bob"),
                                    )
                            )
                    )
            )
    )
  }
}
