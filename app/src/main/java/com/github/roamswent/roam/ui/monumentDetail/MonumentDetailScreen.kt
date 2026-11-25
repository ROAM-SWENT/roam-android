// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.guide.MonumentShareSettings
import com.github.roamswent.roam.model.guide.ShareAudience
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.collection.CollectionFriend
import com.github.roamswent.roam.ui.components.ProfilePicture
import com.github.roamswent.roam.ui.loading.ExploreOptInUiState
import com.github.roamswent.roam.ui.loading.ExploreUnavailableReason
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.accentContainer
import com.github.roamswent.roam.ui.theme.accentIcon
import com.github.roamswent.roam.ui.theme.guideCalloutContainer
import com.github.roamswent.roam.ui.theme.guidePanel
import com.github.roamswent.roam.ui.theme.isDark
import com.github.roamswent.roam.ui.theme.monumentShareUnavailableIcon
import com.github.roamswent.roam.ui.theme.monumentShareUnavailableText
import com.github.roamswent.roam.ui.theme.monumentShareUnavailableTitle
import com.github.roamswent.roam.ui.theme.offlineNoticeContainer
import com.github.roamswent.roam.ui.theme.onAccentContainer
import com.github.roamswent.roam.ui.theme.photoButtonContainer
import com.github.roamswent.roam.ui.theme.secondaryText

/** Sharing data rendered below a monument guide. */
data class MonumentDetailSharingState(
    /** Current audience and selected recipients. */
    val settings: MonumentShareSettings = MonumentShareSettings(),
    /** Friends whose names and avatars can be shown in the status panel. */
    val recipients: List<CollectionFriend> = emptyList(),
    /** Whether sharing is unavailable because the photo was too far from the monument. */
    val unavailableBecauseTooFar: Boolean = false,
    /** Whether a sharing write is currently in progress. */
    val isSaving: Boolean = false,
)

/**
 * Shows a scrolling [guide] beneath a fixed [image]. The favourite heart is shown when [isFavorite]
 * is not null, that is once the guide is in the collection, and [showFavoriteError] shows a
 * snackbar that is reported back with [onFavoriteErrorShown]. The Explore switch is shown only when
 * [exploreOptIn] is not null, and reports changes with [onShowInExploreChange]. Otherwise, when
 * [exploreUnavailableReason] is not null, a message explains why the guide cannot be put on
 * Explore. [isGalleryImage] words both for a gallery photo, located by its own metadata, rather
 * than for a scan located where the user stands.
 */
