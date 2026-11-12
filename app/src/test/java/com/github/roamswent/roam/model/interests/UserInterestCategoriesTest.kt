package com.github.roamswent.roam.model.interests

import org.junit.Assert.assertEquals
import org.junit.Test

class UserInterestCategoriesTest {

  @Test
  fun userInterestCategories_ids_matchesFrozenIdentifiersOrderAndValues_regressionDetectsRenamingCorruptingProfilesOrAiPrompt() {
    val expectedFrozenIds =
        listOf(
            "Architecture",
            "History",
            "Art & Sculpture",
            "Local Culture",
            "Legends & Myths",
            "Fun Facts",
            "Nature",
            "Food & Drink",
        )
    assertEquals(expectedFrozenIds, UserInterestCategories.ids)
  }
}
