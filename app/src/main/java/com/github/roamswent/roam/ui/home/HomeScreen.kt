package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.github.roamswent.roam.ui.theme.Teal

@Composable
fun HomeScreen(onScanClicked: () -> Unit) {
  Column(
      modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.home_screen_container },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Button(
        onClick = onScanClicked,
        modifier = Modifier.size(220.dp).semantics { testTag = C.Tag.home_scan_button },
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Teal,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
    ) {
      Text(
          text = "Scan a monument",
          modifier = Modifier.semantics { testTag = C.Tag.home_scan_label },
      )
    }
  }

  @Preview(showBackground = true)
  @Composable
  fun HomeScreenPreview() {
    SampleAppTheme { HomeScreen(onScanClicked = {}) }
  }
}
