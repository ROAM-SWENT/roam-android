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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.common.formatDiscoveryDate
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.CollectionPlaceholder
import com.github.roamswent.roam.ui.theme.CollectionPlaceholderIcon
import com.github.roamswent.roam.ui.theme.LoadingErrorText
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.LoadingTextSecondary
import com.github.roamswent.roam.ui.theme.ScreenBackground

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
  val photoKey = remember(guide.photoBytes) { "${guide.id}:${guide.photoBytes.contentHashCode()}" }
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
            Modifier.matchParentSize().background(CollectionPlaceholder).semantics {
              testTag = C.Tag.collection_card_photo
            },
    )
    when (photoState) {
      is AsyncImagePainter.State.Empty,
      is AsyncImagePainter.State.Loading ->
          CircularProgressIndicator(
              modifier =
                  Modifier.size(32.dp).semantics { testTag = C.Tag.collection_card_photo_loading }
          )
      is AsyncImagePainter.State.Error ->
          PhotoPlaceholder(Modifier.matchParentSize(), C.Tag.collection_card_photo_error)
      is AsyncImagePainter.State.Success -> Unit
    }
  }
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier, tag: String) {
  Box(
      contentAlignment = Alignment.Center,
      modifier = modifier.background(CollectionPlaceholder).semantics { testTag = tag },
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
