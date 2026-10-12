package com.github.roamswent.roam.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.github.roamswent.roam.resources.C

@Composable
fun ScanReviewScreen() {
  BackHandler(enabled = true) {}
  Box(
      Modifier.fillMaxSize().semantics { testTag = C.Tag.scan_review_placeholder },
      contentAlignment = Alignment.Center,
  ) {
    Text("Review")
  }
}
