/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.profile

/** Loads the currently authenticated user's profile information. */
interface ProfileRepository {
  /** Initializes private user data for the signed-in account without overwriting existing data. */
  suspend fun initializePrivateProfile(): Result<Unit> = Result.success(Unit)

  /**
   * Loads the profile of the currently authenticated user.
   *
   * @return A [Result] containing the user's profile on success, or an exception on failure.
   */
  suspend fun loadProfile(): Result<UserProfile>
}
