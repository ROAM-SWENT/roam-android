/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.interests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserInterestsRepositoryTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val preferences = context.getSharedPreferences("user_interests", Context.MODE_PRIVATE)

  @Before
  fun clearPreferences() {
    preferences.edit().clear().commit()
  }

  @After
  fun clearPreferencesAfterTest() {
    preferences.edit().clear().commit()
  }

  @Test
  fun getUserInterests_returnsNotAskedWithNoInterestsByDefault() {
    val repository = SharedPreferencesUserInterestsRepository(context)

    assertEquals(
        UserInterestsState(hasBeenAsked = false, interests = emptyList()),
        repository.getUserInterests(),
    )
  }

  @Test
  fun saveInterests_recordsAskedStateAndInterests() {
    val repository = SharedPreferencesUserInterestsRepository(context)

    repository.saveInterests(listOf("art", "history"))

    assertEquals(
        UserInterestsState(hasBeenAsked = true, interests = listOf("art", "history")),
        repository.getUserInterests(),
    )
  }

  @Test
  fun skipInterests_recordsAskedStateWithoutInterests() {
    val repository = SharedPreferencesUserInterestsRepository(context)

    repository.skipInterests()

    assertEquals(
        UserInterestsState(hasBeenAsked = true, interests = emptyList()),
        repository.getUserInterests(),
    )
  }

  @Test
  fun savedInterests_persistForANewRepositoryInstance() {
    SharedPreferencesUserInterestsRepository(context).saveInterests(listOf("architecture", "food"))

    assertEquals(
        UserInterestsState(hasBeenAsked = true, interests = listOf("architecture", "food")),
        SharedPreferencesUserInterestsRepository(context).getUserInterests(),
    )
  }

  @Test
  fun saveInterests_replacesPreviouslySavedInterests() {
    val repository = SharedPreferencesUserInterestsRepository(context)
    repository.saveInterests(listOf("art", "history"))

    repository.saveInterests(listOf("nature"))

    assertEquals(
        UserInterestsState(hasBeenAsked = true, interests = listOf("nature")),
        repository.getUserInterests(),
    )
  }
}
