/*
 * Portions of this code were generated with the help of GitHub Copilot.
 */

package com.github.roamswent.roam.model.profile

/** Loads the currently authenticated user's profile information. */
interface ProfileRepository {

  /**
   * Loads the profile of the currently authenticated user.
   *
   * @return A [Result] containing the user's profile on success, or an exception on failure.
   */
  suspend fun loadProfile(): Result<UserProfile>
}
