package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun MonumentDetailRoute(
    viewModel: MonumentDetailViewModel,
    onNavigateHome: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        MonumentDetailEvent.NavigateHome -> onNavigateHome()
      }
    }
  }

  when (val state = uiState) {
    is MonumentDetailUiState.Success ->
        MonumentDetailScreen(
            image = rememberAsyncImagePainter(state.monument.imageUri),
            text = state.monument.description,
            onBackClick = viewModel::onBackClick,
        )

    MonumentDetailUiState.Loading ->
        Box(Modifier.fillMaxSize().background(ScreenBackground)) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
          MonumentDetailBackButton(onClick = viewModel::onBackClick)
        }

    is MonumentDetailUiState.Error ->
        Box(Modifier.fillMaxSize().background(ScreenBackground)) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = state.message,
                  color = Color.Black,
                  modifier = Modifier.semantics { testTag = C.Tag.monument_detail_error_message },
              )
              TextButton(
                  onClick = viewModel::load,
                  modifier = Modifier.semantics { testTag = C.Tag.monument_detail_retry_button },
              ) {
                Text("Retry")
              }
            }
          }
          MonumentDetailBackButton(onClick = viewModel::onBackClick)
        }
  }
}

@Composable
fun MonumentDetailScreen(image: Painter, text: String, onBackClick: () -> Unit = {}) {
  val textBackgroundColor = ScreenBackground
  val cornerRadius = 32.dp

  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize().semantics { testTag = C.Tag.monument_detail_screen_container }
  ) {
    val totalHeightPx = constraints.maxHeight.toFloat()
    var textFraction by remember { mutableFloatStateOf(2f / 3f) }

    Image(
        painter = image,
        contentDescription = "Monument picture",
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
                      .background(Color.Gray.copy(alpha = 0.5f))
          )
        }

        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = Color.Black,
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = C.Tag.monument_detail_text },
        )
      }
    }
  }
  MonumentDetailBackButton(onClick = onBackClick)
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
              containerColor = Color.Gray.copy(alpha = 0.6f),
              contentColor = Color.White,
          ),
  ) {
    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewMonumentDetailScreen() {
  val placeholderText =
      """
      Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non risus. Suspendisse lectus tortor, dignissim sit amet, adipiscing nec, ultricies sed, dolor. Cras elementum ultrices diam. Maecenas ligula massa, varius a, semper congue, euismod non, mi. Proin porttitor, orci nec nonummy molestie, enim est eleifend mi, non fermentum diam nisl sit amet erat. Duis semper. Duis arcu massa, scelerisque vitae, consequat in, pretium a, enim. Pellentesque congue. Ut in risus volutpat libero pharetra tempor. Cras vestibulum bibendum augue. Praesent egestas leo in pede. Praesent blandit odio eu enim. Pellentesque sed dui ut augue blandit sodales. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Aliquam nibh. Mauris ac mauris sed pede pellentesque fermentum. Maecenas adipiscing ante non diam sodales hendrerit.

      Ut velit mauris, egestas sed, gravida nec, ornare ut, mi. Aenean ut orci vel massa suscipit pulvinar. Nulla sollicitudin. Fusce varius, ligula non tempus aliquam, nunc turpis ullamcorper nibh, in tempus sapien eros vitae ligula. Pellentesque rhoncus nunc et augue. Integer id felis. Curabitur aliquet pellentesque diam. Integer quis metus vitae elit lobortis egestas. Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Morbi vel erat non mauris convallis vehicula. Nulla et sapien. Integer tortor tellus, aliquam faucibus, convallis id, congue eu, quam. Mauris ullamcorper felis vitae erat. Proin feugiat, augue non elementum posuere, metus purus iaculis lectus, et tristique ligula justo vitae magna.
      Aliquam convallis sollicitudin purus. Praesent aliquam, enim at fermentum mollis, ligula massa adipiscing nisl, ac euismod nibh nisl eu lectus. Fusce vulputate sem at sapien. Vivamus leo. Aliquam euismod libero eu enim. Nulla nec felis sed leo placerat imperdiet. Aenean suscipit nulla in justo. Suspendisse cursus rutrum augue. Nulla tincidunt tincidunt mi. Curabitur iaculis, lorem vel rhoncus faucibus, felis magna fermentum augue, et ultricies lacus lorem varius purus. Curabitur eu amet. 
      """
          .trimIndent()

  MaterialTheme {
    MonumentDetailScreen(
        image = painterResource(id = android.R.drawable.ic_menu_gallery),
        text = placeholderText.repeat(2),
    )
  }
}
