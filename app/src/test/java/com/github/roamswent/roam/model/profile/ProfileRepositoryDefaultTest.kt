/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryDefaultTest {
  private class StubProfileRepository : ProfileRepository {
    override suspend fun loadProfile(): Result<UserProfile> =
        Result.success(UserProfile("Traveler", "traveler@example.com", "Account"))
  }

  @Test
  fun privateProfileInitialization_defaultsToSuccess() = runTest {
    val repository = StubProfileRepository()

    assertTrue(repository.initializePrivateProfile().isSuccess)
  }
}
