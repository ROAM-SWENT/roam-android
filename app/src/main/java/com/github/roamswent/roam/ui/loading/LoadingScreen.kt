package com.github.roamswent.roam.ui.loading

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.LoadingErrorText
import com.github.roamswent.roam.ui.theme.LoadingProgressAccent
import com.github.roamswent.roam.ui.theme.LoadingProgressTrack
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.LoadingTextSecondary
import com.github.roamswent.roam.ui.theme.ScreenBackground
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LoadingRoute(
    imageUri: Uri,
    onCancel: () -> Unit,
    onDiscoverySuccess: (MonumentGuide, Uri) -> Unit,
    onDiscoveryError: (String) -> Unit,
    repository: MonumentGuideRepository,
    modifier: Modifier = Modifier,
    readImage: (suspend (Uri) -> ImagePayload)? = null,
    language: String = Locale.getDefault().toLanguageTag(),
) {
  val context = LocalContext.current.applicationContext
  val imageReader =
      readImage
          ?: { uri ->
            withContext(Dispatchers.IO) {
              val resolver = context.contentResolver
              val imageBytes =
                  requireNotNull(resolver.openInputStream(uri)) {
                        "Unable to open the captured image."
                      }
                      .use { it.readBytes() }
              ImagePayload(imageBytes, resolver.getType(uri) ?: "image/jpeg")
            }
          }
  val viewModel: LoadingViewModel =
      viewModel(key = imageUri.toString()) {
        LoadingViewModel(repository, imageReader, imageUri, language)
      }
  val uiState by viewModel.uiState.collectAsState()

  LaunchedEffect(viewModel) {
    viewModel.navigationEvents.collect { event ->
      when (event) {
        LoadingNavigationEvent.NavigateHome -> onCancel()
        is LoadingNavigationEvent.NavigateHomeWithError -> onDiscoveryError(event.message)
        is LoadingNavigationEvent.NavigateToMonumentDetail ->
            onDiscoverySuccess(event.guide, Uri.parse(event.imageUri))
      }
    }
  }

  AnalyzingScreen(
      image = rememberAsyncImagePainter(imageUri),
      onCancel = viewModel::onCancel,
      modifier = modifier,
      errorMessage = (uiState as? LoadingUiState.Error)?.message,
  )
}

@Composable
fun AnalyzingScreen(
    image: Painter, // L'image change à chaque fois : on la passe en paramètre
    onCancel: () -> Unit, // Retour à l'écran précédent
    modifier: Modifier = Modifier,
    title: String = "Analyzing…",
    subtitle: String = "Identifying your monument.",
    errorMessage: String? = null,
) {
  // Le bouton retour système se comporte comme "Cancel"
  BackHandler(onBack = onCancel)

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(ScreenBackground)
              .statusBarsPadding()
              .navigationBarsPadding()
  ) {
    // Contenu centré
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Image(
          painter = image,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier =
              Modifier.width(148.dp).height(193.dp).clip(RoundedCornerShape(20.dp)).semantics {
                testTag = C.Tag.loading_screen_image
              },
      )

      Spacer(Modifier.height(32.dp))

      // Sans paramètre `progress`, le cercle tourne en continu
      CircularProgressIndicator(
          modifier = Modifier.size(32.dp).semantics { testTag = C.Tag.loading_screen_progress },
          color = LoadingProgressAccent,
          trackColor = LoadingProgressTrack,
          strokeWidth = 3.dp,
      )

      Spacer(Modifier.height(24.dp))

      Text(
          text = title,
          modifier = Modifier.semantics { testTag = C.Tag.loading_screen_title },
          color = LoadingTextPrimary,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
      )

      Spacer(Modifier.height(6.dp))

      Text(
          text = errorMessage ?: subtitle,
          modifier = Modifier.semantics { testTag = C.Tag.loading_screen_subtitle },
          color = if (errorMessage == null) LoadingTextSecondary else LoadingErrorText,
          fontSize = 14.sp,
      )
    }

    // Bouton Cancel en bas
    TextButton(
        onClick = onCancel,
        modifier =
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).semantics {
              testTag = C.Tag.loading_screen_cancel_button
            },
    ) {
      Text(
          text = "Cancel",
          color = LoadingTextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun AnalyzingScreenPreview() {
  AnalyzingScreen(
      image = painterResource(android.R.drawable.ic_menu_gallery),
      onCancel = {},
  )
}