@Composable
fun MonumentDetailScreen(
    image: Painter,
    guide: MonumentGuide,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean? = null,
    onFavoriteClick: () -> Unit = {},
    showFavoriteError: Boolean = false,
    onFavoriteErrorShown: () -> Unit = {},
    exploreOptIn: ExploreOptInUiState? = null,
    onShowInExploreChange: (Boolean) -> Unit = {},
    exploreUnavailableReason: ExploreUnavailableReason? = null,
    isGalleryImage: Boolean = false,
    sharingState: MonumentDetailSharingState = MonumentDetailSharingState(),
    onShareClick: () -> Unit = {},
    showSharingError: Boolean = false,
    onSharingErrorShown: () -> Unit = {},
    showSharingControls: Boolean = true,
    showSharingAction: Boolean = true,
) {
  val cornerRadius = 24.dp

  BoxWithConstraints(
      modifier =
          modifier.fillMaxSize().semantics { testTag = C.Tag.monument_detail_screen_container }
  ) {
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val maximumPanelFraction = (1f - (topInset + 64.dp) / maxHeight).coerceIn(0.5f, 0.85f)
    val minimumPanelFraction = (200.dp / maxHeight).coerceIn(0.2f, maximumPanelFraction)
    val panelFraction = 0.65f.coerceIn(minimumPanelFraction, maximumPanelFraction)

    Image(
        painter = image,
        contentDescription = stringResource(R.string.monument_detail_picture_description),
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.fillMaxWidth()
                .height(maxHeight * (1f - panelFraction) + cornerRadius)
                .align(Alignment.TopCenter)
                .semantics { testTag = C.Tag.monument_detail_picture },
    )

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .fillMaxHeight(panelFraction)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                .background(MaterialTheme.guidePanel)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .semantics { testTag = C.Tag.monument_detail_panel },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
          modifier =
              Modifier.widthIn(max = 640.dp)
                  .weight(1f)
                  .fillMaxWidth()
                  .verticalScroll(state = rememberScrollState(), overscrollEffect = null)
                  .padding(horizontal = 24.dp, vertical = 24.dp)
                  .semantics { testTag = C.Tag.monument_detail_text },
          verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        GuideHeader(guide)
        if (exploreOptIn != null) {
          ShowInExploreRow(
              optIn = exploreOptIn,
              onCheckedChange = onShowInExploreChange,
              isGalleryImage = isGalleryImage,
          )
        } else if (
            exploreUnavailableReason != null &&
                !(exploreUnavailableReason == ExploreUnavailableReason.TOO_FAR &&
                    sharingState.unavailableBecauseTooFar)
        ) {
          ExploreUnavailableMessage(
              reason = exploreUnavailableReason,
              isGalleryImage = isGalleryImage,
          )
        }
        GuideContent(guide)
        if (showSharingControls) {
          MonumentDetailSharingStatus(
              state = sharingState,
              monumentName = guide.monumentName,
          )
        }
      }
      if (showSharingControls && showSharingAction) {
        MonumentDetailSharingButton(
            state = sharingState,
            onBackClick = onBackClick,
            onShareClick = onShareClick,
            modifier =
                Modifier.widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(
                        start = 24.dp,
                        top = 8.dp,
                        end = 24.dp,
                        bottom = 24.dp,
                    ),
        )
      }
    }

    MonumentDetailBackButton(
        onClick = onBackClick,
        modifier = Modifier.align(Alignment.TopStart),
    )

    isFavorite?.let {
      MonumentDetailFavoriteButton(
          isFavorite = it,
          onClick = onFavoriteClick,
          modifier = Modifier.align(Alignment.TopEnd),
      )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val favoriteErrorMessage = stringResource(R.string.collection_favorite_error)
    val sharingErrorMessage = stringResource(R.string.collection_share_error)
    LaunchedEffect(showFavoriteError) {
      if (showFavoriteError) {
        snackbarHostState.showSnackbar(favoriteErrorMessage)
        onFavoriteErrorShown()
      }
    }
    LaunchedEffect(showSharingError) {
      if (showSharingError) {
        snackbarHostState.showSnackbar(sharingErrorMessage)
        onSharingErrorShown()
      }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
    )
  }
}

/** Shows the current sharing audience at the bottom of the scrollable guide content. */
@Composable
private fun MonumentDetailSharingStatus(
    state: MonumentDetailSharingState,
    monumentName: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (!state.unavailableBecauseTooFar && state.settings.audience == ShareAudience.NONE) {
      Spacer(Modifier.height(8.dp))
    }

    when {
      state.unavailableBecauseTooFar -> {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.offlineNoticeContainer)
                    .padding(start = 12.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                    .semantics { testTag = C.Tag.monument_detail_share_unavailable },
            verticalAlignment = Alignment.Top,
        ) {
          Icon(
              imageVector = if (MaterialTheme.isDark) Icons.Filled.Info else Icons.Outlined.Info,
              contentDescription = null,
              tint = MaterialTheme.monumentShareUnavailableIcon,
          )
          Spacer(Modifier.width(10.dp))
          Column {
            Text(
                text = stringResource(R.string.monument_detail_share_unavailable_title),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.monumentShareUnavailableTitle,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    stringResource(
                        R.string.monument_detail_share_unavailable_message,
                        monumentName,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.monumentShareUnavailableText,
            )
          }
        }
      }
      state.settings.audience == ShareAudience.NONE -> {
        Row(
            modifier =
                Modifier.fillMaxWidth().semantics { testTag = C.Tag.monument_detail_share_status },
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = MaterialTheme.secondaryText,
          )
          Spacer(Modifier.width(8.dp))
          Text(
              text = stringResource(R.string.monument_detail_not_shared),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.secondaryText,
          )
        }
      }
      else -> {
        SharedRecipientsStatus(
            recipients = state.recipients,
            modifier = Modifier.semantics { testTag = C.Tag.monument_detail_share_status },
        )
      }
    }
  }
}

