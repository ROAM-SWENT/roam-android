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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.guide.ExploreUnavailableReason
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.loading.ExploreOptInUiState
import com.github.roamswent.roam.ui.theme.GuideTypography
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.accentIcon
import com.github.roamswent.roam.ui.theme.guidePanel
import com.github.roamswent.roam.ui.theme.offlineNoticeContainer
import com.github.roamswent.roam.ui.theme.photoButtonContainer

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
    isOnline: Boolean = true,
    onOpenInExplore: ((Coordinates) -> Unit)? = null,
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
                  .fillMaxWidth()
                  .verticalScroll(state = rememberScrollState(), overscrollEffect = null)
                  .padding(horizontal = 24.dp, vertical = 24.dp)
                  .semantics { testTag = C.Tag.monument_detail_text },
          verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        MaterialTheme(typography = GuideTypography) {
          GuideHeader(guide)
          if (exploreOptIn != null) {
            ShowInExploreRow(
                optIn = exploreOptIn,
                onCheckedChange = onShowInExploreChange,
                isGalleryImage = isGalleryImage,
            )
          }
          GuideContent(guide)
          val monumentLocation = guide.monumentLocation
          val unavailableReason =
              if (monumentLocation == null) ExploreUnavailableReason.NO_MONUMENT_LOCATION
              else exploreUnavailableReason.takeIf { exploreOptIn == null }
          if (unavailableReason != null) {
            ExploreUnavailableMessage(unavailableReason, isGalleryImage)
          } else if (
              isOnline &&
                  monumentLocation != null &&
                  onOpenInExplore != null &&
                  exploreUnavailableReason == null &&
                  exploreOptIn?.error == null
          ) {
            GuideMiniMap(
                monumentName = guide.monumentName,
                location = monumentLocation,
                onOpen = { onOpenInExplore(monumentLocation) },
            )
          }
        }
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
    LaunchedEffect(showFavoriteError) {
      if (showFavoriteError) {
        snackbarHostState.showSnackbar(favoriteErrorMessage)
        onFavoriteErrorShown()
      }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      // The row handles the toggle, so the switch is only its visual state.
      Switch(checked = optIn.isShownInExplore, onCheckedChange = null)
    }
    if (optIn.error != null) {
      Text(
          text = stringResource(R.string.monument_detail_show_in_explore_error),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.semantics { testTag = C.Tag.monument_detail_show_in_explore_error },
      )
    }
  }
}

/**
 * Explains the location problem in the footer. For gallery images the observed location is the
 * photo's capture position.
 */
@Composable
private fun ExploreUnavailableMessage(
    reason: ExploreUnavailableReason,
    isGalleryImage: Boolean,
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
  val title =
      if (reason == ExploreUnavailableReason.NO_MONUMENT_LOCATION) {
        R.string.monument_detail_explore_location_unavailable_title
      } else {
        R.string.monument_detail_explore_unavailable_title
      }
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.offlineNoticeContainer, RoundedCornerShape(12.dp))
              .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top,
  ) {
    Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
          text = stringResource(title),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text = stringResource(message),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.semantics { testTag = C.Tag.monument_detail_explore_unavailable },
      )
    }
  }
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
