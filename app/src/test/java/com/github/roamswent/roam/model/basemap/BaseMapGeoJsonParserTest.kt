// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseMapGeoJsonParserTest {
  private val square = "[[0,0],[10,0],[10,5],[0,5],[0,0]]"
  private val hole = "[[2,1],[3,1],[3,2],[2,1]]"

  private fun collection(vararg geometries: String) =
      """{"type":"FeatureCollection","features":[${
        geometries.joinToString(",") { """{"type":"Feature","properties":{"NAME":"x"},"geometry":$it}""" }
      }]}"""

  @Test
  fun polygonPositionsAreReadAsLongitudeThenLatitude() {
    val polygons =
        BaseMapGeoJsonParser.parse(collection("""{"type":"Polygon","coordinates":[$square]}"""))

    assertEquals(1, polygons.size)
    assertEquals(Coordinates(latitude = 0.0, longitude = 10.0), polygons[0].outline[1])
    assertEquals(Coordinates(latitude = 5.0, longitude = 10.0), polygons[0].outline[2])
    assertTrue(polygons[0].holes.isEmpty())
  }

  @Test
  fun multiPolygonGivesOnePolygonPerPartWithItsHoles() {
    val polygons =
        BaseMapGeoJsonParser.parse(
            collection("""{"type":"MultiPolygon","coordinates":[[$square,$hole],[$square]]}""")
        )

    assertEquals(2, polygons.size)
    assertEquals(1, polygons[0].holes.size)
    assertEquals(Coordinates(latitude = 1.0, longitude = 2.0), polygons[0].holes[0][0])
    assertTrue(polygons[1].holes.isEmpty())
  }

  @Test
  fun otherGeometriesAndFeaturesWithoutGeometryAreIgnored() {
    val geoJson =
        """{"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]}},
          {"type":"Feature","geometry":null},
          {"type":"Feature"},
          {"type":"Feature","geometry":{"type":"Polygon","coordinates":[$square]}}
        ]}"""

    assertEquals(1, BaseMapGeoJsonParser.parse(geoJson).size)
  }

  @Test
  fun ringsTooShortToEncloseAnAreaAreDropped() {
    val polygons =
        BaseMapGeoJsonParser.parse(
            collection(
                """{"type":"Polygon","coordinates":[[[0,0],[1,1],[0,0]]]}""",
                """{"type":"Polygon","coordinates":[$square,[[1,1],[2,2],[1,1]]]}""",
            )
        )

    assertEquals(1, polygons.size)
    assertTrue(polygons[0].holes.isEmpty())
  }

  @Test
  fun collectionWithoutFeaturesIsEmpty() {
    assertTrue(BaseMapGeoJsonParser.parse("""{"type":"FeatureCollection"}""").isEmpty())
  }

  @Test(expected = IllegalArgumentException::class)
  fun anythingButAFeatureCollectionIsRejected() {
    BaseMapGeoJsonParser.parse("""{"type":"Polygon","coordinates":[$square]}""")
  }

  @Test(expected = IllegalArgumentException::class)
  fun invalidJsonIsRejected() {
    BaseMapGeoJsonParser.parse("not json")
  }

  @Test(expected = IllegalArgumentException::class)
  fun positionsOutOfRangeAreRejected() {
    BaseMapGeoJsonParser.parse(
        collection("""{"type":"Polygon","coordinates":[[[0,0],[200,0],[1,1],[0,0]]]}""")
    )
  }
}
