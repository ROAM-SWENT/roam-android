// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.guide.Guide
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionFormattingTest {
  @Test
  fun sortsNewestFirstWithUndatedGuidesLast() {
    val undated = guide("undated", null)
    val older = guide("older", 1L)
    val newer = guide("newer", 2L)

    assertEquals(
        listOf("newer", "older", "undated"),
        newestFirst(listOf(undated, older, newer)).map { it.id },
    )
  }

  private fun guide(id: String, discoveryDate: Long?) =
      Guide(
          id = id,
          photoBytes = ByteArray(0),
          monumentName = id,
          confidence = 0.9,
          history = "History",
          facts = emptyList(),
          culture = "Culture",
          discoveryDate = discoveryDate,
      )
}
