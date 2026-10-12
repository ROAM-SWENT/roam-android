package com.github.roamswent.roam.ui.scan.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.github.roamswent.roam.ui.theme.ShutterHalo

@Composable
fun ExpressiveFloatingButton(
    onClick: () -> Unit,
    enabled: Boolean,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
      animateFloatAsState(
          targetValue = if (isPressed) 1.14f else 1f,
          animationSpec =
              spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMediumLow,
              ),
          label = "floatingButtonScale",
      )
  val hapticFeedback = LocalHapticFeedback.current

  Box(
      modifier =
          modifier
              .clip(shape)
              .background(ShutterHalo, shape)
              .clickable(
                  enabled = enabled,
                  interactionSource = interactionSource,
                  indication = null,
                  onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                  },
              )
              .scale(scale),
      contentAlignment = Alignment.Center,
  ) {
    content()
  }
}
