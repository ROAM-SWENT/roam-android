// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.profile

/** Stores public profiles and searches registered usernames. */
interface PublicProfileRepository {
  /** Loads the signed-in user's public profile, or `null` if they have not registered. */
  suspend fun getCurrentProfile(): PublicProfile?

  /** Loads a public profile by UID, or `null` if the user has not registered. */
  suspend fun getProfile(uid: String): PublicProfile?

  /** Registers a username for the signed-in user. */
  suspend fun registerUsername(username: String): PublicProfile

  /** Changes the signed-in user's username and releases the previous username. */
  suspend fun renameUsername(username: String): PublicProfile

  /** Searches usernames by case-insensitive prefix, returning at most 20 profiles. */
  suspend fun searchProfiles(prefix: String): List<PublicProfile>
}

/** A distinguishable failure from a public-profile operation. */
sealed class PublicProfileException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  class InvalidUsername :
      PublicProfileException("Username must contain 3 to 20 ASCII letters, digits, or underscores.")

  class UsernameTaken : PublicProfileException("That username is already taken.")

  class UnauthenticatedUser :
      PublicProfileException("A signed-in user is required for this operation.")

  class UsernameAlreadyRegistered :
      PublicProfileException("The signed-in user already has a registered username.")

  class ProfileNotRegistered :
      PublicProfileException("The signed-in user does not have a registered username.")

  class FirestoreFailure(cause: Throwable) :
      PublicProfileException("The public profile operation failed in Firestore.", cause)
}
