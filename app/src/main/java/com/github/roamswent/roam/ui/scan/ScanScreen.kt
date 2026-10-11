package com.github.roamswent.roam.ui.scan

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.scan.components.ExpressiveFloatingButton
import com.github.roamswent.roam.ui.scan.components.FramingReticle
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
        FramingReticle(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.75f).aspectRatio(1f),
        )
        if (!shutterEnabled) {
          Box(modifier = Modifier.fillMaxSize().background(CameraCaptureOverlay))
        }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
              modifier =
                  Modifier.size(40.dp)
                      .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                      .clickable(onClick = onGoBack)
                      .semantics { testTag = C.Tag.scan_close_button },
              contentAlignment = Alignment.Center,
          ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
          }
          Box(
              modifier = Modifier.weight(1f).padding(start = 12.dp),
              contentAlignment = Alignment.Center,
          ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.semantics { testTag = C.Tag.scan_prompt_pill },
            ) {
              Text(
                  text = "Frame the monument",
                  color = Color.White,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              )
            }
          }
          Spacer(Modifier.size(40.dp))
        }
        ExpressiveFloatingButton(
            onClick = onCapture,
            enabled = shutterEnabled,
            containerColor = ShutterHalo,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .size(80.dp)
                    .semantics { testTag = C.Tag.scan_shutter_button },
        ) {
          Box(
              modifier = Modifier.size(80.dp).border(4.dp, Color.White, CircleShape),
          ) {
            Box(Modifier.align(Alignment.Center).size(64.dp).background(ShutterWhite, CircleShape))
          }
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
