// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.flow.Flow

interface MonumentSharingRepository {
  suspend fun setShareAudience(
      guideId: String,
      audience: ShareAudience,
      recipientUids: Set<String> = emptySet(),
  )

  suspend fun grantAccess(guideId: String, recipientUids: Set<String>)

  suspend fun revokeAccess(guideId: String, recipientUids: Set<String>)

  suspend fun dismissSharedGuide(ownerUid: String, guideId: String)

  suspend fun getShareSettings(guideId: String): MonumentShareSettings

  /**
   * Reads one shared guide, or returns null when it is missing, not shared, or dismissed. Owner
   * reads follow the same shared-only filtering as [getSharedGuidesFrom]. A permission-denied read
   * of another user's guide also returns null: Firestore does not distinguish a deleted document
   * from revoked access. Authentication, invalid input, owner permission and other storage failures
   * are reported as exceptions.
   */
  suspend fun getSharedGuideFrom(ownerUid: String, guideId: String): Guide?

  suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide>

  fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>>
}

fun interface MonumentSharingProvider {
  fun repository(): MonumentSharingRepository
}
