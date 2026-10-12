package com.github.roamswent.roam.model.ai

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseGeminiClientTest {

  @Test
  fun mapsNetworkFailuresToTransient() {
    val mapped = discoveryErrorFor(IOException("unexpected end of stream"))
    assertTrue(mapped is TransientException)
  }

  @Test
  fun rethrowsUnknownErrorsSoBugsStayVisible() {
    val bug = IllegalStateException("bug in our code")
    val thrown = assertThrows(IllegalStateException::class.java) { throw discoveryErrorFor(bug) }
    assertEquals(bug, thrown)
  }
}
