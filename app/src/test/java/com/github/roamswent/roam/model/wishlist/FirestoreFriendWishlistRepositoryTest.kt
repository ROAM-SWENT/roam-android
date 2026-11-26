// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.wishlist

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FirestoreFriendWishlistRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val registration = mockk<ListenerRegistration>(relaxed = true)
  private val listener = slot<EventListener<QuerySnapshot>>()
  private val repository = FirestoreFriendWishlistRepository(firestore, auth)

  private fun signedIn(uid: String? = "me") {
    val user = uid?.let { mockk<FirebaseUser> { every { this@mockk.uid } returns it } }
    every { auth.currentUser } returns user
  }

  private fun wireCollection(friendUid: String) {
    val collection = mockk<CollectionReference>()
    val doc = mockk<DocumentReference>()
    val users = mockk<CollectionReference>()
    every { firestore.collection("users") } returns users
    every { users.document(friendUid) } returns doc
    every { doc.collection("wishlist") } returns collection
    every { collection.addSnapshotListener(MetadataChanges.INCLUDE, capture(listener)) } returns
        registration
  }

  private fun doc(name: Any?) =
      mockk<DocumentSnapshot> { every { this@mockk.get("monumentName") } returns name }

  private fun snapshot(vararg docs: DocumentSnapshot) =
      mockk<QuerySnapshot> { every { documents } returns docs.toList() }

  @Test
  fun signedOutUserFails() {
    signedIn(null)
    try {
      repository.observeWishlistedMonumentNames("friend")
      fail("expected exception")
    } catch (_: WishlistRepositoryException.UnauthenticatedUser) {}
  }

  @Test
  fun invalidFriendUidFails() {
    signedIn()
    try {
      repository.observeWishlistedMonumentNames("bad/uid")
      fail("expected exception")
    } catch (_: WishlistRepositoryException.InvalidUid) {}
  }

  @Test
  fun emitsValidNamesAndRemovesListenerOnCancel() =
      runTest(UnconfinedTestDispatcher()) {
        signedIn()
        wireCollection("friend")
        val emissions = mutableListOf<List<String>>()
        val job = launch {
          repository.observeWishlistedMonumentNames("friend").collect { emissions += it }
        }

        listener.captured.onEvent(snapshot(doc("Louvre"), doc(null), doc("  "), doc(123)), null)
        listener.captured.onEvent(snapshot(), null)
        job.cancel()

        assertEquals(listOf(listOf("Louvre"), emptyList()), emissions)
        verify { registration.remove() }
      }

  @Test
  fun listenerErrorIsWrapped() =
      runTest(UnconfinedTestDispatcher()) {
        signedIn()
        wireCollection("friend")
        val failure = mockk<FirebaseFirestoreException>()
        val job = launch {
          try {
            repository.observeWishlistedMonumentNames("friend").toList()
            fail("expected exception")
          } catch (_: WishlistRepositoryException.FirestoreFailure) {}
        }
        listener.captured.onEvent(null, failure)
        job.join()
      }
}