@Composable
private fun MonumentDetailSharingButton(
    state: MonumentDetailSharingState,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Button(
      onClick = if (state.unavailableBecauseTooFar) onBackClick else onShareClick,
      enabled = !state.isSaving,
      modifier =
          modifier.fillMaxWidth().heightIn(min = 52.dp).semantics {
            testTag = C.Tag.monument_detail_share_button
          },
      colors =
          ButtonDefaults.buttonColors(
              containerColor =
                  if (state.settings.audience == ShareAudience.NONE) {
                    MaterialTheme.accentContainer
                  } else {
                    MaterialTheme.guideCalloutContainer
                  },
              contentColor =
                  if (state.settings.audience == ShareAudience.NONE) {
                    MaterialTheme.onAccentContainer
                  } else {
                    MaterialTheme.accentIcon
                  },
          ),
  ) {
    if (!state.unavailableBecauseTooFar) {
      Icon(
          imageVector = Icons.Filled.Share,
          contentDescription = null,
      )
      Spacer(Modifier.width(16.dp))
    }
    Text(
        text =
            stringResource(
                when {
                  state.unavailableBecauseTooFar -> R.string.monument_detail_continue
                  state.settings.audience == ShareAudience.NONE ->
                      R.string.monument_detail_share_with_friends
                  else -> R.string.monument_detail_manage_sharing
                }
            ),
        style = MaterialTheme.typography.labelLarge,
    )
  }
}

@Composable
private fun SharedRecipientsStatus(
    recipients: List<CollectionFriend>,
    modifier: Modifier = Modifier,
) {
  val names = recipients.map { it.username }
  val text =
      when (names.size) {
        0 -> stringResource(R.string.monument_detail_shared_with_friends)
        1 -> stringResource(R.string.monument_detail_shared_with_one, names[0])
        2 -> stringResource(R.string.monument_detail_shared_with_two, names[0], names[1])
        else ->
            if (names.size == 3) {
              stringResource(
                  R.string.monument_detail_shared_with_one_other,
                  names[0],
                  names[1],
              )
            } else {
              stringResource(
                  R.string.monument_detail_shared_with_others,
                  names[0],
                  names[1],
                  names.size - 2,
              )
            }
      }
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.guideCalloutContainer)
              .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(modifier = Modifier.widthIn(max = 88.dp)) {
      recipients.take(2).forEachIndexed { index, friend ->
        ProfilePicture(
            name = friend.username,
            avatarBytes = friend.avatarBytes,
            modifier =
                Modifier.offset(x = (-12 * index).dp).size(32.dp).semantics {
                  testTag = "${C.Tag.monument_detail_share_avatar}_$index"
                },
            textStyle = MaterialTheme.typography.labelSmall,
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.accentIcon,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
    )
  }
}

/** Heart over the photo, styled like the one on collection cards: filled when [isFavorite]. */
@Composable
private fun MonumentDetailFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier
              .windowInsetsPadding(
                  WindowInsets.safeDrawing.only(
                      WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                  )
              )
              .padding(16.dp)
              .size(44.dp)
              .semantics { testTag = C.Tag.monument_detail_favorite_button },
      colors =
          IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.photoButtonContainer),
  ) {
    Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription =
            stringResource(
                if (isFavorite) R.string.collection_favorite_remove
                else R.string.collection_favorite_add
            ),
        tint = MaterialTheme.accentIcon,
    )
  }
}

