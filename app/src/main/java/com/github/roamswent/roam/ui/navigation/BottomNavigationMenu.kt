package com.github.se.bootcamp.ui.navigation

//noinspection UsingMaterialAndMaterial3Libraries
//noinspection UsingMaterialAndMaterial3Libraries

import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

sealed class Tab(val name: String, val icon: ImageVector, val destination: Screen) {
  object Overview : Tab("Overview", Icons.Outlined.Menu, Screen.Overview)

  object Map : Tab("Map", Icons.Outlined.Place, Screen.Map)
}

private val tabs =
    listOf(
        Tab.Overview,
        Tab.Map,
    )

@Composable
fun BottomNavigationMenu(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  BottomNavigation(
      modifier =
          modifier.fillMaxWidth().height(60.dp).testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
      backgroundColor = MaterialTheme.colorScheme.surface,
      content = {
        tabs.forEach { tab ->
          BottomNavigationItem(
              icon = { Icon(tab.icon, contentDescription = null) },
              label = { Text(tab.name) },
              selected = tab == selectedTab,
              onClick = { onTabSelected(tab) },
              modifier =
                  Modifier.clip(RoundedCornerShape(50.dp))
                      .testTag(NavigationTestTags.getTabTestTag(tab)))
        }
      },
  )
}
