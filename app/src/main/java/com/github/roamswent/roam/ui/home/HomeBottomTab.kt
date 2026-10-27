package com.github.roamswent.roam.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C

enum class HomeBottomTab(val labelRes: Int, val icon: ImageVector, val tag: String) {
  HOME(R.string.home_tab_home, Icons.Outlined.Home, C.Tag.home_tab_home),
  SCAN(R.string.home_tab_scan, Icons.Filled.CenterFocusStrong, C.Tag.home_tab_scan),
  PROFILE(R.string.home_tab_profile, Icons.Outlined.Person, C.Tag.home_tab_profile),
}
