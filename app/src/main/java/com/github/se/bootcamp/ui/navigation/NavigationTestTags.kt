package com.github.se.bootcamp.ui.navigation

object NavigationTestTags {
  const val BOTTOM_NAVIGATION_MENU = "BottomNavigationMenu"
  const val GO_BACK_BUTTON = "GoBackButton"
  const val TOP_BAR_TITLE = "TopBarTitle"
  const val OVERVIEW_TAB = "OverviewTab"
  const val MAP_TAB = "MapTab"

  fun getTabTestTag(tab: Tab): String =
      when (tab) {
        is Tab.Overview -> OVERVIEW_TAB
        is Tab.Map -> MAP_TAB
      }
}
