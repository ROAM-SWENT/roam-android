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

  suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide>

  fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>>
}

fun interface MonumentSharingProvider {
  fun repository(): MonumentSharingRepository
}
