package com.github.roamswent.roam.ui.common

import com.github.roamswent.roam.model.location.Coordinates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val discoveryDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

/** Formats [epochMillis] like "12 Oct 2026", in [zone] and [locale]. */
internal fun formatDiscoveryDate(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String =
    discoveryDateFormatter.withLocale(locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** Formats [coordinates] like "48.8584° N, 2.2945° E". */
internal fun formatCoordinates(coordinates: Coordinates): String {
  val latitudeHemisphere = if (coordinates.latitude < 0) "S" else "N"
  val longitudeHemisphere = if (coordinates.longitude < 0) "W" else "E"
  return String.format(
      Locale.US,
      "%.4f° %s, %.4f° %s",
      abs(coordinates.latitude),
      latitudeHemisphere,
      abs(coordinates.longitude),
      longitudeHemisphere,
  )
}
