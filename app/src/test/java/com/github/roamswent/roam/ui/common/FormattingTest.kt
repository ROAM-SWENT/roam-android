package com.github.roamswent.roam.ui.common

import com.github.roamswent.roam.model.location.Coordinates
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
  @Test
  fun formatsDiscoveryDateAsDayShortMonthAndYear() {
    assertEquals(
        "12 Oct 2026",
        formatDiscoveryDate(1_791_806_400_000, ZoneOffset.UTC, Locale.ENGLISH),
    )
    assertEquals(
        "08 Oct 2026",
        formatDiscoveryDate(1_791_460_800_000, ZoneOffset.UTC, Locale.ENGLISH),
    )
  }

  @Test
  fun formatsDiscoveryDateInTheGivenZone() {
    // 23:30 UTC on 11 Oct is already 12 Oct in Zurich.
    val lateEvening = 1_791_761_400_000L

    assertEquals("11 Oct 2026", formatDiscoveryDate(lateEvening, ZoneOffset.UTC, Locale.ENGLISH))
    assertEquals(
        "12 Oct 2026",
        formatDiscoveryDate(lateEvening, ZoneId.of("Europe/Zurich"), Locale.ENGLISH),
    )
  }

  @Test
  fun formatsCoordinatesWithNorthAndEastForPositiveValues() {
    assertEquals("48.8584° N, 2.2945° E", formatCoordinates(Coordinates(48.8584, 2.2945)))
  }

  @Test
  fun formatCoordinatesUsesHemispheresForNegativeValues() {
    assertEquals("33.8568° S, 151.2153° E", formatCoordinates(Coordinates(-33.8568, 151.2153)))
    assertEquals("40.6892° N, 74.0445° W", formatCoordinates(Coordinates(40.6892, -74.0445)))
  }
}
