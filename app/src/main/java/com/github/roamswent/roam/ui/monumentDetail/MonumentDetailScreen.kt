package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.ScreenBackground
import kotlin.math.roundToInt

@Composable
fun MonumentDetailScreen(image: Painter, guide: MonumentGuide, modifier: Modifier = Modifier) {
  val textBackgroundColor = ScreenBackground
  val cornerRadius = 32.dp

  BoxWithConstraints(
      modifier =
          modifier.fillMaxSize().semantics { testTag = C.Tag.monument_detail_screen_container }
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
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold,
              color = Color.Black,
              modifier = Modifier.semantics { testTag = C.Tag.monument_detail_title },
          )
          Text(
              text = "${(guide.confidence * 100).roundToInt()}% match",
              fontSize = 14.sp,
              color = Color.Gray,
              modifier =
                  Modifier.padding(bottom = 16.dp).semantics {
                    testTag = C.Tag.monument_detail_confidence
                  },
          )
          Text(
              text = guide.history,
              fontSize = 16.sp,
              lineHeight = 24.sp,
              color = Color.Black,
              modifier =
                  Modifier.padding(bottom = 16.dp).semantics {
                    testTag = C.Tag.monument_detail_history
                  },
          )
          guide.facts.forEach { fact ->
            Text(
                text = "• $fact",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Color.Black,
                modifier =
                    Modifier.padding(bottom = 4.dp).semantics {
                      testTag = C.Tag.monument_detail_fact_item
                    },
            )
          }
          Text(
              text = guide.culture,
              fontSize = 16.sp,
              lineHeight = 24.sp,
              color = Color.Black,
              modifier =
                  Modifier.padding(top = 12.dp).semantics {
                    testTag = C.Tag.monument_detail_culture
                  },
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewMonumentDetailScreen() {
  MaterialTheme {
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
    )
  }
}
