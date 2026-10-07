package com.github.roamswent.roam.ui.scan

import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
                    .fillMaxWidth()
                    .fillMaxHeight(1f / 3f)
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
          ExpressiveShutterButton(onClick = onCapture, enabled = shutterEnabled)
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

@Composable
fun ExpressiveShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val hapticFeedback = LocalHapticFeedback.current
  val innerScale by
      animateFloatAsState(
          targetValue = if (isPressed) 1.14f else 1f,
          animationSpec =
              spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMediumLow,
              ),
          label = "shutterScale",
      )

  Box(
      modifier =
          modifier
              .size(84.dp)
              .clip(CircleShape)
              .border(4.dp, ShutterHalo, CircleShape)
              .clickable(
                  enabled = enabled,
                  interactionSource = interactionSource,
                  indication = null,
                  onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                  },
              )
              .semantics { testTag = C.Tag.scan_shutter_button },
      contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(76.dp).scale(innerScale).background(ShutterWhite, CircleShape))
  }
}
