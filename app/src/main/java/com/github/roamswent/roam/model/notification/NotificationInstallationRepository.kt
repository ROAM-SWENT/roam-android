/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.notification

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Provides FCM installation-ID synchronization for the signed-in user's devices. */
interface NotificationInstallationRepository {
  /** Registers with FCM; the messaging service persists its FID from the registration callback. */
  suspend fun synchronizeCurrentInstallation()

  /** Stores [installationId] for the signed-in user after an FCM registration callback. */
  suspend fun saveInstallationId(installationId: String)

  /**
   * Removes the server-side association for the signed-in user without unregistering this app
   * installation from FCM.
   */
  suspend fun removeCurrentInstallation()
}

/**
 * Stores FCM installation IDs in the private Firestore path consumed by Cloud Functions.
 *
 * Registration is invoked after notification permission is granted, and the messaging service
 * persists every FID reported by FCM.
 */
class FirestoreNotificationInstallationRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val registerWithFcm: suspend () -> Unit = {
      FirebaseMessaging.getInstance().register().await()
    },
    private val getInstallationId: suspend () -> String = {
      FirebaseInstallations.getInstance().id.await()
    },
) : NotificationInstallationRepository {

  override suspend fun synchronizeCurrentInstallation() {
    registerWithFcm()
  }

  override suspend fun saveInstallationId(installationId: String) {
    require(installationId.isNotBlank()) { "A Firebase Installation ID is required." }
    val uid = auth.currentUser?.uid ?: return
    installationDocument(uid, installationId)
        .set(
            mapOf(
                INSTALLATION_ID_FIELD to installationId,
                UPDATED_AT_FIELD to FieldValue.serverTimestamp(),
            )
        )
        .await()
  }

  override suspend fun removeCurrentInstallation() {
    val uid = auth.currentUser?.uid ?: return
    installationDocument(uid, getInstallationId()).delete().await()
  }

  private fun installationDocument(uid: String, installationId: String) =
      firestore
          .collection(INSTALLATIONS_COLLECTION)
          .document(uid)
          .collection(USER_INSTALLATIONS_COLLECTION)
          .document(installationId)

  private companion object {
    const val INSTALLATIONS_COLLECTION = "notificationInstallations"
    const val USER_INSTALLATIONS_COLLECTION = "installations"
    const val INSTALLATION_ID_FIELD = "fid"
    const val UPDATED_AT_FIELD = "updatedAt"
  }
}
