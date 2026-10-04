package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground

@Composable
fun HomeScreen(onScanClicked: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxSize().background(ScreenBackground).semantics {
            testTag = C.Tag.home_screen_container
          }
  ) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        color = Color.White,
    ) {
      Column(
          modifier = Modifier.navigationBarsPadding(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        Box(
            modifier =
                Modifier.size(72.dp)
                    .offset { IntOffset(0, -24.dp.roundToPx()) }
                    .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
          IconButton(
              onClick = onScanClicked,
              modifier =
                  Modifier.size(60.dp).background(ScanTeal, CircleShape).semantics {
                    testTag = C.Tag.home_scan_button
                  },
          ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "Scan a monument",
                tint = Color.White,
            )
          }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Scan a monument",
            modifier = Modifier.semantics { testTag = C.Tag.home_scan_label },
            color = ScanTeal,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  SampleAppTheme { HomeScreen(onScanClicked = {}) }
}
