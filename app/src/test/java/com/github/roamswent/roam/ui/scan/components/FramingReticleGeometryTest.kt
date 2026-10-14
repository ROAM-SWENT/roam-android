package com.github.roamswent.roam.ui.scan.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingReticleGeometryTest {
  private val inset = 1.5f

  @Test
  fun returnsFourBracketsInCornerOrder() {
    val brackets = bracketGeometry(width = 1080f, height = 1080f, arm = 32f, inset = inset)

    assertEquals(4, brackets.size)
    val left = inset
    val top = inset
    val right = 1080f - inset
    val bottom = 1080f - inset
    for (point in brackets[0].points) {
      assertTrue(point.first == left || point.second == top)
    }
    for (point in brackets[1].points) {
      assertTrue(point.first == right || point.second == top)
    }
    for (point in brackets[2].points) {
      assertTrue(point.first == right || point.second == bottom)
    }
    for (point in brackets[3].points) {
      assertTrue(point.first == left || point.second == bottom)
    }
    assertTrue(brackets[0].points.any { it.first == left })
    assertTrue(brackets[0].points.any { it.second == top })
    assertTrue(brackets[1].points.any { it.first == right })
    assertTrue(brackets[1].points.any { it.second == top })
    assertTrue(brackets[2].points.any { it.first == right })
    assertTrue(brackets[2].points.any { it.second == bottom })
    assertTrue(brackets[3].points.any { it.first == left })
    assertTrue(brackets[3].points.any { it.second == bottom })
  }

  @Test
  fun eachBracketHasExactlyThreePoints() {
    val brackets = bracketGeometry(width = 1080f, height = 1080f, arm = 32f, inset = inset)

    for (bracket in brackets) {
      assertEquals(3, bracket.points.size)
    }
  }

  @Test
  fun coordinatesStayWithinBounds() {
    val sizes = listOf(1080f to 1080f, 720f to 1080f)
    for ((width, height) in sizes) {
      val brackets = bracketGeometry(width, height, arm = 32f, inset = inset)

      for (bracket in brackets) {
        for ((x, y) in bracket.points) {
          assertTrue("x=$x outside [0, $width]", x in 0f..width)
          assertTrue("y=$y outside [0, $height]", y in 0f..height)
        }
      }
    }
  }

  @Test
  fun oversizedArmStaysWithinBounds() {
    for (arm in listOf(540f, 600f)) {
      val brackets = bracketGeometry(width = 1080f, height = 1080f, arm = arm, inset = inset)

      for (bracket in brackets) {
        for ((x, y) in bracket.points) {
          assertTrue("x=$x outside [0, 1080]", x in 0f..1080f)
          assertTrue("y=$y outside [0, 1080]", y in 0f..1080f)
        }
      }
    }
  }

  @Test
  fun degenerateSizesAreNotClamped() {
    val zero = bracketGeometry(width = 0f, height = 0f, arm = 32f, inset = inset)
    assertEquals(
        listOf(1.5f to 33.5f, 1.5f to 1.5f, 33.5f to 1.5f),
        zero[0].points,
    )
    assertEquals(
        listOf(-1.5f to 33.5f, -1.5f to 1.5f, -33.5f to 1.5f),
        zero[1].points,
    )
    assertEquals(
        listOf(-1.5f to -33.5f, -1.5f to -1.5f, -33.5f to -1.5f),
        zero[2].points,
    )
    assertEquals(
        listOf(1.5f to -33.5f, 1.5f to -1.5f, 33.5f to -1.5f),
        zero[3].points,
    )

    val tiny = bracketGeometry(width = 20f, height = 20f, arm = 32f, inset = inset)
    assertEquals(
        listOf(1.5f to 33.5f, 1.5f to 1.5f, 33.5f to 1.5f),
        tiny[0].points,
    )
    assertEquals(
        listOf(18.5f to 33.5f, 18.5f to 1.5f, -13.5f to 1.5f),
        tiny[1].points,
    )
    assertEquals(
        listOf(18.5f to -13.5f, 18.5f to 18.5f, -13.5f to 18.5f),
        tiny[2].points,
    )
    assertEquals(
        listOf(1.5f to -13.5f, 1.5f to 18.5f, 33.5f to 18.5f),
        tiny[3].points,
    )
  }

  @Test
  fun typicalSizeMatchesExpectedSnapshot() {
    val brackets = bracketGeometry(width = 1000f, height = 1000f, arm = 100f, inset = 0f)

    assertEquals(listOf(0f to 100f, 0f to 0f, 100f to 0f), brackets[0].points)
    assertEquals(listOf(1000f to 100f, 1000f to 0f, 900f to 0f), brackets[1].points)
    assertEquals(listOf(1000f to 900f, 1000f to 1000f, 900f to 1000f), brackets[2].points)
    assertEquals(listOf(0f to 900f, 0f to 1000f, 100f to 1000f), brackets[3].points)
  }

  @Test
  fun repeatedCallsReturnIndependentEqualInstances() {
    val first = bracketGeometry(width = 1080f, height = 1080f, arm = 32f, inset = inset)
    val second = bracketGeometry(width = 1080f, height = 1080f, arm = 32f, inset = inset)

    assertEquals(first, second)
    assertNotSame(first, second)
    assertNotSame(first[0].points, second[0].points)
  }
}
