// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.explore

import com.github.roamswent.roam.model.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests how Explore resolves and retains selected monuments. */
class ExploreMapMarkersTest {
  private val userLocation = Coordinates(46.51916, 6.56673)

  /** Verifies that a selected guide ID resolves to its corresponding monument. */
  @Test
  fun selectedMonumentForIdReturnsMatchingMonument() {
    val monument = ExploreMonument("guide-1", "Eiffel Tower", userLocation)

    assertEquals(monument, selectedMonumentForId(listOf(monument), "guide-1"))
  }

  /** Verifies that a missing selected guide ID produces no selected monument. */
  @Test
  fun selectedMonumentForIdReturnsNullWhenSelectedMonumentIsMissing() {
    val monument = ExploreMonument("guide-1", "Eiffel Tower", userLocation)

    assertEquals(null, selectedMonumentForId(listOf(monument), "guide-2"))
  }

  /** Verifies that a selected monument remains selected while it is still available. */
  @Test
  fun selectedMonumentRemainsSelectedWhenStillAvailable() {
    val monument = ExploreMonument("guide-1", "Eiffel Tower", userLocation)

    assertEquals("guide-1", updateSelectedMonumentId(listOf(monument), "guide-1"))
  }

  /** Verifies that a selected monument is cleared after it disappears from the collection. */
  @Test
  fun selectedMonumentIsClearedWhenNoLongerAvailable() {
    assertEquals(null, updateSelectedMonumentId(emptyList(), "guide-1"))
  }
}
