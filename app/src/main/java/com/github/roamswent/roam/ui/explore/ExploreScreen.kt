/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.theme.HomeTextSecondary
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.ScreenBackground
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

/** A standard marker drawn on the Explore map, identified by a [key] unique within the map. */
internal data class ExploreMapMarker(
    val key: String,
    val coordinates: Coordinates,
    val title: String,
)

/** Key of the user's location marker; monument keys are prefixed so they can never collide. */
internal const val USER_LOCATION_MARKER_KEY = "user_location"

/**
 * Lists the markers of the Explore map: the user's location first, then one marker per saved
 * monument, titled with the monument name.
 */
internal fun exploreMapMarkers(
    userLocation: Coordinates,
    userLocationTitle: String,
    monuments: List<ExploreMonument>,
): List<ExploreMapMarker> =
    listOf(ExploreMapMarker(USER_LOCATION_MARKER_KEY, userLocation, userLocationTitle)) +
        monuments.map { ExploreMapMarker("monument_${it.guideId}", it.coordinates, it.name) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBackClick: () -> Unit,
    exploreViewModel: ExploreViewModel,
    locationPermissionController: LocationPermissionController,
) {
  val uiState by exploreViewModel.uiState.collectAsState()
  val permissionState by locationPermissionController.state.collectAsState()

  LaunchedEffect(permissionState) {
    exploreViewModel.onPermissionStateChanged(
        isGranted = permissionState.isGranted,
        decision = permissionState.decision,
    )
  }

  Scaffold(
      containerColor = ScreenBackground,
      contentWindowInsets =
          ScaffoldDefaults.contentWindowInsets.only(
              WindowInsetsSides.Top + WindowInsetsSides.Horizontal
          ),
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.explore_title),
                  color = MaterialTheme.colorScheme.onSurface,
                  fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.explore_back_description),
                )
              }
            },
        )
      },
  ) { paddingValues ->
    Box(
        modifier =
            Modifier.fillMaxSize().padding(paddingValues).semantics {
              testTag = C.Tag.explore_screen_container
            }
    ) {
      when (val state = uiState) {
        is ExploreUiState.LocationAvailable -> {
          val monuments by exploreViewModel.monuments.collectAsState()
          val coordinates = state.coordinates
          val userLocation = coordinates.toLatLng()
          val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(userLocation, 14f)
          }
          val markers =
              exploreMapMarkers(
                  userLocation = coordinates,
                  userLocationTitle = stringResource(R.string.explore_user_location),
                  monuments = monuments,
              )
          GoogleMap(
              modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.explore_map },
              cameraPositionState = cameraPositionState,
          ) {
            markers.forEach { marker ->
              key(marker.key) {
                val position = marker.coordinates.toLatLng()
                val markerState = remember(position) { MarkerState(position = position) }
                Marker(state = markerState, title = marker.title)
              }
            }
          }
        }
        ExploreUiState.Loading ->
            ExploreStateCard(message = stringResource(R.string.explore_loading_location))
        is ExploreUiState.PermissionRequired ->
            ExploreStateCard(message = stringResource(R.string.explore_permission_required))
        ExploreUiState.LocationUnavailable ->
            ExploreStateCard(
                message = stringResource(R.string.explore_location_unavailable),
                actionLabel = stringResource(R.string.explore_retry),
                onAction = exploreViewModel::retryLocation,
                showActionIcon = true,
            )
      }
    }
  }
}

@Composable
private fun ExploreStateCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showActionIcon: Boolean = false,
) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(text = message, color = HomeTextSecondary)
      if (actionLabel != null && onAction != null) {
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ScanTeal,
                    contentColor = Color.White,
                ),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (showActionIcon) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
              Spacer(Modifier.size(8.dp))
            }
            Text(actionLabel, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}
