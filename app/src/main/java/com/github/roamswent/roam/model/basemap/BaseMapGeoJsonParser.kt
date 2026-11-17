// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

import com.github.roamswent.roam.model.location.Coordinates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the polygons of a GeoJSON FeatureCollection. Only Polygon and MultiPolygon geometries are
 * kept; other geometries and all properties are ignored. GeoJSON positions are
 * [longitude, latitude].
 */
object BaseMapGeoJsonParser {

  /**
   * @throws IllegalArgumentException if [geoJson] is not a FeatureCollection of valid positions.
   */
  fun parse(geoJson: String): List<BaseMapPolygon> {
    val root = runCatching {
      Json.parseToJsonElement(geoJson).jsonObject
    }
        .getOrElse { throw IllegalArgumentException("Base map is not a JSON object", it) }
    require(root.string("type") == FEATURE_COLLECTION) { "Base map is not a FeatureCollection" }
    val features = root["features"] as? JsonArray ?: return emptyList()
    return features.flatMap { feature ->
      val geometry = (feature as? JsonObject)?.get("geometry") as? JsonObject
      geometry?.let(::polygonsOf).orEmpty()
    }
  }

  private fun polygonsOf(geometry: JsonObject): List<BaseMapPolygon> {
    val coordinates = geometry["coordinates"] ?: return emptyList()
    return when (geometry.string("type")) {
      POLYGON -> listOfNotNull(polygonOf(coordinates))
      MULTI_POLYGON -> coordinates.jsonArray.mapNotNull(::polygonOf)
      else -> emptyList()
    }
  }

  /** A polygon is a list of rings: the outline first, then its holes. */
  private fun polygonOf(rings: JsonElement): BaseMapPolygon? {
    val parsed = rings.jsonArray.map(::ringOf)
    // The first ring is the outline; a hole must never be promoted to outline if it is invalid.
    val outline = parsed.firstOrNull()?.takeIf { it.size >= MIN_RING_SIZE } ?: return null
    return BaseMapPolygon(
        outline = outline,
        holes = parsed.drop(1).filter { it.size >= MIN_RING_SIZE },
    )
  }

  private fun ringOf(ring: JsonElement): List<Coordinates> =
      ring.jsonArray.map { position ->
        val values = position.jsonArray
        require(values.size >= 2) { "A position needs a longitude and a latitude" }
        Coordinates(
            latitude = values[1].jsonPrimitive.double,
            longitude = values[0].jsonPrimitive.double,
        )
      }

  private fun JsonObject.string(key: String): String? =
      (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

  private const val FEATURE_COLLECTION = "FeatureCollection"
  private const val POLYGON = "Polygon"
  private const val MULTI_POLYGON = "MultiPolygon"

  /** A closed ring needs three distinct points plus the closing one. */
  private const val MIN_RING_SIZE = 4
}
