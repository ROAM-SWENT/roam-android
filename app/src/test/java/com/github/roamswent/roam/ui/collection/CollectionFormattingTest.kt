// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.guide.Guide
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionFormattingTest {
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
