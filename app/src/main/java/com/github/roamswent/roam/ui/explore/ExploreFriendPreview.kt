// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.location.MonumentLocality
import com.github.roamswent.roam.model.location.resolveMonumentLocality
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.wishlist.WishlistMonumentKey
import com.github.roamswent.roam.ui.components.ProfilePicture
import com.github.roamswent.roam.ui.theme.OnBrand
import com.github.roamswent.roam.ui.theme.accentIcon
import com.github.roamswent.roam.ui.theme.isDark
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
internal fun ExploreFriendPreview(
    group: ExploreFriendPinGroup,
    friends: List<ExploreFriendMonuments>,
    mapArtwork: Map<String, FriendMarkerArtwork>,
    viewModel: ExploreViewModel,
    onDismiss: () -> Unit,
    onGuideClick: (ExploreSharedPin) -> Unit,
    modifier: Modifier = Modifier,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  var hasExpanded by rememberSaveable { mutableStateOf(false) }
  val first = group.first
  val monument = first.monument
  val photoMonument =
      remember(group) {
        group.pins.firstOrNull { it.monument.photoBytes.isNotEmpty() }?.monument ?: monument
      }
  val profiles = remember(friends) { friends.associateBy { it.ownerUid } }
  val ownerPositions =
      remember(group) { group.ownerUids.withIndex().associate { it.value to it.index + 1 } }
  val context = LocalContext.current
  val locality by
      produceState<MonumentLocality?>(null, monument.coordinates, context) {
        value = resolveMonumentLocality(context, monument.coordinates)
      }
  val monumentKey =
      remember(monument.coordinates) { WishlistMonumentKey.fromCoordinates(monument.coordinates) }
  var wishlistCheckAttempt by remember(monumentKey) { mutableStateOf(0) }
  val wishlistStatus by
      key(monumentKey, wishlistCheckAttempt) {
        produceState(ExploreWishlistStatus.LOADING) { value = viewModel.checkWishlist(monumentKey) }
      }
  val write by viewModel.wishlistWrite.collectAsStateWithLifecycle()
  val selectedWriteStatus = write?.takeIf { it.key == monumentKey }?.status
  val saved =
      wishlistStatus == ExploreWishlistStatus.SAVED ||
          selectedWriteStatus == ExploreWishlistWrite.Status.SAVED
  val canAdd =
      wishlistStatus == ExploreWishlistStatus.NOT_SAVED &&
          !saved &&
          write?.status != ExploreWishlistWrite.Status.PENDING
  val wishlistLabel =
      stringResource(
          if (saved) R.string.explore_friend_wishlist_saved
          else R.string.explore_friend_wishlist_add
      )
  val wishlistingFriends by
      produceState(emptyList<PublicProfile>(), monumentKey, hasExpanded) {
        if (hasExpanded) value = viewModel.friendsWhoWishlisted(monumentKey)
      }
  val wishlistFailed = selectedWriteStatus == ExploreWishlistWrite.Status.FAILED && !saved
  val friendCount = group.ownerUids.size
  val countLabel =
      pluralStringResource(R.plurals.explore_friend_scanned_count, friendCount, friendCount)
  val viewGuide = stringResource(R.string.explore_friend_view_guide)
  val onViewGuide = { onGuideClick(first) }
  val onAdd = { viewModel.addToWishlist(first) }

  ExplorePreviewSurface(
      modifier =
          modifier.padding(
              top = if (expanded) EXPLORE_CARD_DOCK_CLEARANCE else 0.dp,
              bottom = EXPLORE_CARD_DOCK_CLEARANCE,
          ),
      isExpanded = expanded,
      expandedHeightFraction = 1f,
      onExpand = {
        expanded = true
        hasExpanded = true
      },
  ) {
    if (expanded) {
      Column(
          modifier =
              Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(EXPLORE_PREVIEW_PADDING),
          verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          PreviewTitle(monument.name, Modifier.weight(1f))
          IconButton(onClick = { expanded = false }) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                stringResource(R.string.explore_minimize_monument_description),
                tint = MaterialTheme.accentIcon,
            )
          }
        }
        ExploreMonumentPhoto(
            photoMonument,
            Modifier.fillMaxWidth().height(EXPLORE_PREVIEW_PHOTO_HEIGHT),
        )
        PreviewLocation(locality)
        Text(countLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        for (pin in group.pins) {
          val friend = profiles[pin.ownerUid]
          FriendScanRow(
              friend?.profile,
              ownerPositions.getValue(pin.ownerUid),
              friend?.scanDates?.get(pin.monument.guideId),
          ) {
            onGuideClick(pin)
          }
        }
        HorizontalDivider()
        if (wishlistingFriends.isNotEmpty()) {
          WishlistAttribution(wishlistingFriends, profiles, mapArtwork)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP)) {
          OutlinedButton(
              onClick = onViewGuide,
              modifier = Modifier.weight(1f),
              border = BorderStroke(PREVIEW_ACTION_BORDER, MaterialTheme.accentIcon),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.accentIcon),
          ) {
            Text(viewGuide)
          }
          Button(
              onClick = onAdd,
              enabled = canAdd,
              modifier = Modifier.weight(1f),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.accentIcon,
                      contentColor =
                          if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface else OnBrand,
                  ),
          ) {
            Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
            Text(wishlistLabel, modifier = Modifier.padding(start = PREVIEW_INLINE_GAP))
          }
        }
        WishlistFeedback(wishlistStatus, saved, wishlistFailed) { wishlistCheckAttempt++ }
        Row(horizontalArrangement = Arrangement.spacedBy(PREVIEW_INLINE_GAP)) {
          Icon(
              Icons.Outlined.Info,
              contentDescription = null,
              modifier = Modifier.size(PREVIEW_INLINE_ICON_SIZE),
              tint = MaterialTheme.accentIcon,
          )
          Text(
              stringResource(R.string.explore_friend_collection_info),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      Column(Modifier.fillMaxWidth().padding(EXPLORE_PREVIEW_PADDING)) {
        Row(horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP)) {
          ExploreMonumentPhoto(photoMonument, Modifier.size(EXPLORE_PREVIEW_THUMBNAIL_SIZE))
          Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(PREVIEW_INLINE_GAP),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              PreviewTitle(monument.name, Modifier.weight(1f))
              IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.explore_close_monument_description),
                    tint = MaterialTheme.accentIcon,
                )
              }
            }
            val profile = profiles[first.ownerUid]?.profile
            val name = previewFriendName(profile, 1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PREVIEW_INLINE_GAP),
            ) {
              PreviewAvatarStack(
                  group.visibleOwnerUids.map(mapArtwork::getValue),
                  group.additionalOwnerCount,
              )
              Text(
                  if (friendCount == 1) stringResource(R.string.explore_friend_scanned_by, name)
                  else countLabel,
                  style = MaterialTheme.typography.bodySmall,
              )
            }
            PreviewLocation(locality)
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) { GuideAction(viewGuide, onClick = onViewGuide) }
          IconButton(
              onClick = onAdd,
              enabled = canAdd,
              colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.accentIcon),
          ) {
            Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, wishlistLabel)
          }
        }
        WishlistFeedback(wishlistStatus, saved, wishlistFailed) { wishlistCheckAttempt++ }
      }
    }
  }
}

