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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.permission.LocationPermissionDecision
import com.github.roamswent.roam.model.permission.SharedPreferencesLocationPermissionDecisionRepository
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
class LocationPermissionControllerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var activity: Activity
  private lateinit var lifecycleOwner: LifecycleOwner
  private lateinit var controller: LocationPermissionController
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val repository = SharedPreferencesLocationPermissionDecisionRepository(context)
  private val permissionNames =
      arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

  @After
  fun tearDown() {
    context
        .getSharedPreferences("location_permission", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit()
    if (::activity.isInitialized) activity.finish()
  }

  @Test
  fun freshStateReflectsCurrentPermissionAndNoSavedDecision() {
    setUpController()

    assertFalse(controller.hasPermission())
    assertFalse(controller.state.value.isGranted)
    assertFalse(controller.state.value.hasBeenAsked)
    assertEquals(LocationPermissionDecision.NOT_ASKED, controller.state.value.decision)
  }

  @Test
  fun requestPermissionRequestsCoarseAndFineLocation() {
    setUpController()

    controller.requestPermission()

    assertEquals(
        permissionNames.toSet(),
        shadowOf(activity).lastRequestedPermission?.requestedPermissions?.toSet(),
    )
  }

  @Test
  fun grantResultUpdatesCurrentStateAndPersistsDecision() {
    setUpController()
    requestAndDispatch(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED)

    assertTrue(controller.state.value.isGranted)
    assertTrue(controller.state.value.hasBeenAsked)
    assertEquals(LocationPermissionDecision.GRANTED, controller.state.value.decision)
    assertEquals(LocationPermissionDecision.GRANTED, repository.getDecision())
  }

  @Test
  fun denyResultPersistsDecisionButDoesNotGrantAccess() {
    setUpController()
    requestAndDispatch(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_DENIED)

    assertFalse(controller.state.value.isGranted)
    assertTrue(controller.state.value.hasBeenAsked)
    assertEquals(LocationPermissionDecision.DENIED, controller.state.value.decision)
    assertEquals(LocationPermissionDecision.DENIED, repository.getDecision())
  }

  @Test
  fun alreadyGrantedPermissionPersistsGrantAndReportsPermissionResult() {
    var permissionResult: Boolean? = null
    setUpController { permissionResult = it }
    shadowOf(activity).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    controller.requestPermission()

    assertTrue(controller.state.value.isGranted)
    assertEquals(LocationPermissionDecision.GRANTED, controller.state.value.decision)
    assertEquals(LocationPermissionDecision.GRANTED, repository.getDecision())
    assertEquals(true, permissionResult)
    assertEquals(null, shadowOf(activity).lastRequestedPermission)
  }

  @Test
  fun refreshUsesCurrentAndroidPermissionInsteadOfPersistedDecision() {
    repository.saveDecision(LocationPermissionDecision.DENIED)
    setUpController()
    shadowOf(activity).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    controller.refreshPermissionState()

    assertTrue(controller.state.value.isGranted)
    assertEquals(LocationPermissionDecision.DENIED, controller.state.value.decision)
  }

  @Test
  fun persistedGrantDoesNotOverrideCurrentDeniedPermission() {
    repository.saveDecision(LocationPermissionDecision.GRANTED)
    setUpController()

    assertFalse(controller.hasPermission())
    assertFalse(controller.state.value.isGranted)
    assertEquals(LocationPermissionDecision.GRANTED, controller.state.value.decision)
  }

  @Test
  fun requestPermissionDoesNotPromptWhenAndroidAlreadyGrantsAccess() {
    repository.saveDecision(LocationPermissionDecision.DENIED)
    setUpController()
    shadowOf(activity).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    controller.requestPermission()

    assertTrue(controller.state.value.isGranted)
    assertEquals(LocationPermissionDecision.DENIED, controller.state.value.decision)
    assertEquals(null, shadowOf(activity).lastRequestedPermission)
  }

  @Test
  fun onResumeRefreshesCurrentAndroidPermission() {
    setUpController()
    val lifecycle = lifecycleOwner.lifecycle as LifecycleRegistry
    lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    shadowOf(activity).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    assertTrue(controller.state.value.isGranted)
  }

  private fun setUpController(onPermissionResult: (Boolean) -> Unit = {}) {
    composeTestRule.setContent {
      activity = LocalActivity.current!!
      lifecycleOwner = LocalLifecycleOwner.current
      controller =
          rememberLocationPermissionController(
              decisionRepository = repository,
              onPermissionResult = onPermissionResult,
          )
    }
  }

  private fun requestAndDispatch(coarseResult: Int, fineResult: Int) {
    controller.requestPermission()
    val request = shadowOf(activity).lastRequestedPermission
    assertNotNull(request)
    shadowOf(activity).denyPermissions(*permissionNames)
    val grantedPermissions = permissionNames.filterIndexed { index, _ ->
      (index == 0 && coarseResult == PackageManager.PERMISSION_GRANTED) ||
          (index == 1 && fineResult == PackageManager.PERMISSION_GRANTED)
    }
    if (grantedPermissions.isNotEmpty()) {
      shadowOf(activity).grantPermissions(*grantedPermissions.toTypedArray())
    }
    activity.onRequestPermissionsResult(
        request!!.requestCode,
        permissionNames,
        intArrayOf(coarseResult, fineResult),
    )
  }
}
