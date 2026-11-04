/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.permission.LocationPermissionController
import com.github.roamswent.roam.ui.theme.HomeTextPrimary
import com.github.roamswent.roam.ui.theme.ScreenBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBackClick: () -> Unit,
    exploreViewModel: ExploreViewModel,
    locationPermissionController: LocationPermissionController,
) {
  val permissionState by locationPermissionController.state.collectAsState()

  LaunchedEffect(permissionState) {
    exploreViewModel.onPermissionStateChanged(
        isGranted = permissionState.isGranted,
        decision = permissionState.decision,
    )
  }

  Scaffold(
      containerColor = ScreenBackground,
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.explore_title),
                  color = HomeTextPrimary,
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
    )
  }
}
