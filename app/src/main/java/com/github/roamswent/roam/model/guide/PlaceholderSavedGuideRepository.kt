// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory saved guides used to render the collection before it is wired to Firestore. Guides have
 * no photo, so the UI shows its placeholder image.
 */
class PlaceholderSavedGuideRepository(initialGuides: List<Guide> = placeholderGuides) :
    SavedGuideRepository {
  private val guides = MutableStateFlow(initialGuides)

  override suspend fun addGuide(guide: Guide): Guide {
    val savedGuide = guide.copy(id = guide.id.ifBlank { UUID.randomUUID().toString() })
    guides.update { current -> current.filterNot { it.id == savedGuide.id } + savedGuide }
    return savedGuide
  }

  override suspend fun removeGuide(guideId: String) {
    guides.update { current -> current.filterNot { it.id == guideId } }
  }

  override suspend fun getSavedGuides(): List<Guide> = guides.value

  override fun observeSavedGuides(): Flow<List<Guide>> = guides.asStateFlow()

  override suspend fun getGuide(guideId: String): Guide? =
      guides.value.firstOrNull { it.id == guideId }
}

private fun placeholderGuide(id: String, name: String, discoveryDate: Long) =
    Guide(
        id = id,
        photoBytes = ByteArray(0),
        monumentName = name,
        confidence = 0.9,
        history = "Placeholder history for $name.",
        facts = listOf("Placeholder fact about $name."),
        culture = "Placeholder cultural context for $name.",
        discoveryDate = discoveryDate,
    )

/** Six sample guides, discovered one day apart from 12 Oct 2026 backwards. */
internal val placeholderGuides: List<Guide> =
    listOf(
            "Eiffel Tower",
            "Colosseum",
            "Sagrada Família",
            "Big Ben",
            "Acropolis of Athens",
            "Taj Mahal",
        )
        .mapIndexed { index, name ->
          placeholderGuide(
              id = "placeholder-${index + 1}",
              name = name,
              discoveryDate = PLACEHOLDER_LATEST_DISCOVERY - index * DAY_MILLIS,
          )
        }

/** 12 Oct 2026, 12:00 UTC. */
private const val PLACEHOLDER_LATEST_DISCOVERY = 1_791_806_400_000L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
