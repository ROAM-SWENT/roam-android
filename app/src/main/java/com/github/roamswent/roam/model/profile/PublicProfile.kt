// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.github.roamswent.roam.model.profile

/**
 * Public profile information used to identify a user in username search and on the leaderboard.
 *
 * @property verifiedMonumentCount Number of distinct monuments the user has verified; 0 when the
 *   profile has none stored yet.
 */
data class PublicProfile(
    val uid: String,
    val username: String,
    val verifiedMonumentCount: Int = 0,
    val avatarBytes: ByteArray? = null,
) {
  override fun equals(other: Any?): Boolean =
      other is PublicProfile &&
          uid == other.uid &&
          username == other.username &&
          verifiedMonumentCount == other.verifiedMonumentCount &&
          ((avatarBytes == null && other.avatarBytes == null) ||
              (avatarBytes != null &&
                  other.avatarBytes != null &&
                  avatarBytes.contentEquals(other.avatarBytes)))

  override fun hashCode(): Int {
    var result = uid.hashCode()
    result = 31 * result + username.hashCode()
    result = 31 * result + verifiedMonumentCount
    result = 31 * result + (avatarBytes?.contentHashCode() ?: 0)
    return result
  }
}
