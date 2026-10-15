package com.github.roamswent.roam.ui.loading

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.LoadingErrorText
import com.github.roamswent.roam.ui.theme.LoadingProgressAccent
import com.github.roamswent.roam.ui.theme.LoadingProgressTrack
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary
import com.github.roamswent.roam.ui.theme.LoadingTextSecondary
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun LoadingScreen(
    image: Painter,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    errorMessage: String? = null,
) {
  BackHandler(onBack = onCancel)
  val isLoading = errorMessage == null
  val displayedTitle = title ?: stringResource(R.string.loading_title)
  val displayedSubtitle = subtitle ?: stringResource(R.string.loading_subtitle)

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(ScreenBackground)
              .statusBarsPadding()
              .navigationBarsPadding()
  ) {
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

      if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp).semantics { testTag = C.Tag.loading_screen_progress },
            color = LoadingProgressAccent,
            trackColor = LoadingProgressTrack,
            strokeWidth = 3.dp,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = displayedTitle,
            modifier = Modifier.semantics { testTag = C.Tag.loading_screen_title },
            color = LoadingTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(6.dp))
      } else {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Error",
            tint = LoadingErrorText,
            modifier = Modifier.size(32.dp).semantics { testTag = C.Tag.loading_screen_error_icon },
        )

        Spacer(Modifier.height(24.dp))
      }

      Text(
          text = errorMessage ?: displayedSubtitle,
          modifier = Modifier.semantics { testTag = C.Tag.loading_screen_subtitle },
          color = if (errorMessage == null) LoadingTextSecondary else LoadingErrorText,
          fontSize = 14.sp,
      )
    }

    TextButton(
        onClick = onCancel,
        modifier =
            Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).semantics {
              testTag = C.Tag.loading_screen_cancel_button
            },
    ) {
      Text(
          text = stringResource(R.string.loading_cancel),
          color = LoadingTextPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun LoadingScreenPreview() {
  LoadingScreen(
      image = painterResource(android.R.drawable.ic_menu_gallery),
      onCancel = {},
  )
}
