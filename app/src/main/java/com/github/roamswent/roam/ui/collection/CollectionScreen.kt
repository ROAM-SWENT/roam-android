// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>
// Co-authored-by: Google Antigravity <noreply@google.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.github.roamswent.roam.model.guide.SharingEligibility
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.components.ProfileBubble
import com.github.roamswent.roam.ui.components.ProfilePicture
import com.github.roamswent.roam.ui.friends.FriendsSearchField
import com.github.roamswent.roam.ui.friends.filterByUsernameQuery
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
import com.github.roamswent.roam.ui.theme.guideCalloutContainer
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
    isGuideWishlisted: (Guide) -> Boolean = { false },
    onToggleWishlist: ((Guide) -> Unit)? = null,
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
    wishlistState: WishlistUiState = WishlistUiState(),
    onSetWishlisted: (String, Boolean) -> Unit = { _, _ -> },
    onRetryWishlist: () -> Unit = {},
    initialShowWishlist: Boolean = false,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showWishlist by rememberSaveable { mutableStateOf(initialShowWishlist) }
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
      contentWindowInsets =
          ScaffoldDefaults.contentWindowInsets.only(
              WindowInsetsSides.Top + WindowInsetsSides.Horizontal
          ),
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
      if (
          state.guides.isNotEmpty() ||
              (!readOnly && (showWishlist || wishlistState.monuments.isNotEmpty()))
      ) {
        CollectionSearchBar(
            query = state.searchQuery,
            onQueryChange = onSearchQueryChange,
            readOnly = readOnly,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
      }
      if (!readOnly) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
          CollectionFilterChips(
              favoritesOnly = state.showFavoritesOnly,
              showWishlist = showWishlist,
              onChange = {
                showWishlist = false
                onShowFavoritesOnlyChange(it)
              },
              onWishlist = { showWishlist = true },
              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
          )
          CollectionGalleryButton(onPickFromGallery)
        }
      }
      if (state.guides.isNotEmpty() && state.guidesError != null && onRetryGuides != null) {
        CollectionRetryError(onRetryGuides, Modifier.padding(horizontal = 20.dp))
      }
      when {
        !readOnly && showWishlist -> {
          WishlistContent(
              wishlistState = wishlistState,
              query = state.searchQuery,
              onSetWishlisted = onSetWishlisted,
              onRetry = onRetryWishlist,
          )
        }
        state.guides.isNotEmpty() -> {
          val guides = visibleGuides(state.guides, state.showFavoritesOnly, state.searchQuery)
          when {
            guides.isEmpty() && state.searchQuery.isNotBlank() -> {
              CollectionMessage(C.Tag.collection_search_no_matches) { CollectionSearchNoMatches() }
            }
            guides.isEmpty() && state.showFavoritesOnly -> {
              CollectionMessage(C.Tag.collection_favorites_empty) { FavoritesEmpty() }
            }
            guides.isEmpty() -> {
              CollectionMessage(C.Tag.collection_empty_state) { CollectionEmpty() }
            }
            else -> {
              GuideGrid(
                  guides = guides,
                  onGuideClick = onGuideClick,
                  onDeleteGuide = onDeleteGuide,
                  deletingGuideIds = state.deletingGuideIds,
                  onToggleFavorite = toggleFavorite,
                  readOnly = readOnly,
                  isGuideWishlisted = isGuideWishlisted,
                  onToggleWishlist = onToggleWishlist,
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
        state.showFavoritesOnly && !readOnly ->
            CollectionMessage(C.Tag.collection_favorites_empty) { FavoritesEmpty() }
        else -> {
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

/** The collection filters, including the separate wishlist. */
@Composable
private fun CollectionFilterChips(
    favoritesOnly: Boolean,
    showWishlist: Boolean,
    onChange: (Boolean) -> Unit,
    onWishlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CollectionFilterChip(
        label = stringResource(R.string.collection_filter_all),
        selected = !favoritesOnly && !showWishlist,
        onClick = { onChange(false) },
        tag = C.Tag.collection_filter_all,
    )
    CollectionFilterChip(
        label = stringResource(R.string.collection_filter_favorites),
        selected = favoritesOnly && !showWishlist,
        onClick = { onChange(true) },
        tag = C.Tag.collection_filter_favorites,
        icon = Icons.Outlined.FavoriteBorder,
    )
    CollectionFilterChip(
        label = stringResource(R.string.collection_filter_wishlist),
        selected = showWishlist,
        onClick = onWishlist,
        tag = C.Tag.collection_filter_wishlist,
        icon = Icons.Outlined.BookmarkBorder,
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
    icon: ImageVector? = null,
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
          if (icon != null) {
            {
              Icon(
                  imageVector = icon,
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
private fun WishlistContent(
    wishlistState: WishlistUiState,
    query: String,
    onSetWishlisted: (String, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
  val monuments = wishlistState.monuments
  val matches = monuments.filter { monument ->
    matchesText(listOf(monument.name, monument.locationLabel()).joinToString(" "), query)
  }
  Column {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(MaterialTheme.guideCalloutContainer, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .semantics { testTag = C.Tag.collection_wishlist_info },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = MaterialTheme.accentIcon,
          modifier = Modifier.size(20.dp),
      )
      Text(
          text = stringResource(R.string.collection_wishlist_info),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.secondaryText,
      )
    }
    if (monuments.isEmpty() && wishlistState.isLoading) {
      CollectionLoading()
    } else if (monuments.isEmpty() && wishlistState.error != null) {
      CollectionMessage(C.Tag.collection_error) { CollectionRetryError(onRetry) }
    } else if (matches.isEmpty()) {
      CollectionMessage(
          if (query.isNotBlank()) C.Tag.collection_search_no_matches
          else C.Tag.collection_wishlist_empty
      ) {
        if (query.isNotBlank()) CollectionSearchNoMatches()
        else
            Text(
                text = stringResource(R.string.collection_wishlist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.secondaryText,
                textAlign = TextAlign.Center,
            )
      }
    } else {
      LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(matches, key = { it.id }) { monument ->
          WishlistCard(monument, monument.isWishlisted) {
            onSetWishlisted(monument.id, !monument.isWishlisted)
          }
        }
      }
    }
  }
}

@Composable
private fun WishlistCard(
    monument: WishlistMonument,
    isWishlisted: Boolean,
    onToggleWishlist: () -> Unit,
) {
  Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.cardContainer),
      modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.collection_wishlist_card },
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
      WishlistPhoto(monument)
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = monument.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val location = monument.locationLabel()
        if (location.isNotEmpty()) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.secondaryText,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (monument.scannedBy.isNotEmpty()) {
          val scannedBy =
              if (monument.scannedBy.size == 1) {
                stringResource(
                    R.string.collection_wishlist_scanned_by,
                    monument.scannedBy.first().name,
                )
              } else {
                pluralStringResource(
                    R.plurals.collection_wishlist_scanned_by_friends,
                    monument.scannedBy.size,
                    monument.scannedBy.size,
                )
              }
          Row(
              modifier = Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) {},
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
              monument.scannedBy.take(3).forEach { friend -> WishlistFriendAvatar(friend) }
            }
            if (monument.scannedBy.size > 3) {
              Text(
                  text =
                      stringResource(
                          R.string.collection_wishlist_more_friends,
                          monument.scannedBy.size - 3,
                      ),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.accentIcon,
                  modifier =
                      Modifier.semantics { testTag = C.Tag.collection_wishlist_more_friends },
              )
            }
            Text(
                text = scannedBy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      WishlistButton(isWishlisted, onToggleWishlist, Modifier)
    }
  }
}

@Composable
private fun WishlistFriendAvatar(friend: WishlistFriend) {
  Box(modifier = Modifier.size(24.dp).semantics { testTag = C.Tag.collection_wishlist_avatar }) {
    ProfilePicture(
        name = friend.name,
        modifier = Modifier.fillMaxSize(),
        textStyle = MaterialTheme.typography.labelSmall,
    )
    if (friend.avatarUrl != null) {
      AsyncImage(
          model = friend.avatarUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize().clip(CircleShape),
      )
    }
  }
}

private fun WishlistMonument.locationLabel(): String =
    listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")

@Composable
private fun WishlistPhoto(monument: WishlistMonument) {
  val modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp))
  val photoUrl = monument.photoUrl
  if (photoUrl.isNullOrBlank()) {
    PhotoPlaceholder(modifier, C.Tag.collection_card_photo_placeholder)
    return
  }
  var photoState by
      remember(photoUrl) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    AsyncImage(
        model = photoUrl,
        contentDescription = stringResource(R.string.collection_photo_description, monument.name),
        contentScale = ContentScale.Crop,
        onState = { photoState = it },
        modifier =
            Modifier.matchParentSize().semantics { testTag = C.Tag.collection_wishlist_photo },
    )
    when (photoState) {
      is AsyncImagePainter.State.Error ->
          PhotoPlaceholder(Modifier.matchParentSize(), C.Tag.collection_card_photo_error)
      is AsyncImagePainter.State.Empty,
      is AsyncImagePainter.State.Loading ->
          CircularProgressIndicator(modifier = Modifier.size(24.dp))
      is AsyncImagePainter.State.Success -> Unit
    }
  }
}

@Composable
private fun WishlistButton(
    isWishlisted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = C.Tag.collection_wishlist_button,
) {
  IconButton(
      onClick = onClick,
      modifier = modifier.size(36.dp).semantics { testTag = tag },
  ) {
    Icon(
        imageVector = if (isWishlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription =
            stringResource(
                if (isWishlisted) R.string.collection_wishlist_remove
                else R.string.collection_wishlist_add
            ),
        tint = MaterialTheme.accentIcon,
        modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun GuideGrid(
    guides: List<Guide>,
    onGuideClick: (String) -> Unit,
    onDeleteGuide: (String) -> Unit,
    deletingGuideIds: Set<String>,
    onToggleFavorite: (Guide) -> Unit,
    readOnly: Boolean,
    isGuideWishlisted: (Guide) -> Boolean,
    onToggleWishlist: ((Guide) -> Unit)?,
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
          isWishlisted = isGuideWishlisted(guide),
          onToggleWishlist = onToggleWishlist?.let { toggle -> { toggle(guide) } },
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
    isWishlisted: Boolean,
    onToggleWishlist: (() -> Unit)?,
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
      if (readOnly && onToggleWishlist != null) {
        WishlistButton(
            isWishlisted = isWishlisted,
            onClick = onToggleWishlist,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(MaterialTheme.photoButtonContainer, CircleShape),
            tag = C.Tag.collection_card_wishlist,
        )
      }
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
        if (guide.sharingEligibility == SharingEligibility.TOO_FAR) {
          UnavailableShareButton(
              modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
          )
        } else {
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
        guideId = guide.id,
        monumentName = guide.monumentName,
        shareSettings = guide.shareSettings,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnavailableShareButton(modifier: Modifier = Modifier) {
  val tooltipState = rememberTooltipState()
  val scope = rememberCoroutineScope()
  Box(modifier = modifier) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
          PlainTooltip { Text(stringResource(R.string.collection_share_unavailable_message)) }
        },
        state = tooltipState,
    ) {
      IconButton(
          onClick = { scope.launch { tooltipState.show() } },
          modifier =
              Modifier.size(36.dp)
                  .background(MaterialTheme.photoButtonContainer, CircleShape)
                  .semantics { testTag = C.Tag.collection_card_share },
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
          Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = stringResource(R.string.collection_share_unavailable_button),
              tint = MaterialTheme.accentIcon.copy(alpha = 0.45f),
              modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuideSharingDialog(
    guideId: String,
    monumentName: String,
    shareSettings: MonumentShareSettings,
    friends: List<CollectionFriend>,
    isLoadingFriends: Boolean,
    friendsError: Exception?,
    isSaving: Boolean,
    onRetryLoadingFriends: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (ShareAudience, Set<String>) -> Unit,
) {
  var audience by remember(guideId) { mutableStateOf(shareSettings.audience) }
  val selectedFriendUids =
      remember(guideId) {
        mutableStateSetOf<String>().apply { addAll(shareSettings.sharedWithUids) }
      }
  var friendsQuery by remember(guideId) { mutableStateOf("") }
  val filteredFriends =
      remember(friends, friendsQuery) {
        filterByUsernameQuery(friends, friendsQuery, CollectionFriend::username)
      }
  val selectedRecipients =
      selectedFriendUids.filter { selectedUid -> friends.any { it.uid == selectedUid } }.toSet()
  val selectedCount =
      if (audience == ShareAudience.ALL_FRIENDS) friends.size else selectedRecipients.size
  val canShare =
      !isSaving &&
          (audience != ShareAudience.SELECTED_FRIENDS ||
              (!isLoadingFriends && friendsError == null && selectedRecipients.isNotEmpty()))
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
      modifier = Modifier.semantics { testTag = C.Tag.collection_share_dialog },
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = stringResource(R.string.collection_share_new_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = stringResource(R.string.collection_share_new_message, monumentName),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.secondaryText,
      )
      ShareAudienceOption(
          label = stringResource(R.string.collection_share_new_none),
          icon = { Icon(Icons.Default.Lock, contentDescription = null) },
          selected = audience == ShareAudience.NONE,
          tag = C.Tag.collection_share_audience_none,
          onSelect = {
            audience = ShareAudience.NONE
            selectedFriendUids.clear()
          },
      )
      ShareAudienceOption(
          label = stringResource(R.string.collection_share_new_all_friends),
          icon = { Icon(Icons.Default.Group, contentDescription = null) },
          selected = audience == ShareAudience.ALL_FRIENDS,
          tag = C.Tag.collection_share_audience_all_friends,
          onSelect = {
            audience = ShareAudience.ALL_FRIENDS
            selectedFriendUids.clear()
            selectedFriendUids.addAll(friends.map(CollectionFriend::uid))
          },
      )
      FriendsSearchField(
          query = friendsQuery,
          onQueryChanged = { friendsQuery = it },
          modifier = Modifier.fillMaxWidth(),
      )
      when {
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
        filteredFriends.isEmpty() -> {
          Text(
              text =
                  if (friends.isEmpty()) {
                    stringResource(R.string.collection_share_no_friends)
                  } else {
                    stringResource(R.string.friends_no_users_found)
                  },
              modifier = Modifier.padding(vertical = 12.dp),
          )
        }
        else -> {
          val friendListState = rememberLazyListState()
          val friendListNestedScrollConnection =
              remember(friendListState) {
                object : NestedScrollConnection {
                  override fun onPreScroll(
                      available: Offset,
                      source: NestedScrollSource,
                  ): Offset {
                    return consumeFriendListPreScroll(
                        available = available,
                        source = source,
                        canScrollBackward = friendListState.canScrollBackward,
                    )
                  }
                }
              }
          LazyColumn(
              modifier =
                  Modifier.fillMaxWidth()
                      .heightIn(max = 280.dp)
                      .nestedScroll(friendListNestedScrollConnection),
              state = friendListState,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(filteredFriends, key = CollectionFriend::uid) { friend ->
              ShareFriendRow(
                  friend = friend,
                  selected =
                      audience == ShareAudience.ALL_FRIENDS || friend.uid in selectedFriendUids,
                  onSelectedChange = { checked ->
                    if (checked) {
                      selectedFriendUids.add(friend.uid)
                      audience =
                          if (friends.all { it.uid in selectedFriendUids }) {
                            ShareAudience.ALL_FRIENDS
                          } else {
                            ShareAudience.SELECTED_FRIENDS
                          }
                    } else {
                      if (audience == ShareAudience.ALL_FRIENDS) {
                        selectedFriendUids.clear()
                        selectedFriendUids.addAll(friends.map(CollectionFriend::uid))
                      }
                      selectedFriendUids.remove(friend.uid)
                      if (selectedFriendUids.none { uid -> friends.any { it.uid == uid } }) {
                        audience = ShareAudience.NONE
                      } else {
                        audience = ShareAudience.SELECTED_FRIENDS
                      }
                    }
                  },
              )
            }
          }
        }
      }
      Spacer(Modifier.weight(1f, fill = false))
      Text(
          text =
              pluralStringResource(
                  R.plurals.collection_share_selected_friends,
                  selectedCount,
                  selectedCount,
              ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.secondaryText,
          modifier = Modifier.align(Alignment.End),
      )
      Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
            onClick = onDismiss,
            enabled = !isSaving,
            modifier = Modifier.weight(1f).semantics { testTag = C.Tag.collection_share_cancel },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.cardContainer,
                    contentColor = MaterialTheme.accentIcon,
                ),
        ) {
          Text(stringResource(R.string.collection_share_cancel))
        }
        Button(
            onClick = {
              onConfirm(
                  audience,
                  if (audience == ShareAudience.SELECTED_FRIENDS) selectedRecipients
                  else emptySet(),
              )
            },
            enabled = canShare,
            modifier = Modifier.weight(1f).semantics { testTag = C.Tag.collection_share_confirm },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.accentContainer,
                    contentColor = MaterialTheme.onAccentContainer,
                ),
        ) {
          Text(stringResource(R.string.collection_share_new_confirm))
        }
      }
    }
  }
}

internal fun consumeFriendListPreScroll(
    available: Offset,
    source: NestedScrollSource,
    canScrollBackward: Boolean,
): Offset =
    if (source == NestedScrollSource.UserInput && available.y > 0f && !canScrollBackward) {
      Offset(x = 0f, y = available.y)
    } else {
      Offset.Zero
    }

@Composable
private fun ShareAudienceOption(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
              .semantics { testTag = tag },
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.cardContainer,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      icon()
      Text(
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f).padding(start = 12.dp),
      )
      RadioButton(selected = selected, onClick = null)
    }
  }
}

@Composable
private fun ShareFriendRow(
    friend: CollectionFriend,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .toggleable(
                  value = selected,
                  role = Role.Checkbox,
                  onValueChange = onSelectedChange,
              )
              .semantics { testTag = C.Tag.collection_share_friend },
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ProfilePicture(
        name = friend.username,
        avatarBytes = friend.avatarBytes,
        modifier = Modifier.size(48.dp),
    )
    Text(
        text = friend.username,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f).padding(start = 12.dp),
    )
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.accentContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.onAccentContainer,
        border =
            if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      if (selected) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
      }
    }
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

/** Whether [text] contains [query], or a word of it is within typo distance of [query]. */
internal fun matchesText(text: String, query: String): Boolean {
  if (query.isBlank()) return true
  val trimmedQuery = query.trim()
  if (text.contains(trimmedQuery, ignoreCase = true)) return true
  return text.split("\\s+".toRegex()).any { matchesTier2Word(trimmedQuery, it) }
}

private fun matchesGuide(guide: Guide, query: String): Boolean =
    matchesText(guideSearchableText(guide), query)

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
        guideId = previewGuides.first().id,
        monumentName = previewGuides.first().monumentName,
        shareSettings =
            MonumentShareSettings(
                audience = ShareAudience.SELECTED_FRIENDS,
                sharedWithUids = setOf("preview-alice"),
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
        guideId = previewGuides.first().id,
        monumentName = previewGuides.first().monumentName,
        shareSettings = previewSelectedSharing,
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
        guideId = previewGuides.first().id,
        monumentName = previewGuides.first().monumentName,
        shareSettings = previewSelectedSharing,
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
        guideId = previewGuides.first().id,
        monumentName = previewGuides.first().monumentName,
        shareSettings = previewSelectedSharing,
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
