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
class NotificationPermissionDecisionRepositoryTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val preferences =
      context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)

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
    assertEquals(
        NotificationPermissionDecision.NOT_ASKED,
        SharedPreferencesNotificationPermissionDecisionRepository(context).getDecision(),
    )
  }

  @Test
  fun saveDecision_persistsDecisionForANewRepositoryInstance() {
    SharedPreferencesNotificationPermissionDecisionRepository(context)
        .saveDecision(NotificationPermissionDecision.DENIED)

    assertEquals(
        NotificationPermissionDecision.DENIED,
        SharedPreferencesNotificationPermissionDecisionRepository(context).getDecision(),
    )
  }

  @Test
  fun getDecision_returnsNotAskedForAnUnknownStoredValue() {
    preferences.edit().putString("decision", "UNKNOWN").commit()

    assertEquals(
        NotificationPermissionDecision.NOT_ASKED,
        SharedPreferencesNotificationPermissionDecisionRepository(context).getDecision(),
    )
  }
}
