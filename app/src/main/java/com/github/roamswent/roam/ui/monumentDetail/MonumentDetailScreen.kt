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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.loading.ExploreOptInUiState
import com.github.roamswent.roam.ui.loading.ExploreUnavailableReason
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.github.roamswent.roam.ui.theme.accentIcon
import com.github.roamswent.roam.ui.theme.guideCalloutContainer
import com.github.roamswent.roam.ui.theme.guideEyebrow
import com.github.roamswent.roam.ui.theme.isDark
import com.github.roamswent.roam.ui.theme.photoButtonContainer
import kotlin.math.roundToInt

/**
 * Shows [guide] over its [image]. The favourite heart is shown only when [isFavorite] is not null,
 * that is once the guide is in the collection, and [showFavoriteError] shows a snackbar that is
 * reported back with [onFavoriteErrorShown]. The Explore switch is shown only when [exploreOptIn]
 * is not null, and reports changes with [onShowInExploreChange]. Otherwise, when
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
              text = stringResource(R.string.monument_detail_ai_guide_label),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.guideEyebrow,
              modifier = Modifier.padding(bottom = 6.dp),
          )
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
          if (exploreOptIn != null) {
            ShowInExploreRow(
                optIn = exploreOptIn,
                onCheckedChange = onShowInExploreChange,
                isGalleryImage = isGalleryImage,
                modifier = Modifier.padding(bottom = 16.dp),
            )
          } else if (exploreUnavailableReason != null) {
            ExploreUnavailableMessage(
                reason = exploreUnavailableReason,
                isGalleryImage = isGalleryImage,
                modifier = Modifier.padding(bottom = 16.dp),
            )
          }
          Text(
              text = markdownText(guide.history),
              style = guideBodyStyle(),
              color = textColor,
              modifier =
                  Modifier.padding(bottom = 16.dp).semantics {
                    testTag = C.Tag.monument_detail_history
                  },
          )
          if (guide.sections.isNotEmpty()) {
            val funFactsTitle = stringResource(R.string.interest_fun_facts)
            val funFactTitle = stringResource(R.string.monument_detail_facts_title)
            val legendsTitle = stringResource(R.string.interest_legends_myths)
            val isCalloutTitle: (String) -> Boolean = { title ->
              title.trim().equals(funFactsTitle, ignoreCase = true) ||
                  title.trim().equals(funFactTitle, ignoreCase = true) ||
                  title.trim().equals(legendsTitle, ignoreCase = true)
            }
            val regularSections = guide.sections.filterNot { isCalloutTitle(it.title) }
            val calloutSections = guide.sections.filter { isCalloutTitle(it.title) }
            (regularSections + calloutSections).forEach { section ->
              GuideSectionCard(
                  title = section.title,
                  content = section.content,
                  bodyStyle = guideBodyStyle(),
                  isCallout = isCalloutTitle(section.title),
              )
            }
          } else {
            guide.facts.forEach { fact ->
              GuideCallout(
                  title = stringResource(R.string.monument_detail_facts_title),
                  content = fact,
                  bodyStyle = guideBodyStyle(),
                  modifier =
                      Modifier.padding(bottom = 8.dp).semantics {
                        testTag = C.Tag.monument_detail_fact_item
                      },
              )
            }
            GuideSectionHeading(
                title = stringResource(R.string.monument_detail_culture_title),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = markdownText(guide.culture),
                style = guideBodyStyle(),
                color = textColor,
                modifier =
                    Modifier.padding(bottom = 8.dp).semantics {
                      testTag = C.Tag.monument_detail_culture
                    },
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

@Composable private fun guideBodyStyle() = MaterialTheme.typography.bodyLarge

@Composable
private fun GuideSectionCard(
    title: String,
    content: String,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    isCallout: Boolean,
) {
  if (isCallout) {
    GuideCallout(
        title = title,
        content = content,
        bodyStyle = bodyStyle,
        modifier = Modifier.padding(bottom = 12.dp),
    )
  } else {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
      GuideSectionHeading(title = title)
      Text(
          text = markdownText(content),
          style = bodyStyle,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun GuideCallout(
    title: String,
    content: String,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.guideCalloutContainer)
              .padding(12.dp)
  ) {
    GuideCalloutHeading(title = title)
    Text(
        text = markdownText(content),
        style = bodyStyle,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun GuideSectionHeading(title: String, modifier: Modifier = Modifier) {
  Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.accentIcon,
      modifier = modifier.padding(bottom = 6.dp),
  )
}

@Composable
private fun GuideCalloutHeading(title: String) {
  Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.accentIcon,
      modifier = Modifier.padding(bottom = 4.dp),
  )
}

private fun markdownText(markdown: String): AnnotatedString = buildAnnotatedString {
  markdown.lines().forEachIndexed { index, line ->
    if (index > 0) append("\n")
    val trimmed = line.trim()
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      append("• ")
      appendMarkdownInline(trimmed.drop(2))
    } else {
      appendMarkdownInline(trimmed)
    }
  }
}

private fun AnnotatedString.Builder.appendMarkdownInline(text: String) {
  val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
  var cursor = 0
  boldPattern.findAll(text).forEach { match ->
    append(text.substring(cursor, match.range.first))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
    cursor = match.range.last + 1
  }
  append(text.substring(cursor))
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
          modifier.statusBarsPadding().padding(16.dp).size(44.dp).semantics {
            testTag = C.Tag.monument_detail_back_button
          },
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
