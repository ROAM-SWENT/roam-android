// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.ui.theme.accentIcon
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState

/** A temporary landmark pin. Opted-in collection pins are managed separately. */
@Composable
@GoogleMapComposable
internal fun GuideMonumentPin(location: Coordinates) {
  val tint = MaterialTheme.accentIcon
  MarkerComposable(
      tint,
      state = remember(location) { MarkerState(position = location.toLatLng()) },
      anchor = Offset(0.5f, 0.92f),
      zIndex = 1f,
      onClick = { true },
      content = @UiComposable { GuideMonumentPinIcon(tint) },
  )
}

@Composable
@UiComposable
private fun GuideMonumentPinIcon(tint: Color) {
  Icon(
      imageVector = Icons.Filled.LocationOn,
      contentDescription = null,
      modifier = Modifier.size(36.dp),
      tint = tint,
  )
}
