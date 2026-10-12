package com.github.roamswent.roam.model.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiscoveryModelsTest {

  @Test
  fun imagePayloadsWithEqualContentAreEqual() {
    val first = ImagePayload(byteArrayOf(1, 2, 3), "image/jpeg")
    val second = ImagePayload(byteArrayOf(1, 2, 3), "image/jpeg")
    assertEquals(first, second)
    assertEquals(first.hashCode(), second.hashCode())
  }

  @Test
  fun imagePayloadsWithDifferentBytesAreNotEqual() {
    val first = ImagePayload(byteArrayOf(1, 2, 3), "image/jpeg")
    val second = ImagePayload(byteArrayOf(1, 2, 4), "image/jpeg")
    assertNotEquals(first, second)
  }

  @Test
  fun imagePayloadsWithDifferentMimeTypesAreNotEqual() {
    val first = ImagePayload(byteArrayOf(1, 2, 3), "image/jpeg")
    val second = ImagePayload(byteArrayOf(1, 2, 3), "image/png")
    assertNotEquals(first, second)
  }

  @Test
  fun discoveryRequestsBuiltFromSeparateIdenticalByteArraysAreEqual() {
    val preferences = UserPreferences("en")
    val first =
        DiscoveryRequest(ImagePayload(byteArrayOf(9, 9, 9), "image/jpeg"), null, preferences)
    val second =
        DiscoveryRequest(ImagePayload(byteArrayOf(9, 9, 9), "image/jpeg"), null, preferences)
    assertEquals(first, second)
  }
}
