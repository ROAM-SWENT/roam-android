// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.explore.EXPLORE_POINT_ZOOM
import com.github.roamswent.roam.ui.explore.ExploreCameraTarget
import com.github.roamswent.roam.ui.explore.GuideMonumentPin
import com.github.roamswent.roam.ui.explore.exploreMapColorScheme
import com.github.roamswent.roam.ui.explore.toCameraPosition
import com.github.roamswent.roam.ui.theme.guideHeading
import com.github.roamswent.roam.ui.theme.guidePanel
import com.github.roamswent.roam.ui.theme.isDark
import com.github.roamswent.roam.ui.theme.offlineMapBorder
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import kotlinx.coroutines.delay

internal const val GUIDE_MAP_LOAD_TIMEOUT_MS = 8_000L

private enum class MapReadiness {
  UNSEEN,
  LOADING,
  READY,
  FAILED,
}

private val guideMapUiSettings =
    MapUiSettings(
        compassEnabled = false,
        indoorLevelPickerEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
        rotationGesturesEnabled = false,
        scrollGesturesEnabled = false,
        scrollGesturesEnabledDuringRotateOrZoom = false,
        tiltGesturesEnabled = false,
        zoomControlsEnabled = false,
        zoomGesturesEnabled = false,
    )

@Composable
internal fun GuideMiniMap(
    monumentName: String,
    location: Coordinates,
    onOpen: () -> Unit,
) {
  var readiness by remember(location) { mutableStateOf(MapReadiness.UNSEEN) }
  LaunchedEffect(readiness) {
    // The Maps SDK does not invoke onMapLoaded when it fails to render tiles.
    if (readiness == MapReadiness.LOADING) {
      delay(GUIDE_MAP_LOAD_TIMEOUT_MS)
      if (readiness == MapReadiness.LOADING) readiness = MapReadiness.FAILED
    }
  }
  if (readiness == MapReadiness.FAILED) return

  val ready = readiness == MapReadiness.READY
  val shape = RoundedCornerShape(12.dp)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (ready) {
      Text(
          text = stringResource(R.string.monument_detail_map_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.guideHeading,
          modifier = Modifier.semantics { heading() },
      )
    }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(112.dp)
                .clip(shape)
                .then(
                    if (ready) Modifier.border(1.dp, MaterialTheme.offlineMapBorder, shape)
                    else Modifier
                )
                .onGloballyPositioned { coordinates ->
                  if (readiness == MapReadiness.UNSEEN && !coordinates.boundsInWindow().isEmpty) {
                    readiness = MapReadiness.LOADING
                  }
                },
    ) {
      if (readiness != MapReadiness.UNSEEN) {
        val camera =
            remember(location) {
              CameraPositionState(
                  ExploreCameraTarget(location, EXPLORE_POINT_ZOOM).toCameraPosition()
              )
            }
        GoogleMap(
            modifier = Modifier.matchParentSize().clearAndSetSemantics {},
            mergeDescendants = true,
            cameraPositionState = camera,
            uiSettings = guideMapUiSettings,
            mapColorScheme = exploreMapColorScheme(MaterialTheme.isDark),
            onMapLoaded = { if (readiness == MapReadiness.LOADING) readiness = MapReadiness.READY },
        ) {
          GuideMonumentPin(location)
        }
      }

      if (ready) {
        val action = stringResource(R.string.monument_detail_open_map, monumentName)
        // A sibling above the MapView receives taps while the parent guide handles vertical drags.
        Box(
            Modifier.matchParentSize()
                .testTag(C.Tag.monument_detail_map)
                .semantics { contentDescription = action }
                .clickable(role = Role.Button, onClick = onOpen)
        )
      } else {
        // Keep the MapView measured so it can load tiles, with no visible or tappable map yet.
        Box(
            Modifier.matchParentSize()
                .background(MaterialTheme.guidePanel)
                .testTag(C.Tag.monument_detail_map_probe)
        )
      }
    }
  }
}
