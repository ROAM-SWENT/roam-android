// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.guide.placeholderGuides
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.CollectionPlaceholder
import com.github.roamswent.roam.ui.theme.CollectionPlaceholderIcon
import com.github.roamswent.roam.ui.theme.LoadingErrorText
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.LoadingTextSecondary
import com.github.roamswent.roam.ui.theme.ScreenBackground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shows the user's saved guides as a two-column grid of cards, newest first. Guides already loaded
 * stay visible while the collection reloads or fails to refresh.
 */
@Composable
fun CollectionScreen(
    state: SavedGuidesUiState,
    modifier: Modifier = Modifier,
    onGuideClick: (String) -> Unit = {},
) {
  Scaffold(
      modifier = modifier.fillMaxSize().semantics { testTag = C.Tag.collection_screen },
      containerColor = ScreenBackground,
  ) { contentPadding ->
    Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
      Text(
          text = stringResource(R.string.collection_title),
          fontSize = 28.sp,
          fontWeight = FontWeight.Bold,
          color = LoadingTextPrimary,
          modifier =
              Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics {
                testTag = C.Tag.collection_title
              },
      )
      when {
        state.guides.isNotEmpty() -> GuideGrid(newestFirst(state.guides), onGuideClick)
        state.isLoadingGuides -> CollectionLoading()
        state.guidesError != null -> CollectionMessage(C.Tag.collection_error) { CollectionError() }
        else -> CollectionMessage(C.Tag.collection_empty_state) { CollectionEmpty() }
      }
    }
  }
}

@Composable
private fun GuideGrid(guides: List<Guide>, onGuideClick: (String) -> Unit) {
  LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.collection_grid },
  ) {
    items(guides, key = { it.id }) { guide ->
      GuideCard(guide, onClick = { onGuideClick(guide.id) })
    }
  }
}

@Composable
private fun GuideCard(guide: Guide, onClick: () -> Unit) {
  Card(
      onClick = onClick,
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.collection_card },
  ) {
    GuidePhoto(guide)
    Column(modifier = Modifier.padding(12.dp)) {
      Text(
          text = guide.monumentName,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = LoadingTextPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.semantics { testTag = C.Tag.collection_card_name },
      )
      guide.discoveryDate?.let { date ->
        Text(
            text = formatDiscoveryDate(date),
            fontSize = 12.sp,
            color = LoadingTextSecondary,
            modifier =
                Modifier.padding(top = 4.dp).semantics { testTag = C.Tag.collection_card_date },
        )
      }
    }
  }
}

/** Shows the guide's photo, or a placeholder when it has none or the photo cannot be decoded. */
@Composable
private fun GuidePhoto(guide: Guide) {
  val photoModifier = Modifier.fillMaxWidth().aspectRatio(1f)
  if (guide.photoBytes.isEmpty()) {
    PhotoPlaceholder(photoModifier)
  } else {
    val placeholder = rememberVectorPainter(Icons.Filled.AccountBalance)
    AsyncImage(
        model = guide.photoBytes,
        contentDescription =
            stringResource(R.string.collection_photo_description, guide.monumentName),
        contentScale = ContentScale.Crop,
        error = placeholder,
        modifier = photoModifier.background(CollectionPlaceholder),
    )
  }
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          modifier.background(CollectionPlaceholder).semantics {
            testTag = C.Tag.collection_card_photo_placeholder
          },
  ) {
    Icon(
        imageVector = Icons.Filled.AccountBalance,
        contentDescription = null,
        tint = CollectionPlaceholderIcon,
        modifier = Modifier.size(40.dp),
    )
  }
}

@Composable
private fun CollectionLoading() {
  Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
    CircularProgressIndicator(modifier = Modifier.semantics { testTag = C.Tag.collection_loading })
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
private fun CollectionEmpty() {
  Icon(
      imageVector = Icons.Filled.AccountBalance,
      contentDescription = null,
      tint = CollectionPlaceholderIcon,
      modifier = Modifier.size(56.dp),
  )
  Text(
      text = stringResource(R.string.collection_empty_title),
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = LoadingTextPrimary,
      modifier = Modifier.padding(top = 16.dp),
  )
  Text(
      text = stringResource(R.string.collection_empty_subtitle),
      fontSize = 14.sp,
      color = LoadingTextSecondary,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun CollectionError() {
  Text(
      text = stringResource(R.string.collection_error),
      fontSize = 14.sp,
      color = LoadingErrorText,
      textAlign = TextAlign.Center,
  )
}

private val discoveryDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

/** Formats [epochMillis] like "12 Oct 2026", in [zone] and [locale]. */
internal fun formatDiscoveryDate(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String =
    discoveryDateFormatter.withLocale(locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** Sorts [guides] by discovery date, most recent first; guides without a date come last. */
internal fun newestFirst(guides: List<Guide>): List<Guide> =
    guides.sortedWith(compareByDescending(nullsFirst()) { it.discoveryDate })

@Preview(showBackground = true)
@Composable
fun PreviewCollectionScreen() {
  MaterialTheme { CollectionScreen(state = SavedGuidesUiState(guides = placeholderGuides)) }
}

@Preview(showBackground = true)
@Composable
fun PreviewCollectionScreenEmpty() {
  MaterialTheme { CollectionScreen(state = SavedGuidesUiState()) }
}
