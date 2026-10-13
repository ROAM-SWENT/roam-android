/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.permission

import com.github.roamswent.roam.model.permission.LocationPermissionDecision

/** Current Android access and the separately persisted result of the app's last request. */
data class LocationPermissionState(
    val isGranted: Boolean,
    val decision: LocationPermissionDecision,
) {
  val hasBeenAsked: Boolean
    get() = decision != LocationPermissionDecision.NOT_ASKED
}
