// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.model.profile

/**
 * Public profile information used to identify a user in username search, on the leaderboard, and
 * for account privacy preferences.
 *
 * @property uid The Firebase Authentication UID of the user.
 * @property username The user's unique display username.
 * @property verifiedMonumentCount Number of distinct monuments the user has verified; 0 when the
 *   profile has none stored yet.
 * @property avatarBytes Optional encoded profile picture bytes.
 * @property isPublic Whether the account is public and can be followed unidirectionally.
 * @property allowFriendRequests Whether other users are permitted to send friend requests.
 */
data class PublicProfile(
    val uid: String,
    val username: String,
    val verifiedMonumentCount: Int = 0,
    val avatarBytes: ByteArray? = null,
    val isPublic: Boolean = false,
    val allowFriendRequests: Boolean = true,
) {
  override fun equals(other: Any?): Boolean =
      other is PublicProfile &&
          uid == other.uid &&
          username == other.username &&
          verifiedMonumentCount == other.verifiedMonumentCount &&
          ((avatarBytes == null && other.avatarBytes == null) ||
              (avatarBytes != null &&
                  other.avatarBytes != null &&
                  avatarBytes.contentEquals(other.avatarBytes))) &&
          isPublic == other.isPublic &&
          allowFriendRequests == other.allowFriendRequests

  override fun hashCode(): Int {
    var result = uid.hashCode()
    result = 31 * result + username.hashCode()
    result = 31 * result + verifiedMonumentCount
    result = 31 * result + (avatarBytes?.contentHashCode() ?: 0)
    result = 31 * result + isPublic.hashCode()
    result = 31 * result + allowFriendRequests.hashCode()
    return result
  }
}
