// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.profile

/**
 * Public profile information used to identify a user and expose their account privacy preferences.
 *
 * @property uid The Firebase Authentication UID of the user.
 * @property username The user's unique display username.
 * @property isPublic Whether the account is public and can be followed unidirectionally.
 * @property allowFriendRequests Whether other users are permitted to send friend requests.
 */
data class PublicProfile(
    val uid: String,
    val username: String,
    val isPublic: Boolean = false,
    val allowFriendRequests: Boolean = true,
)
