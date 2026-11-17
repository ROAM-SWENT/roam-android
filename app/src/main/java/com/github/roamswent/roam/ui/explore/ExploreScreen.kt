/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.basemap.BaseMapPolygon
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.theme.CollectionPlaceholder
import com.github.roamswent.roam.ui.theme.CollectionPlaceholderIcon
import com.github.roamswent.roam.ui.theme.OnBrand
import com.github.roamswent.roam.ui.theme.ScanTeal
import com.github.roamswent.roam.ui.theme.cardContainer
import com.github.roamswent.roam.ui.theme.isDark
import com.github.roamswent.roam.ui.theme.offlineMapBorder
import com.github.roamswent.roam.ui.theme.offlineMapLand
import com.github.roamswent.roam.ui.theme.offlineMapWater
import com.github.roamswent.roam.ui.theme.screenBackground
import com.github.roamswent.roam.ui.theme.secondaryText
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState

private fun Coordinates.toLatLng() = LatLng(latitude, longitude)

internal fun exploreMapColorScheme(isDark: Boolean): ComposeMapColorScheme =
    if (isDark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT

/** A standard marker drawn on the Explore map, identified by a [key] unique within the map. */
internal data class ExploreMapMarker(
    val key: String,
    val coordinates: Coordinates,
    val title: String,
)

/** Key of the user's location marker; monument keys are prefixed so they can never collide. */
internal const val USER_LOCATION_MARKER_KEY = "user_location"

/**
 * Lists the markers of the Explore map: the user's location first when it is known, then one marker
 * per saved monument, titled with the monument name.
 */
internal fun exploreMapMarkers(
    userLocation: Coordinates?,
    userLocationTitle: String,
    monuments: List<ExploreMonument>,
): List<ExploreMapMarker> =
    listOfNotNull(
        userLocation?.let { ExploreMapMarker(USER_LOCATION_MARKER_KEY, it, userLocationTitle) }
    ) + monuments.map { ExploreMapMarker("monument_${it.guideId}", it.coordinates, it.name) }

/** The user's coordinates when [state] has them, so the map can draw without a location. */
internal fun exploreUserLocation(state: ExploreUiState): Coordinates? =
    (state as? ExploreUiState.LocationAvailable)?.coordinates

internal fun selectedMonumentForId(
    monuments: List<ExploreMonument>,
    selectedMonumentId: String?,
): ExploreMonument? = monuments.firstOrNull { it.guideId == selectedMonumentId }

internal fun selectedMonumentIdForMarkerKey(markerKey: String): String? =
    markerKey.takeUnless { it == USER_LOCATION_MARKER_KEY }?.removePrefix("monument_")

internal fun updateSelectedMonumentId(
    monuments: List<ExploreMonument>,
    selectedMonumentId: String?,
): String? = selectedMonumentId?.takeIf { selectedId -> monuments.any { it.guideId == selectedId } }

/** Mercator maps stop around 85 degrees, so the sea is drawn up to there. */
private const val OFFLINE_SEA_MAX_LATITUDE = 85.0

/** Longitude width of each sea band; under 180 degrees so no band is drawn the short way round. */
private const val OFFLINE_SEA_BAND_WIDTH = 90.0

/**
 * The sea of the offline base map, as rectangles that cover the world together. A single rectangle
 * cannot be used: Google Maps joins two points by the shorter way round the globe.
 */
internal fun offlineSeaBands(): List<List<Coordinates>> =
    generateSequence(-180.0) { it + OFFLINE_SEA_BAND_WIDTH }
        .takeWhile { it < 180.0 }
        .map { west ->
          val east = west + OFFLINE_SEA_BAND_WIDTH
          listOf(
              Coordinates(-OFFLINE_SEA_MAX_LATITUDE, west),
              Coordinates(-OFFLINE_SEA_MAX_LATITUDE, east),
              Coordinates(OFFLINE_SEA_MAX_LATITUDE, east),
              Coordinates(OFFLINE_SEA_MAX_LATITUDE, west),
          )
        }
        .toList()

/**
 * Map properties for the connection state: offline, the Google tiles are switched off so the
 * bundled base map is the only background, rather than a patchwork of whatever tiles were cached.
 */
internal fun exploreMapProperties(isOnline: Boolean): MapProperties =
    MapProperties(mapType = if (isOnline) MapType.NORMAL else MapType.NONE)

internal fun handleExploreMarkerClick(
    markerKey: String,
    onMonumentSelected: (String) -> Unit,
): Boolean =
    selectedMonumentIdForMarkerKey(markerKey)?.let {
      onMonumentSelected(it)
      true
    } ?: false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBackClick: () -> Unit,
    exploreViewModel: ExploreViewModel,
    locationPermissionController: LocationPermissionController,
    isOnline: Boolean = true,
) {
  val uiState by exploreViewModel.uiState.collectAsState()
  val offlineBaseMap by exploreViewModel.offlineBaseMap.collectAsState()

  LaunchedEffect(isOnline) { if (!isOnline) exploreViewModel.loadOfflineBaseMap() }
  val permissionState by locationPermissionController.state.collectAsState()

  LaunchedEffect(permissionState) {
    exploreViewModel.onPermissionStateChanged(
        isGranted = permissionState.isGranted,
        decision = permissionState.decision,
    )
  }

  Scaffold(
      containerColor = MaterialTheme.screenBackground,
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
      val monuments by exploreViewModel.monuments.collectAsState()
      val userLocation = exploreUserLocation(uiState)
      val cameraFocus = exploreCameraFocus(userLocation, monuments)
      val cameraPositionState = rememberCameraPositionState {
        position = exploreCameraTarget(userLocation, monuments).toCameraPosition()
      }
      // Reframe only when the focus changes (the location arrives, the first monuments load), so
      // the user's panning is kept when monuments are added and across configuration changes.
      var framedFocus by rememberSaveable { mutableStateOf(cameraFocus) }
      LaunchedEffect(cameraFocus) {
        if (framedFocus != cameraFocus) {
          cameraPositionState.position =
              exploreCameraTarget(userLocation, monuments).toCameraPosition()
          framedFocus = cameraFocus
        }
      }
      val markers =
          exploreMapMarkers(
              userLocation = userLocation,
              userLocationTitle = stringResource(R.string.explore_user_location),
              monuments = monuments,
          )
      var selectedMonumentId by remember { mutableStateOf<String?>(null) }
      LaunchedEffect(monuments, selectedMonumentId) {
        selectedMonumentId = updateSelectedMonumentId(monuments, selectedMonumentId)
      }
      val selectedMonument = selectedMonumentForId(monuments, selectedMonumentId)
      var isMonumentExpanded by remember(selectedMonumentId) { mutableStateOf(false) }

      GoogleMap(
          modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.explore_map },
          cameraPositionState = cameraPositionState,
          properties = exploreMapProperties(isOnline),
          mapColorScheme = exploreMapColorScheme(MaterialTheme.isDark),
      ) {
        if (!isOnline) OfflineBaseMap(offlineBaseMap)
        markers.forEach { marker ->
          key(marker.key) {
            val position = marker.coordinates.toLatLng()
            val markerState = remember(position) { MarkerState(position = position) }
            Marker(
                state = markerState,
                title = marker.title,
                onClick = { handleExploreMarkerClick(marker.key) { selectedMonumentId = it } },
            )
          }
        }
      }
      ExploreLocationStatus(
          state = uiState,
          onRetry = exploreViewModel::retryLocation,
          modifier = Modifier.align(Alignment.TopCenter),
      )
      selectedMonument?.let { monument ->
        ExploreMonumentCard(
            monument = monument,
            onDismiss = { selectedMonumentId = null },
            isExpanded = isMonumentExpanded,
            onExpand = { isMonumentExpanded = true },
            onMinimize = { isMonumentExpanded = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
  }
}

/** Draws the bundled world map, sea then land, with theme colors. Markers stay on top of it. */
@Composable
@GoogleMapComposable
private fun OfflineBaseMap(countries: List<BaseMapPolygon>) {
  val sea = remember { offlineSeaBands().map { band -> band.map { it.toLatLng() } } }
  val land =
      remember(countries) {
        countries.map { country ->
          country.outline.map { it.toLatLng() } to
              country.holes.map { hole -> hole.map { it.toLatLng() } }
        }
      }
  val seaColor = MaterialTheme.offlineMapWater
  val landColor = MaterialTheme.offlineMapLand
  val borderColor = MaterialTheme.offlineMapBorder
  sea.forEach { band ->
    Polygon(points = band, fillColor = seaColor, strokeColor = seaColor, strokeWidth = 0f)
  }
  land.forEach { (outline, holes) ->
    Polygon(
        points = outline,
        holes = holes,
        fillColor = landColor,
        strokeColor = borderColor,
        strokeWidth = OFFLINE_BORDER_WIDTH_PX,
        zIndex = 1f,
    )
  }
}

/** Border width in pixels, as Google Maps expects; thin enough for small countries. */
private const val OFFLINE_BORDER_WIDTH_PX = 2f

private fun ExploreCameraTarget.toCameraPosition(): CameraPosition =
    CameraPosition.fromLatLngZoom(center.toLatLng(), zoom)

/**
 * Explains, over the map, why the user's location is not shown; draws nothing once it is. The map
 * and the saved monuments stay usable in every state.
 */
@Composable
private fun ExploreLocationStatus(
    state: ExploreUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  when (state) {
    is ExploreUiState.LocationAvailable -> Unit
    ExploreUiState.Loading ->
        ExploreStateCard(
            message = stringResource(R.string.explore_loading_location),
            modifier = modifier,
        )
    is ExploreUiState.PermissionRequired ->
        ExploreStateCard(
            message = stringResource(R.string.explore_permission_required),
            modifier = modifier,
        )
    ExploreUiState.LocationUnavailable ->
        ExploreStateCard(
            message = stringResource(R.string.explore_location_unavailable),
            actionLabel = stringResource(R.string.explore_retry),
            onAction = onRetry,
            showActionIcon = true,
            modifier = modifier,
        )
  }
}

@Composable
internal fun ExploreMonumentCard(
    modifier: Modifier = Modifier,
    monument: ExploreMonument,
    onDismiss: () -> Unit,
    isExpanded: Boolean = false,
    onExpand: () -> Unit = {},
    onMinimize: () -> Unit = {},
) {
  Card(
      modifier =
          modifier
              .fillMaxWidth()
              .then(if (isExpanded) Modifier.fillMaxHeight(0.75f) else Modifier)
              .padding(16.dp)
              .then(if (isExpanded) Modifier else Modifier.clickable(onClick = onExpand)),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.cardContainer),
  ) {
    if (isExpanded) {
      Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
          Text(
              text = monument.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onMinimize, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.explore_minimize_monument_description),
            )
          }
        }
        ExploreMonumentPhoto(
            monument = monument,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(220.dp),
        )
        Text(
            text = monument.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
      }
    } else {
      Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.Top,
      ) {
        ExploreMonumentPhoto(monument)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
          Text(
              text = monument.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
              text = monument.description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.explore_close_monument_description),
          )
        }
      }
    }
  }
}