@Composable
private fun WishlistAttribution(
    friends: List<PublicProfile>,
    mapFriends: Map<String, ExploreFriendMonuments>,
    mapArtwork: Map<String, FriendMarkerArtwork>,
) {
  val avatars = mutableListOf<FriendMarkerArtwork>()
  for (friend in friends.take(MAX_STACKED_AVATARS)) {
    key(friend.uid) {
      val mapAvatar = mapArtwork[friend.uid]
      avatars +=
          if (mapAvatar != null && mapFriends[friend.uid]?.profile == friend) mapAvatar
          else rememberFriendMarkerArtwork(friend)
    }
  }
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PREVIEW_INLINE_GAP),
  ) {
    PreviewAvatarStack(avatars, friends.size - avatars.size, useWishlistColors = true)
    Text(
        stringResource(R.string.explore_friend_wishlisted_by, friends.joinToString { it.username }),
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun FriendScanRow(
    profile: PublicProfile?,
    position: Int,
    scanDate: Long?,
    onGuideClick: () -> Unit,
) {
  val name = previewFriendName(profile, position)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP),
  ) {
    PreviewAvatar(name, profile)
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
      if (scanDate != null) {
        Text(
            text = remember(scanDate) { relativeScanDate(scanDate) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    GuideAction(
        stringResource(R.string.explore_friend_read_guide),
        Modifier.weight(1f),
        onGuideClick,
    )
  }
}

private fun relativeScanDate(timestamp: Long): String {
  val zone = ZoneId.systemDefault()
  val today = LocalDate.now(zone)
  val scanDay = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
  val formatter = RelativeDateTimeFormatter.getInstance()
  val years = ChronoUnit.YEARS.between(today, scanDay)
  val months = ChronoUnit.MONTHS.between(today, scanDay)
  val weeks = ChronoUnit.WEEKS.between(today, scanDay)
  return when {
    years != 0L -> formatter.format(years.toDouble(), RelativeDateTimeUnit.YEAR)
    months != 0L -> formatter.format(months.toDouble(), RelativeDateTimeUnit.MONTH)
    weeks != 0L -> formatter.format(weeks.toDouble(), RelativeDateTimeUnit.WEEK)
    else ->
        formatter.format(
            ChronoUnit.DAYS.between(today, scanDay).toDouble(),
            RelativeDateTimeUnit.DAY,
        )
  }
}

@Composable
private fun WishlistFeedback(
    status: ExploreWishlistStatus,
    saved: Boolean,
    writeFailed: Boolean,
    onRetry: () -> Unit,
) {
  if (!saved && status == ExploreWishlistStatus.CHECK_FAILED) {
    Column {
      Text(
          stringResource(R.string.explore_friend_wishlist_check_error),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
      )
      TextButton(
          onClick = onRetry,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.accentIcon),
      ) {
        Text(stringResource(R.string.explore_retry))
      }
    }
  } else if (writeFailed) {
    Text(
        stringResource(R.string.explore_friend_wishlist_error),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
  }
}

@Composable
private fun PreviewTitle(title: String, modifier: Modifier = Modifier) {
  Text(
      title,
      modifier = modifier,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun PreviewLocation(locality: MonumentLocality?) {
  val city = locality?.city?.takeIf { it.isNotBlank() }
  val country = locality?.country?.takeIf { it.isNotBlank() }
  val label =
      when {
        city != null && country != null ->
            stringResource(R.string.explore_friend_city_country, city, country)
        else -> city ?: country ?: stringResource(R.string.explore_friend_location_unavailable)
      }
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PREVIEW_INLINE_GAP),
  ) {
    Icon(
        Icons.Default.LocationOn,
        null,
        modifier = Modifier.size(PREVIEW_INLINE_ICON_SIZE),
        tint = MaterialTheme.accentIcon,
    )
    Text(label, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun previewFriendName(profile: PublicProfile?, position: Int): String =
    profile?.username?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.explore_friend_pin_unknown_owner, position)

@Composable
private fun PreviewAvatar(
    name: String,
    profile: PublicProfile?,
) {
  ProfilePicture(
      name,
      profile?.avatarBytes,
      Modifier.size(FRIEND_AVATAR_SIZE),
      MaterialTheme.typography.labelSmall,
  )
}

@Composable
private fun PreviewAvatarStack(
    avatars: List<FriendMarkerArtwork>,
    extra: Int,
    useWishlistColors: Boolean = false,
) {
  val bitmap =
      rememberFriendAvatarStack(avatars, extra, COMPACT_AVATAR_SIZE, useWishlistColors) ?: return
  val image = remember(bitmap) { bitmap.asImageBitmap() }
  val width = with(LocalDensity.current) { bitmap.width.toDp() }
  Image(
      bitmap = image,
      contentDescription = null,
      modifier = Modifier.size(width, COMPACT_AVATAR_SIZE),
  )
}

@Composable
private fun GuideAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  TextButton(
      onClick = onClick,
      modifier = modifier,
      colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.accentIcon),
  ) {
    Text(label, modifier = Modifier.weight(1f, fill = false))
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
  }
}

private val PREVIEW_GAP = 12.dp
private val PREVIEW_INLINE_GAP = 4.dp
private val COMPACT_AVATAR_SIZE = 24.dp
private val FRIEND_AVATAR_SIZE = 36.dp
private val PREVIEW_INLINE_ICON_SIZE = 16.dp
private val PREVIEW_ACTION_BORDER = 1.dp
