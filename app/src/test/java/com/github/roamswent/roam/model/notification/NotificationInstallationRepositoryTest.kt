/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.notification

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationInstallationRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val installations = mockk<CollectionReference>()
  private val userDocument = mockk<DocumentReference>()
  private val userInstallations = mockk<CollectionReference>()
  private val installationDocument = mockk<DocumentReference>()

  @Before
  fun setUp() {
    every { firestore.collection("notificationInstallations") } returns installations
    every { installations.document("user-a") } returns userDocument
    every { userDocument.collection("installations") } returns userInstallations
    every { userInstallations.document("fid-a") } returns installationDocument
    every { user.uid } returns "user-a"
  }

  @Test
  fun synchronizeCurrentInstallation_registersWithFcm() = runTest {
    var registered = false
    val repository =
        FirestoreNotificationInstallationRepository(
            firestore,
            auth,
            registerWithFcm = { registered = true },
            getInstallationId = { "fid-a" },
        )

    repository.synchronizeCurrentInstallation()

    assertTrue(registered)
  }

  @Test
  fun saveInstallationId_requiresNonBlankId() = runTest {
    val repository =
        FirestoreNotificationInstallationRepository(
            firestore,
            auth,
            registerWithFcm = {},
            getInstallationId = { "fid-a" },
        )

    try {
      repository.saveInstallationId(" ")
      throw AssertionError("Expected an IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
      // Expected.
    }
  }

  @Test
  fun saveInstallationId_withoutSignedInUser_doesNotWrite() = runTest {
    every { auth.currentUser } returns null
    val repository =
        FirestoreNotificationInstallationRepository(
            firestore,
            auth,
            registerWithFcm = {},
            getInstallationId = { "fid-a" },
        )

    repository.saveInstallationId("fid-a")

    verify(exactly = 0) { firestore.collection(any()) }
  }

  @Test
  fun saveInstallationId_writesInstallationDocument() = runTest {
    every { auth.currentUser } returns user
    val values = slot<Map<String, Any>>()
    every { installationDocument.set(capture(values)) } returns Tasks.forResult(null)
    val repository =
        FirestoreNotificationInstallationRepository(
            firestore,
            auth,
            registerWithFcm = {},
            getInstallationId = { "fid-a" },
        )

    repository.saveInstallationId("fid-a")

    assertEquals("fid-a", values.captured["fid"])
    assertTrue(values.captured.containsKey("updatedAt"))
    verify { installationDocument.set(any<Map<String, Any>>()) }
  }

  @Test
  fun removeCurrentInstallation_deletesCurrentInstallation() = runTest {
    every { auth.currentUser } returns user
    every { installationDocument.delete() } returns Tasks.forResult(null)
    val repository =
        FirestoreNotificationInstallationRepository(
            firestore,
            auth,
            registerWithFcm = {},
            getInstallationId = { "fid-a" },
        )

    repository.removeCurrentInstallation()

    verify { installationDocument.delete() }
  }

  @Test
  fun removeCurrentInstallation_withoutSignedInUser_doesNotReadInstallationId() = runTest {
    every { auth.currentUser } returns null
    var read = false
    val repository =
        FirestoreNotificationInstallationRepository(
            firestore,
            auth,
            registerWithFcm = {},
            getInstallationId = {
              read = true
              "fid-a"
            },
        )

    repository.removeCurrentInstallation()

    assertEquals(false, read)
    verify(exactly = 0) { firestore.collection(any()) }
  }
}
