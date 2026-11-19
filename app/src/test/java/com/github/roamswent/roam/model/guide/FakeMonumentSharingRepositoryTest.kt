// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMonumentSharingRepositoryTest {
  private val guide =
      Guide(
          id = "guide",
          photoBytes = byteArrayOf(),
          monumentName = "Monument",
          confidence = 1.0,
          history = "",
          facts = emptyList(),
          culture = "",
      )

  @Test
  fun getSharedGuideFromFindsOwnerAndIdAndReflectsChanges() = runTest {
    val repository = FakeMonumentSharingRepository(mapOf("owner" to listOf(guide)))
    assertEquals(guide, repository.getSharedGuideFrom("owner", "guide"))
    assertNull(repository.getSharedGuideFrom("other", "guide"))
    assertNull(repository.getSharedGuideFrom("owner", "missing"))
    repository.dismissSharedGuide("owner", "guide")
    assertNull(repository.getSharedGuideFrom("owner", "guide"))
  }

  @Test
  fun getSharedGuideFromAppliesAudienceAndRecipientFiltering() = runTest {
    val repository = FakeMonumentSharingRepository(currentUserId = "viewer")
    fun setGuide(settings: MonumentShareSettings) {
      repository.guidesByOwner.value =
          mapOf("owner" to listOf(guide.copy(shareSettings = settings)))
    }

    setGuide(MonumentShareSettings(audience = ShareAudience.NONE))
    assertNull(repository.getSharedGuideFrom("owner", "guide"))
    setGuide(MonumentShareSettings(audience = ShareAudience.SELECTED_FRIENDS))
    assertNull(repository.getSharedGuideFrom("owner", "guide"))
    setGuide(
        MonumentShareSettings(
            audience = ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf("viewer"),
        )
    )
    assertEquals(
        guide.copy(
            shareSettings = repository.guidesByOwner.value.getValue("owner").single().shareSettings
        ),
        repository.getSharedGuideFrom("owner", "guide"),
    )
    repository.currentUserId = "owner"
    setGuide(MonumentShareSettings(audience = ShareAudience.SELECTED_FRIENDS))
    assertEquals(
        repository.guidesByOwner.value.getValue("owner").single(),
        repository.getSharedGuideFrom("owner", "guide"),
    )
  }

  @Test
  fun getSharedGuideFromValidatesInputAndPropagatesLoadError() = runTest {
    val repository = FakeMonumentSharingRepository()
    assertTrue(
        runCatching { repository.getSharedGuideFrom("invalid uid", "guide") }.exceptionOrNull()
            is MonumentSharingException.InvalidUid
    )
    assertTrue(
        runCatching { repository.getSharedGuideFrom("owner", " ") }.exceptionOrNull()
            is IllegalArgumentException
    )
    repository.currentUserId = "invalid uid"
    assertTrue(
        runCatching { repository.getSharedGuideFrom("owner", "guide") }.exceptionOrNull()
            is MonumentSharingException.InvalidUid
    )
    repository.currentUserId = "viewer"
    val error = IllegalStateException("load")
    repository.loadError = error
    assertSame(
        error,
        runCatching { repository.getSharedGuideFrom("owner", "guide") }.exceptionOrNull(),
    )
  }
}
