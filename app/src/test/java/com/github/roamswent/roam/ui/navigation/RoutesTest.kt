package com.github.roamswent.roam.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
  @Test
  fun routesMatchRegisteredDestinations() {
    assertEquals("home", Routes.Home)
    assertEquals("scan", Routes.Scan)
  }
}
