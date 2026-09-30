package com.github.se.bootcamp.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.bootcamp.ui.map.MapScreenTestTags.getTestTagForTodoMarker
import com.github.se.bootcamp.ui.navigation.BottomNavigationMenu
import com.github.se.bootcamp.ui.navigation.NavigationActions
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.ui.navigation.Screen
import com.github.se.bootcamp.ui.navigation.Tab
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

object MapScreenTestTags {
  const val GOOGLE_MAP_SCREEN = "mapScreen"

  fun getTestTagForTodoMarker(todoId: String): String = "todoMarker_$todoId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    navigationActions: NavigationActions? = null,
) {

  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.Overview,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU))
      },
      topBar = {
        TopAppBar(
            title = { Text(Screen.Map.name, Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE)) },
        )
      },
      content = { pd ->
        // Camera position state, using the first ToDo location if available
        val cameraPositionState = rememberCameraPositionState {
          position = CameraPosition.fromLatLngZoom(uiState.target, 10f)
        }
        GoogleMap(
            modifier =
                Modifier.fillMaxSize().padding(pd).testTag(MapScreenTestTags.GOOGLE_MAP_SCREEN),
            cameraPositionState = cameraPositionState) {
              uiState.todos.forEach { todo ->
                Marker(
                    state =
                        MarkerState(
                            position = LatLng(todo.location!!.latitude, todo.location.longitude)),
                    title = todo.name,
                    snippet = todo.description,
                    tag = getTestTagForTodoMarker(todo.uid))
              }
            }
      })
}
