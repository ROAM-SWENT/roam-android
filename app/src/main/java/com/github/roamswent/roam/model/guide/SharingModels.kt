// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

enum class ShareAudience {
  NONE,
  SELECTED_FRIENDS,
  ALL_FRIENDS,
  ALL_FOLLOWERS,
}

enum class RelationshipKind {
  BIDIRECTIONAL_FRIEND,
  UNIDIRECTIONAL_FOLLOWER,
}

val DEFAULT_SHARE_AUDIENCE = ShareAudience.ALL_FRIENDS

data class MonumentShareSettings(
    val audience: ShareAudience = DEFAULT_SHARE_AUDIENCE,
    val sharedWithUids: Set<String> = emptySet(),
    val dismissedByUids: Set<String> = emptySet(),
) {
  fun effectiveRecipientUids(
      friendUids: Set<String>,
      followerUids: Set<String> = emptySet(),
  ): Set<String> =
      when (audience) {
        ShareAudience.NONE -> emptySet()
        ShareAudience.SELECTED_FRIENDS -> sharedWithUids - dismissedByUids
        ShareAudience.ALL_FRIENDS -> friendUids - dismissedByUids
        ShareAudience.ALL_FOLLOWERS -> (friendUids + followerUids) - dismissedByUids
      }

  companion object {
    val DEFAULT_SHARE_AUDIENCE = com.github.roamswent.roam.model.guide.DEFAULT_SHARE_AUDIENCE
  }
}

data class AccountPrivacyState(
    val isPublic: Boolean = false,
    val followedUids: Set<String> = emptySet(),
    val followerUids: Set<String> = emptySet(),
) {
  fun availableAudiences(): List<ShareAudience> =
      if (isPublic) {
        listOf(
            ShareAudience.NONE,
            ShareAudience.ALL_FOLLOWERS,
            ShareAudience.ALL_FRIENDS,
            ShareAudience.SELECTED_FRIENDS,
        )
      } else {
        listOf(
            ShareAudience.NONE,
            ShareAudience.ALL_FRIENDS,
            ShareAudience.SELECTED_FRIENDS,
        )
      }
}

sealed class MonumentSharingException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  class UnauthenticatedUser(
      message: String = "A signed-in user is required for this operation.",
      cause: Throwable? = null,
  ) : MonumentSharingException(message, cause)

  class InvalidUid(
      message: String = "The user ID is not a valid UID.",
      cause: Throwable? = null,
  ) : MonumentSharingException(message, cause)

  class GuideNotFound(
      message: String = "The guide was not found.",
      cause: Throwable? = null,
  ) : MonumentSharingException(message, cause)

  class NotGuideOwner(
      message: String = "Only the guide owner can perform this operation.",
      cause: Throwable? = null,
  ) : MonumentSharingException(message, cause)

  class UnauthorizedRecipient(
      message: String = "The recipient is not authorized to access this guide.",
      cause: Throwable? = null,
  ) : MonumentSharingException(message, cause)

  class FirestoreFailure(cause: Throwable) :
      MonumentSharingException(cause.message ?: "The sharing operation failed in Firestore.", cause)
}
