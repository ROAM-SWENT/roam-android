// Co-authored-by: Google Antigravity <noreply@google.com>
package com.github.roamswent.roam.ui.collection

import com.github.roamswent.roam.model.guide.Guide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSearchTest {
  private val eiffelTower =
      Guide(
          id = "eiffel",
          photoBytes = ByteArray(0),
          monumentName = "Eiffel Tower",
          confidence = 0.95,
          history = "Built in 1889 for the Exposition Universelle.",
          facts = listOf("330 meters tall", "Wrought-iron lattice structure"),
          culture = "Global icon of France and romantic symbol.",
          discoveryDate = 1_791_806_400_000,
      )

  private val colosseum =
      Guide(
          id = "colosseum",
          photoBytes = ByteArray(0),
          monumentName = "Colosseum",
          confidence = 0.92,
          history = "Flavian amphitheatre built of travertine limestone.",
          facts = listOf("Completed in 80 AD", "Held up to 80000 spectators"),
          culture = "Iconic symbol of imperial Rome.",
          discoveryDate = 1_791_720_000_000,
      )

  @Test
  fun tier1MatchesMonumentNameSubstring() {
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "Eiffel"),
    )
    assertEquals(listOf(eiffelTower), visibleGuides(listOf(eiffelTower, colosseum), false, "Tower"))
    assertEquals(listOf(colosseum), visibleGuides(listOf(eiffelTower, colosseum), false, "Coloss"))
  }

  @Test
  fun tier1MatchesFormattedDiscoveryDate() {
    val pastGuide = colosseum.copy(discoveryDate = 315_576_000_000L)
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, pastGuide), false, "Oct"),
    )
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, pastGuide), false, "2026"),
    )
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "12 Oct"),
    )
    assertEquals(
        listOf(colosseum),
        visibleGuides(listOf(eiffelTower, colosseum), false, "11 Oct"),
    )
  }

  @Test
  fun tier1MatchesHistoryText() {
    assertEquals(listOf(eiffelTower), visibleGuides(listOf(eiffelTower, colosseum), false, "1889"))
    assertEquals(
        listOf(colosseum),
        visibleGuides(listOf(eiffelTower, colosseum), false, "travertine"),
    )
  }

  @Test
  fun tier1MatchesFactString() {
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "330 meters"),
    )
    assertEquals(listOf(colosseum), visibleGuides(listOf(eiffelTower, colosseum), false, "80 AD"))
  }

  @Test
  fun tier1MatchesCultureText() {
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "romantic"),
    )
    assertEquals(
        listOf(colosseum),
        visibleGuides(listOf(eiffelTower, colosseum), false, "imperial"),
    )
  }

  @Test
  fun tier1CaseInsensitiveMatchesSearchableText() {
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "EIFFEL TOWER"),
    )
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "eiffel tower"),
    )
    assertEquals(
        listOf(colosseum),
        visibleGuides(listOf(eiffelTower, colosseum), false, "cOlOsSeUm"),
    )
  }

  @Test
  fun blankQueryReturnsAllGuidesUnfiltered() {
    assertEquals(
        listOf(eiffelTower, colosseum),
        visibleGuides(listOf(eiffelTower, colosseum), false, ""),
    )
    assertEquals(
        listOf(eiffelTower, colosseum),
        visibleGuides(listOf(eiffelTower, colosseum), false, "   "),
    )
  }

  @Test
  fun tier2MatchesTypoWithinThreshold() {
    assertTrue(matchesTier2Word("eifle", "Eiffel"))
    assertEquals(listOf(eiffelTower), visibleGuides(listOf(eiffelTower, colosseum), false, "eifle"))
  }

  @Test
  fun tier2RejectsDistance3Typo() {
    assertFalse(matchesTier2Word("eiffffff", "Eiffel"))
    assertFalse(matchesTier2Word("banana", "Eiffel"))
    assertTrue(visibleGuides(listOf(eiffelTower, colosseum), false, "banana").isEmpty())
  }

  @Test
  fun tier2SkipsQueriesShorterThan3CharactersEvenAtDistance1() {
    assertFalse(matchesTier2Word("ef", "of"))
    assertFalse(matchesTier2Word("ei", "eiffel"))
    assertFalse(matchesTier2Word("to", "ta"))
  }

  @Test
  fun tier2RejectsDistance2TypoFor3And4CharacterQueries() {
    assertTrue(matchesTier2Word("twer", "tower"))
    assertFalse(matchesTier2Word("tawr", "tower"))
    assertFalse(matchesTier2Word("twr", "tower"))
  }

  @Test
  fun tier2MatchesDistance2TypoFor5CharacterOrLongerQueries() {
    assertTrue(matchesTier2Word("eifle", "eiffel"))
    assertTrue(matchesTier2Word("towab", "tower"))
    assertTrue(matchesTier2Word("colosum", "colosseum"))
  }

  @Test
  fun tier2StripsWordPunctuationBeforeMatching() {
    assertTrue(matchesTier2Word("tower", "Tower,"))
    assertTrue(matchesTier2Word("paris", "(Paris)."))
    assertTrue(matchesTier2Word("twer", "Tower!"))
  }

  @Test
  fun numericQuerySkipsTier2FuzzyMatchingButMatchesExactTier1() {
    val guide2024 = colosseum.copy(discoveryDate = 1_732_000_000_000L)
    assertFalse(matchesTier2Word("2026", "2024"))
    assertFalse(matchesTier2Word("2024", "2026"))
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, guide2024), false, "2026"),
    )
    assertEquals(
        listOf(guide2024),
        visibleGuides(listOf(eiffelTower, guide2024), false, "2024"),
    )
  }

  @Test
  fun alphabeticQueryPreservesTier2FuzzyMatchingThresholds() {
    assertTrue(matchesTier2Word("eifle", "Eiffel"))
    assertEquals(
        listOf(eiffelTower),
        visibleGuides(listOf(eiffelTower, colosseum), false, "eifle"),
    )
    assertTrue(matchesTier2Word("twer", "tower"))
    assertTrue(matchesTier2Word("towab", "tower"))
  }

  @Test
  fun mixedAlphanumericQueryContainingDigitSkipsTier2FuzzyMatching() {
    val tourGuide = colosseum.copy(history = "Official tour2025 celebration")
    assertFalse(matchesTier2Word("tour2026", "tour2025"))
    assertFalse(matchesTier2Word("eiffel1", "eiffel2"))
    assertFalse(matchesTier2Word("eiffel1", "eiffel"))
    assertTrue(visibleGuides(listOf(tourGuide), false, "tour2026").isEmpty())
    assertEquals(listOf(tourGuide), visibleGuides(listOf(tourGuide), false, "tour2025"))
  }

  @Test
  fun favoritesOnlyWithQueryFiltersWithinFavoritesOnly() {
    val favoriteEiffel = eiffelTower.copy(isFavorite = true)
    val favoriteColosseum = colosseum.copy(isFavorite = true)
    val nonFavoriteColosseum = colosseum.copy(isFavorite = false)

    assertEquals(
        listOf(favoriteEiffel),
        visibleGuides(listOf(favoriteEiffel, favoriteColosseum), true, "Eiffel"),
    )
    assertEquals(
        emptyList<Guide>(),
        visibleGuides(listOf(favoriteEiffel, nonFavoriteColosseum), true, "Colosseum"),
    )
  }

  @Test
  fun emptyQueryWithFavoritesOnlyPreservesPreviousBehavior() {
    val favoriteEiffel = eiffelTower.copy(isFavorite = true)
    val nonFavoriteColosseum = colosseum.copy(isFavorite = false)

    assertEquals(
        listOf(favoriteEiffel),
        visibleGuides(listOf(favoriteEiffel, nonFavoriteColosseum), true, ""),
    )
  }

  @Test
  fun newestFirstOrderingPreservedAfterQueryFiltering() {
    val undated =
        eiffelTower.copy(
            id = "undated",
            monumentName = "Eiffel Tower Replica",
            discoveryDate = null,
        )
    val older =
        colosseum.copy(id = "older", history = "Eiffel inspired structure", discoveryDate = 1_000L)
    val newer = eiffelTower.copy(id = "newer", discoveryDate = 2_000L)

    val results = visibleGuides(listOf(older, undated, newer), false, "Eiffel")
    assertEquals(listOf("newer", "older", "undated"), results.map { it.id })
  }
}
