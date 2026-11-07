/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.permission

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.permission.NotificationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesNotificationPermissionDecisionRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
  fun alreadyGrantedPermissionPersistsGrantAndReportsResultWithoutLaunchingRequest() {
    var permissionResult: Boolean? = null
    setUpController { permissionResult = it }
    shadowOf(activity).grantPermissions(permission)

    controller.requestPermission()

    assertTrue(controller.state.value.isGranted)
    assertEquals(NotificationPermissionDecision.GRANTED, controller.state.value.decision)
    assertEquals(NotificationPermissionDecision.GRANTED, repository.getDecision())
    assertEquals(true, permissionResult)
    assertEquals(null, shadowOf(activity).lastRequestedPermission)
  }

  @Test
  fun alreadyGrantedPermissionWithSavedDecisionReportsResultWithoutRequestingAgain() {
    repository.saveDecision(NotificationPermissionDecision.DENIED)
    var permissionResult: Boolean? = null
    setUpController { permissionResult = it }
    shadowOf(activity).grantPermissions(permission)

    controller.requestPermission()

    assertTrue(controller.state.value.isGranted)
    assertEquals(NotificationPermissionDecision.DENIED, controller.state.value.decision)
    assertEquals(true, permissionResult)
    assertEquals(null, shadowOf(activity).lastRequestedPermission)
  }

  @Test
  @Config(sdk = [32])
  fun requestPermissionOnOlderAndroidRecordsDeniedWhenNotificationsAreDisabled() {
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    shadowOf(notificationManager).setNotificationsEnabled(false)
    var permissionResult: Boolean? = null
    setUpController { permissionResult = it }

    controller.requestPermission()

    assertFalse(controller.hasPermission())
    assertEquals(NotificationPermissionDecision.DENIED, repository.getDecision())
    assertEquals(false, permissionResult)
    assertEquals(null, shadowOf(activity).lastRequestedPermission)
  }

  @Test
  fun requestPermissionFailsIfGrantedPermissionHasNoResultCallback() {
    repository.saveDecision(NotificationPermissionDecision.DENIED)
    shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(permission)
    val unboundController = AndroidNotificationPermissionController(context, repository)

    val error =
        assertThrows(IllegalStateException::class.java) { unboundController.requestPermission() }

    assertEquals("Notification permission result callback is not attached", error.message)
  }

  @Test
  fun requestPermissionFailsIfLauncherIsNotAttached() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(permission)
    val unboundController = AndroidNotificationPermissionController(context, repository)

    val error =
        assertThrows(IllegalStateException::class.java) { unboundController.requestPermission() }

    assertEquals("Notification permission launcher is not attached", error.message)
  }

  @Test
  fun recordRequestResultFailsIfResultCallbackIsNotAttached() {
    val unboundController = AndroidNotificationPermissionController(context, repository)

    val error =
        assertThrows(IllegalStateException::class.java) {
          unboundController.recordRequestResult(granted = true)
        }

    assertEquals("Notification permission result callback is not attached", error.message)
    assertEquals(NotificationPermissionDecision.GRANTED, repository.getDecision())
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

  @Test
  fun changingDecisionRepositoryRecreatesControllerWithUpdatedDecision() {
    val secondRepository =
        SharedPreferencesNotificationPermissionDecisionRepository(context).also {
          it.saveDecision(NotificationPermissionDecision.GRANTED)
        }
    var activeRepository by mutableStateOf(repository)
    var initialController: NotificationPermissionController? = null
    lateinit var currentController: NotificationPermissionController

    composeTestRule.setContent {
      activity = LocalActivity.current!!
      currentController = rememberNotificationPermissionController(activeRepository)
      if (initialController == null) initialController = currentController
    }

    composeTestRule.runOnIdle { activeRepository = secondRepository }
    composeTestRule.waitForIdle()

    assertNotSame(initialController, currentController)
    assertEquals(NotificationPermissionDecision.GRANTED, currentController.state.value.decision)
  }

  private fun setUpController(onPermissionResult: (Boolean) -> Unit = {}) {
    composeTestRule.setContent {
      activity = LocalActivity.current!!
      controller = rememberNotificationPermissionController(repository, onPermissionResult)
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
