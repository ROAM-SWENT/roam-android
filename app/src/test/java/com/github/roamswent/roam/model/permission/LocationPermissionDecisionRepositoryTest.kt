/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.permission

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationPermissionDecisionRepositoryTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val preferences =
      context.getSharedPreferences("location_permission", Context.MODE_PRIVATE)

  @Before
  fun clearPreferences() {
    preferences.edit().clear().commit()
  }

  @After
  fun clearPreferencesAfterTest() {
    preferences.edit().clear().commit()
  }

  @Test
  fun getDecision_returnsNotAskedWhenNoDecisionWasStored() {
    val repository = SharedPreferencesLocationPermissionDecisionRepository(context)

    assertEquals(LocationPermissionDecision.NOT_ASKED, repository.getDecision())
  }

  @Test
  fun saveDecision_persistsGrantForANewRepositoryInstance() {
    SharedPreferencesLocationPermissionDecisionRepository(context)
        .saveDecision(LocationPermissionDecision.GRANTED)

    assertEquals(
        LocationPermissionDecision.GRANTED,
        SharedPreferencesLocationPermissionDecisionRepository(context).getDecision(),
    )
  }

  @Test
  fun saveDecision_persistsDenialForANewRepositoryInstance() {
    SharedPreferencesLocationPermissionDecisionRepository(context)
        .saveDecision(LocationPermissionDecision.DENIED)

    assertEquals(
        LocationPermissionDecision.DENIED,
        SharedPreferencesLocationPermissionDecisionRepository(context).getDecision(),
    )
  }

  @Test
  fun getDecision_returnsNotAskedForAnUnknownStoredValue() {
    preferences.edit().putString("decision", "UNKNOWN").commit()
    val repository = SharedPreferencesLocationPermissionDecisionRepository(context)

    assertEquals(LocationPermissionDecision.NOT_ASKED, repository.getDecision())
  }
}
