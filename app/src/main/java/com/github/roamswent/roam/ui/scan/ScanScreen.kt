package com.github.roamswent.roam.ui.scan

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.CameraCaptureOverlay
import com.github.roamswent.roam.ui.theme.ShutterHalo
import com.github.roamswent.roam.ui.theme.ShutterWhite

@Composable
fun ScanScreen(
    state: ScanUiState,
    onCapture: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
    previewView: View? = null,
) {
  when (state) {
    ScanUiState.Streaming,
    ScanUiState.Capturing,
    ScanUiState.Captured -> {
      val shutterEnabled = state == ScanUiState.Streaming
      Box(modifier = modifier.fillMaxSize().semantics { testTag = C.Tag.scan_camera_screen }) {
        if (previewView != null) {
          AndroidView(
              factory = { previewView },
              modifier =
                  Modifier.fillMaxSize().semantics { testTag = C.Tag.scan_camera_viewfinder },
          )
        }
        if (!shutterEnabled) {
          Box(modifier = Modifier.fillMaxSize().background(CameraCaptureOverlay))
        }
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .size(80.dp)
                    .border(2.dp, ShutterHalo, CircleShape)
                    .clickable(enabled = shutterEnabled, onClick = onCapture)
                    .semantics { testTag = C.Tag.scan_shutter_button },
            contentAlignment = Alignment.Center,
        ) {
          Box(Modifier.size(58.dp).background(ShutterWhite, CircleShape))
        }
      }
    }
    ScanUiState.Revoked -> {
      Column(
          modifier =
              modifier.fillMaxSize().semantics { testTag = C.Tag.scan_camera_revoked_container },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        Text("Camera access was disabled.")
        Button(
            onClick = onGoBack,
            modifier =
                Modifier.padding(top = 16.dp).semantics {
                  testTag = C.Tag.scan_camera_revoked_back
                },
        ) {
          Text("Go back")
        }
      }
    }
  }
}
