package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun HomeScreen(onScanClicked: () -> Unit) {
  val haloSize = 72.dp

  Box(
      modifier =
          Modifier.fillMaxSize().background(ScreenBackground).semantics {
            testTag = C.Tag.home_screen_container
          }
  ) {
    Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
      Surface(
          modifier =
              Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(top = haloSize / 2),
          color = Color.White,
      ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 44.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              text = "Scan a monument",
              color = ScanTeal,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.semantics { testTag = C.Tag.home_scan_label },
          )
        }
      }

      Box(
          modifier =
              Modifier.align(Alignment.TopCenter)
                  .size(haloSize)
                  .background(color = Color.White, shape = CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        IconButton(
            onClick = onScanClicked,
            modifier =
                Modifier.size(56.dp).background(color = ScanTeal, shape = CircleShape).semantics {
                  testTag = C.Tag.home_scan_button
                },
        ) {
          Icon(
              imageVector = Icons.Default.CenterFocusStrong,
              contentDescription = "Scan a monument",
              tint = Color.White,
              modifier = Modifier.size(28.dp),
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  SampleAppTheme { HomeScreen(onScanClicked = {}) }
}