@Composable
private fun ExploreMonumentPhoto(
    monument: ExploreMonument,
    modifier: Modifier = Modifier.size(96.dp),
) {
  val photoModifier = modifier.clip(RoundedCornerShape(12.dp))
  if (monument.photoBytes.isEmpty()) {
    ExplorePhotoPlaceholder(photoModifier, C.Tag.explore_monument_photo_placeholder)
    return
  }

  var photoState by
      remember(monument.photoBytes) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
      }
  Box(contentAlignment = Alignment.Center, modifier = photoModifier) {
    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(monument.photoBytes)
                .crossfade(true)
                .build(),
        contentDescription =
            stringResource(R.string.explore_monument_photo_description, monument.name),
        contentScale = ContentScale.Crop,
        onState = { photoState = it },
        modifier =
            Modifier.matchParentSize()
                .background(
                    if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface
                    else CollectionPlaceholder
                )
                .semantics { testTag = C.Tag.explore_monument_photo },
    )
    when (photoState) {
      is AsyncImagePainter.State.Empty,
      is AsyncImagePainter.State.Loading ->
          CircularProgressIndicator(
              modifier =
                  Modifier.size(24.dp).semantics { testTag = C.Tag.explore_monument_photo_loading }
          )
      is AsyncImagePainter.State.Error ->
          ExplorePhotoPlaceholder(
              Modifier.matchParentSize(),
              C.Tag.explore_monument_photo_error,
          )
      is AsyncImagePainter.State.Success -> Unit
    }
  }
}

@Composable
private fun ExplorePhotoPlaceholder(modifier: Modifier, tag: String) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          modifier
              .background(
                  if (MaterialTheme.isDark) MaterialTheme.colorScheme.surface
                  else CollectionPlaceholder
              )
              .semantics { testTag = tag },
  ) {
    Icon(
        imageVector = Icons.Filled.AccountBalance,
        contentDescription = null,
        tint =
            if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant
            else CollectionPlaceholderIcon,
        modifier = Modifier.size(32.dp),
    )
  }
}

@Composable
private fun ExploreStateCard(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showActionIcon: Boolean = false,
) {
  Card(
      modifier =
          modifier.fillMaxWidth().padding(20.dp).semantics {
            testTag = C.Tag.explore_location_status
          },
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.cardContainer),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(text = message, color = MaterialTheme.secondaryText)
      if (actionLabel != null && onAction != null) {
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ScanTeal,
                    contentColor = OnBrand,
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
