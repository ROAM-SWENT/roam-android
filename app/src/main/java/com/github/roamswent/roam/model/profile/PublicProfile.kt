// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.profile

/** Public profile information used to identify a user in username search. */
data class PublicProfile(
    val uid: String,
    val username: String,
    val avatarBytes: ByteArray? = null,
) {
  override fun equals(other: Any?): Boolean =
      other is PublicProfile &&
          uid == other.uid &&
          username == other.username &&
          ((avatarBytes == null && other.avatarBytes == null) ||
              (avatarBytes != null &&
                  other.avatarBytes != null &&
                  avatarBytes.contentEquals(other.avatarBytes)))

  override fun hashCode(): Int =
      31 * (31 * uid.hashCode() + username.hashCode()) + (avatarBytes?.contentHashCode() ?: 0)
}