/**
 * Switch that puts the saved guide on Explore at the place it was scanned, or for a gallery photo
 * ([isGalleryImage]) at the place the photo was taken.
 */
@Composable
private fun ShowInExploreRow(
    optIn: ExploreOptInUiState,
    onCheckedChange: (Boolean) -> Unit,
    isGalleryImage: Boolean,
    modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = optIn.isShownInExplore,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .semantics { testTag = C.Tag.monument_detail_show_in_explore },
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
        Text(
            text = stringResource(R.string.monument_detail_show_in_explore),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                stringResource(
                    if (isGalleryImage) R.string.monument_detail_show_in_explore_supporting_photo
                    else R.string.monument_detail_show_in_explore_supporting
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      // The row handles the toggle, so the switch is only its visual state.
      Switch(checked = optIn.isShownInExplore, onCheckedChange = null)
    }
    if (optIn.error != null) {
      Text(
          text = stringResource(R.string.monument_detail_show_in_explore_error),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.semantics { testTag = C.Tag.monument_detail_show_in_explore_error },
      )
    }
  }
}

/**
 * Explains why the guide cannot be put on Explore, shown in place of the Explore switch. For a
 * gallery photo ([isGalleryImage]) the observed location is the photo's, not the user's.
 */
@Composable
private fun ExploreUnavailableMessage(
    reason: ExploreUnavailableReason,
    isGalleryImage: Boolean,
    modifier: Modifier = Modifier,
) {
  val message =
      when (reason) {
        ExploreUnavailableReason.NO_OBSERVED_LOCATION ->
            if (isGalleryImage) R.string.monument_detail_explore_unavailable_no_photo_location
            else R.string.monument_detail_explore_unavailable_no_location
        ExploreUnavailableReason.NO_MONUMENT_LOCATION ->
            R.string.monument_detail_explore_unavailable_no_monument_location
        ExploreUnavailableReason.TOO_FAR ->
            if (isGalleryImage) R.string.monument_detail_explore_unavailable_photo_too_far
            else R.string.monument_detail_explore_unavailable_too_far
      }
  Text(
      text = stringResource(message),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
          modifier.fillMaxWidth().semantics { testTag = C.Tag.monument_detail_explore_unavailable },
  )
}

@Composable
fun MonumentDetailBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier
              .windowInsetsPadding(
                  WindowInsets.safeDrawing.only(
                      WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                  )
              )
              .padding(16.dp)
              .size(44.dp)
              .semantics { testTag = C.Tag.monument_detail_back_button },
      colors =
          IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.photoButtonContainer,
              contentColor = MaterialTheme.accentIcon,
          ),
  ) {
    Icon(
        imageVector = Icons.Filled.Home,
        contentDescription = stringResource(R.string.monument_detail_home_description),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewMonumentDetailScreen() {
  SampleAppTheme(darkTheme = false, dynamicColor = false) {
    MonumentDetailScreen(
        image = painterResource(id = android.R.drawable.ic_menu_gallery),
        guide =
            MonumentGuide(
                monumentName = "Eiffel Tower",
                confidence = 0.92,
                history =
                    "Built in 1889 as the entrance arch for the World's Fair, the Eiffel Tower " +
                        "was designed by Gustave Eiffel's engineering company and was initially " +
                        "criticized before becoming an enduring icon of Paris.",
                facts =
                    listOf(
                        "330 meters tall, the tallest structure in the world until 1930.",
                        "Repainted every seven years with around 60 tonnes of paint.",
                        "Grows up to 15 centimeters taller in summer due to thermal expansion.",
                    ),
                culture =
                    "The globally recognized symbol of Paris and France, epitomizing romance, " +
                        "elegance, and 19th-century industrial architecture.",
                language = "en",
            ),
        onBackClick = {},
        isFavorite = true,
    )
  }
}
