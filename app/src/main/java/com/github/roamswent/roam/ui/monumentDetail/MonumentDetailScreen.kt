// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
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
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.loading.ExploreOptInUiState
import com.github.roamswent.roam.ui.theme.HomeAccentGreen
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.isDark
import kotlin.math.roundToInt

/**
 * Shows [guide] over its [image]. The favourite heart is shown only when [isFavorite] is not null,
 * that is once the guide is in the collection, and [showFavoriteError] shows a snackbar that is
 * reported back with [onFavoriteErrorShown]. The Explore switch is shown only when [exploreOptIn]
 * is not null, and reports changes with [onShowInExploreChange].
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
) {
  val textBackgroundColor =
      if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface else ScreenBackground
  val textColor = MaterialTheme.colorScheme.onSurface
  val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
  val cornerRadius = 32.dp

  BoxWithConstraints(
      modifier =
          modifier.fillMaxSize().semantics { testTag = C.Tag.monument_detail_screen_container }
  ) {
    val totalHeightPx = constraints.maxHeight.toFloat()
    var textFraction by remember { mutableFloatStateOf(2f / 3f) }

    Image(
        painter = image,
        contentDescription = stringResource(R.string.monument_detail_picture_description),
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.fillMaxWidth()
                .height(maxHeight * (1f - textFraction) + cornerRadius)
                .align(Alignment.TopCenter)
                .semantics { testTag = C.Tag.monument_detail_picture },
    )

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .fillMaxHeight(textFraction)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                .background(textBackgroundColor)
                .semantics { testTag = C.Tag.monument_detail_panel }
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .semantics { testTag = C.Tag.monument_detail_drag_handle }
                    .pointerInput(Unit) {
                      detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val dragFraction = dragAmount / totalHeightPx
                        textFraction = (textFraction - dragFraction).coerceIn(0.2f, 0.85f)
                      }
                    },
            contentAlignment = Alignment.Center,
        ) {
          Box(
              modifier =
                  Modifier.width(40.dp)
                      .height(5.dp)
                      .clip(RoundedCornerShape(50))
                      .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
          )
        }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = C.Tag.monument_detail_text }
        ) {
          Text(
              text = guide.monumentName,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = textColor,
              modifier = Modifier.semantics { testTag = C.Tag.monument_detail_title },
          )
          Text(
              text =
                  stringResource(
                      R.string.monument_detail_match_percent,
                      (guide.confidence * 100).roundToInt(),
                  ),
              style = MaterialTheme.typography.bodySmall,
              color = secondaryTextColor,
              modifier =
                  Modifier.padding(bottom = 16.dp).semantics {
                    testTag = C.Tag.monument_detail_confidence
                  },
          )
          exploreOptIn?.let {
            ShowInExploreRow(
                optIn = it,
                onCheckedChange = onShowInExploreChange,
                modifier = Modifier.padding(bottom = 16.dp),
            )
          }
          Text(
              text = guide.history,
              style = MaterialTheme.typography.bodyLarge,
              color = textColor,
              modifier =
                  Modifier.padding(bottom = 16.dp).semantics {
                    testTag = C.Tag.monument_detail_history
                  },
          )
          guide.facts.forEach { fact ->
            Text(
                text = stringResource(R.string.monument_detail_fact_bullet, fact),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier =
                    Modifier.padding(bottom = 4.dp).semantics {
                      testTag = C.Tag.monument_detail_fact_item
                    },
            )
          }
          Text(
              text = guide.culture,
              style = MaterialTheme.typography.bodyLarge,
              color = textColor,
              modifier =
                  Modifier.padding(top = 12.dp).semantics {
                    testTag = C.Tag.monument_detail_culture
                  },
          )
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
          modifier.statusBarsPadding().padding(16.dp).size(44.dp).semantics {
            testTag = C.Tag.monument_detail_favorite_button
          },
      colors =
          IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.85f)),
  ) {
    Icon(
        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription =
            stringResource(
                if (isFavorite) R.string.collection_favorite_remove
                else R.string.collection_favorite_add
            ),
        tint = HomeAccentGreen,
    )
  }
}

/** Switch that puts the saved guide on Explore at the place it was scanned. */
@Composable
private fun ShowInExploreRow(
    optIn: ExploreOptInUiState,
    onCheckedChange: (Boolean) -> Unit,
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
            text = stringResource(R.string.monument_detail_show_in_explore_supporting),
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

@Composable
fun MonumentDetailBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  IconButton(
      onClick = onClick,
      modifier =
          modifier.statusBarsPadding().padding(16.dp).size(44.dp).semantics {
            testTag = C.Tag.monument_detail_back_button
          },
      colors =
          IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.inverseSurface,
              contentColor = MaterialTheme.colorScheme.inverseOnSurface,
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
