/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.permission.NotificationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesNotificationPermissionDecisionRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionControllerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var activity: Activity
  private lateinit var controller: NotificationPermissionController
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val repository = SharedPreferencesNotificationPermissionDecisionRepository(context)
  private val permission = Manifest.permission.POST_NOTIFICATIONS

  @After
  fun tearDown() {
    context
        .getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    if (::activity.isInitialized) activity.finish()
  }

  @Test
  fun freshStateReportsNotAskedAndNoPermission() {
    setUpController()

    assertFalse(controller.hasPermission())
    assertFalse(controller.state.value.hasBeenAsked)
    assertEquals(NotificationPermissionDecision.NOT_ASKED, controller.state.value.decision)
  }

  @Test
  fun requestPermissionRequestsAndroidNotificationPermission() {
    setUpController()

    controller.requestPermission()

    assertEquals(
        setOf(permission),
        shadowOf(activity).lastRequestedPermission?.requestedPermissions?.toSet(),
    )
  }

  @Test
  fun grantResultPersistsDecisionAndUpdatesState() {
    setUpController()
    dispatchPermissionResult(PackageManager.PERMISSION_GRANTED)

    assertTrue(controller.state.value.isGranted)
    assertTrue(controller.state.value.hasBeenAsked)
    assertEquals(NotificationPermissionDecision.GRANTED, repository.getDecision())
  }

  @Test
  fun denyResultPersistsDecisionWithoutGrantingPermission() {
    setUpController()
    dispatchPermissionResult(PackageManager.PERMISSION_DENIED)

    assertFalse(controller.state.value.isGranted)
    assertTrue(controller.state.value.hasBeenAsked)
    assertEquals(NotificationPermissionDecision.DENIED, repository.getDecision())
  }

  @Test
  fun refreshReadsCurrentAndroidPermissionWithoutChangingDecision() {
    repository.saveDecision(NotificationPermissionDecision.DENIED)
    setUpController()
    shadowOf(activity).grantPermissions(permission)

    controller.refreshPermissionState()

    assertTrue(controller.state.value.isGranted)
    assertEquals(NotificationPermissionDecision.DENIED, controller.state.value.decision)
  }

  private fun setUpController() {
    composeTestRule.setContent {
      activity = LocalActivity.current!!
      controller = rememberNotificationPermissionController(repository)
    }
  }

  private fun dispatchPermissionResult(result: Int) {
    controller.requestPermission()
    val request = shadowOf(activity).lastRequestedPermission
    assertNotNull(request)
    if (result == PackageManager.PERMISSION_GRANTED) {
      shadowOf(activity).grantPermissions(permission)
    } else {
      shadowOf(activity).denyPermissions(permission)
    }
    activity.onRequestPermissionsResult(
        request!!.requestCode,
        arrayOf(permission),
        intArrayOf(result),
    )
  }
}
