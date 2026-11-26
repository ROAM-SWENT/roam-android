// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRepositoryException
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.location.Coordinates
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileException
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreWishlistRepositoryTest {
  private val firestore = mockk<FirebaseFirestore>()
  private val auth = mockk<FirebaseAuth>()
  private val user = mockk<FirebaseUser>()
  private val users = mockk<CollectionReference>()
  private val friendRepository = mockk<FriendRepository>()
  private val publicProfileRepository = mockk<PublicProfileRepository>()
  private val userDocs = mutableMapOf<String, DocumentReference>()
  private val wishlistCollections = mutableMapOf<String, CollectionReference>()
  private val savedGuideCollections = mutableMapOf<String, CollectionReference>()

  private val repository =
      FirestoreWishlistRepository(firestore, auth, friendRepository, publicProfileRepository)

  @Before
  fun setUp() {
    every { auth.currentUser } returns user
    every { user.uid } returns CURRENT_UID
    every { firestore.collection("users") } returns users
  }

  @Test
  fun operations_rejectSignedOutAndInvalidCurrentUid() = runTest {
    val item = sampleItem()
    val operations: List<Pair<String, suspend () -> Any?>> =
        listOf(
            "addToWishlist" to { repository.addToWishlist(item) },
            "removeFromWishlist" to { repository.removeFromWishlist(VALID_KEY) },
            "isWishlisted" to { repository.isWishlisted(VALID_KEY) },
            "getWishlist" to { repository.getWishlist() },
            "observeWishlist" to { repository.observeWishlist() },
            "getResolvedWishlist" to { repository.getResolvedWishlist() },
            "getFriendsWhoWishlisted" to { repository.getFriendsWhoWishlisted(VALID_KEY) },
            "getUsersWhoWishlisted" to
                {
                  repository.getUsersWhoWishlisted(VALID_KEY, setOf("other-user"))
                },
        )

    every { auth.currentUser } returns null
    for ((name, op) in operations) {
      assertTrue(
          "$name should fail when signed out",
          captureFailure(op) is WishlistRepositoryException.UnauthenticatedUser,
      )
    }

    every { auth.currentUser } returns user
    every { user.uid } returns "bad/uid"
    for ((name, op) in operations) {
      assertTrue(
          "$name should fail for invalid UID",
          captureFailure(op) is WishlistRepositoryException.UnauthenticatedUser,
      )
    }
  }

  @Test
  fun validationGuards_rejectMalformedArgumentsBeforeTouchingFirestore() = runTest {
    val invalidCalls: List<Pair<Class<out Exception>, suspend () -> Any?>> =
        listOf(
            WishlistRepositoryException.InvalidMonumentKey::class.java to
                {
                  repository.addToWishlist(sampleItem(monumentKey = "bad.key"))
                },
            WishlistRepositoryException.InvalidUid::class.java to
                {
                  repository.addToWishlist(sampleItem(sourceOwnerUid = "bad/uid"))
                },
            WishlistRepositoryException.InvalidGuideId::class.java to
                {
                  repository.addToWishlist(sampleItem(sourceGuideId = "   "))
                },
            WishlistRepositoryException.InvalidGuideId::class.java to
                {
                  repository.addToWishlist(sampleItem(sourceGuideId = "g".repeat(129)))
                },
            WishlistRepositoryException.InvalidMonumentName::class.java to
                {
                  repository.addToWishlist(sampleItem(monumentName = "   "))
                },
            WishlistRepositoryException.InvalidMonumentName::class.java to
                {
                  repository.addToWishlist(sampleItem(monumentName = "M".repeat(201)))
                },
            IllegalArgumentException::class.java to
                {
                  repository.addToWishlist(sampleItem(addedAt = -1L))
                },
            WishlistRepositoryException.InvalidMonumentKey::class.java to
                {
                  repository.removeFromWishlist("bad key")
                },
            WishlistRepositoryException.InvalidMonumentKey::class.java to
                {
                  repository.isWishlisted("")
                },
            WishlistRepositoryException.InvalidMonumentKey::class.java to
                {
                  repository.getFriendsWhoWishlisted("bad/key")
                },
            WishlistRepositoryException.InvalidMonumentKey::class.java to
                {
                  repository.getUsersWhoWishlisted("bad.key", setOf("friend-1"))
                },
            WishlistRepositoryException.InvalidUid::class.java to
                {
                  repository.getUsersWhoWishlisted(VALID_KEY, setOf("bad/uid"))
                },
        )
    for ((expected, call) in invalidCalls) {
      assertTrue(expected.isInstance(captureFailure(call)))
    }
    verify(exactly = 0) { firestore.collection("users") }
  }

  @Test
  fun addToWishlist_writesExactFieldsWithAndWithoutLocationAndWrapsErrors() = runTest {
    val noLocItem = sampleItem(monumentKey = "ref_owner-1~g1", location = null)
    val noLocRef = stubWishlistDoc(CURRENT_UID, noLocItem.monumentKey)
    val noLocPayload = slot<Map<String, Any>>()
    every { noLocRef.set(capture(noLocPayload)) } returns Tasks.forResult(null)

    assertEquals(noLocItem, repository.addToWishlist(noLocItem))
    assertEquals(
        mapOf(
            "monumentKey" to noLocItem.monumentKey,
            "sourceOwnerUid" to noLocItem.sourceOwnerUid,
            "sourceGuideId" to noLocItem.sourceGuideId,
            "monumentName" to noLocItem.monumentName,
            "addedAt" to noLocItem.addedAt,
        ),
        noLocPayload.captured,
    )
    assertFalse("photo" in noLocPayload.captured)
    assertFalse("location" in noLocPayload.captured)

    val locItem = sampleItem(monumentKey = VALID_KEY, location = Coordinates(48.8584, 2.2945))
    val locRef = stubWishlistDoc(CURRENT_UID, locItem.monumentKey)
    val locPayload = slot<Map<String, Any>>()
    every { locRef.set(capture(locPayload)) } returns Tasks.forResult(null)

    assertEquals(locItem, repository.addToWishlist(locItem))
    assertEquals(
        mapOf("latitude" to 48.8584, "longitude" to 2.2945),
        locPayload.captured["location"],
    )
    assertEquals(
        setOf(
            "monumentKey",
            "sourceOwnerUid",
            "sourceGuideId",
            "monumentName",
            "addedAt",
            "location",
        ),
        locPayload.captured.keys,
    )

    val failure = IllegalStateException("Write failed")
    every { locRef.set(any<Map<String, Any>>()) } returns Tasks.forException(failure)
    val error = captureFailure { repository.addToWishlist(locItem) }
    assertTrue(error is WishlistRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    every { locRef.set(any<Map<String, Any>>()) } returns Tasks.forCanceled()
    assertTrue(captureFailure { repository.addToWishlist(locItem) } is CancellationException)
  }

  @Test
  fun removeFromWishlistAndIsWishlisted_delegateToDocumentAndWrapErrors() = runTest {
    val docRef = stubWishlistDoc(CURRENT_UID, VALID_KEY)
    every { docRef.delete() } returns Tasks.forResult(null)
    repository.removeFromWishlist(VALID_KEY)
    verify(exactly = 1) { docRef.delete() }

    val failure = IllegalStateException("Delete failed")
    every { docRef.delete() } returns Tasks.forException(failure)
    val deleteError = captureFailure { repository.removeFromWishlist(VALID_KEY) }
    assertTrue(deleteError is WishlistRepositoryException.FirestoreFailure)
    assertTrue(deleteError.cause === failure)

    every { docRef.get() } returns Tasks.forResult(existenceSnapshot(true))
    assertTrue(repository.isWishlisted(VALID_KEY))

    every { docRef.get() } returns Tasks.forResult(existenceSnapshot(false))
    assertFalse(repository.isWishlisted(VALID_KEY))

    every { docRef.get() } returns Tasks.forException(failure)
    assertTrue(
        captureFailure { repository.isWishlisted(VALID_KEY) }
            is WishlistRepositoryException.FirestoreFailure
    )
  }

  @Test
  fun getWishlist_returnsEmptySortsValidItemsSkipsMalformedDocsAndWrapsErrors() = runTest {
    val collection = stubWishlistCollection(CURRENT_UID)
    every { collection.get() } returns Tasks.forResult(querySnapshot(emptyList()))
    assertEquals(emptyList<WishlistItem>(), repository.getWishlist())

    val newest =
        sampleItem(monumentKey = "key-z", addedAt = 300L, location = Coordinates(10.0, 20.0))
    val tieA = sampleItem(monumentKey = "key-a", addedAt = 200L, location = null)
    val tieB = sampleItem(monumentKey = "key-b", addedAt = 200L, location = null)
    val fallbackKeyDoc =
        wishlistDocSnapshot(
            id = "fallback-id",
            data =
                mapOf(
                    "sourceOwnerUid" to "owner-1",
                    "sourceGuideId" to "guide-1",
                    "monumentName" to "Colosseum",
                    "addedAt" to 100L,
                ),
        )

    val malformedDocs =
        listOf(
            wishlistDocSnapshot("null-data", null),
            wishlistDocSnapshot("bad.id", validWishlistMap(monumentKey = null)),
            wishlistDocSnapshot("bad-owner", validWishlistMap(sourceOwnerUid = "bad/uid")),
            wishlistDocSnapshot("blank-gid", validWishlistMap(sourceGuideId = " ")),
            wishlistDocSnapshot("long-gid", validWishlistMap(sourceGuideId = "g".repeat(129))),
            wishlistDocSnapshot("blank-name", validWishlistMap(monumentName = " ")),
            wishlistDocSnapshot("long-name", validWishlistMap(monumentName = "M".repeat(201))),
            wishlistDocSnapshot("neg-time", validWishlistMap(addedAt = -5L)),
            wishlistDocSnapshot("missing-time", validWishlistMap(addedAt = null)),
            wishlistDocSnapshot(
                "null-loc",
                validWishlistMap(location = null, includeLocation = true),
            ),
            wishlistDocSnapshot(
                "non-map-loc",
                validWishlistMap(location = "48,2", includeLocation = true),
            ),
            wishlistDocSnapshot(
                "no-lat",
                validWishlistMap(location = mapOf("longitude" to 2.0), includeLocation = true),
            ),
            wishlistDocSnapshot(
                "no-lng",
                validWishlistMap(location = mapOf("latitude" to 48.0), includeLocation = true),
            ),
            wishlistDocSnapshot(
                "bad-lat",
                validWishlistMap(
                    location = mapOf("latitude" to 95.0, "longitude" to 2.0),
                    includeLocation = true,
                ),
            ),
            wishlistDocSnapshot(
                "bad-lng",
                validWishlistMap(
                    location = mapOf("latitude" to 48.0, "longitude" to -181.0),
                    includeLocation = true,
                ),
            ),
        )

    val allDocs =
        listOf(
            wishlistDocSnapshot(tieB.monumentKey, itemToMap(tieB)),
            wishlistDocSnapshot(newest.monumentKey, itemToMap(newest)),
            fallbackKeyDoc,
            wishlistDocSnapshot(tieA.monumentKey, itemToMap(tieA)),
        ) + malformedDocs
    every { collection.get() } returns Tasks.forResult(querySnapshot(allDocs))

    assertEquals(
        listOf(
            newest,
            tieA,
            tieB,
            WishlistItem("fallback-id", "owner-1", "guide-1", "Colosseum", 100L, null),
        ),
        repository.getWishlist(),
    )

    every { collection.get() } returns Tasks.forException(IllegalStateException("Read failed"))
    assertTrue(
        captureFailure { repository.getWishlist() } is WishlistRepositoryException.FirestoreFailure
    )
  }

  @Test
  fun observeWishlist_usesMetadataChangesIncludeEmitsUpdatesRemovesListenerAndPropagatesErrors() =
      runTest {
        val collection = stubWishlistCollection(CURRENT_UID)
        val listenerSlot = slot<EventListener<QuerySnapshot>>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        every {
          collection.addSnapshotListener(MetadataChanges.INCLUDE, capture(listenerSlot))
        } returns registration

        val emissions = mutableListOf<List<WishlistItem>>()
        val job = launch { repository.observeWishlist().take(2).toList(emissions) }
        advanceUntilIdle()

        val older = sampleItem(monumentKey = "key-b", addedAt = 100L)
        val newer = sampleItem(monumentKey = "key-a", addedAt = 200L)
        listenerSlot.captured.onEvent(
            querySnapshot(
                listOf(
                    wishlistDocSnapshot(older.monumentKey, itemToMap(older)),
                    wishlistDocSnapshot(newer.monumentKey, itemToMap(newer)),
                )
            ),
            null,
        )
        listenerSlot.captured.onEvent(null, null)
        advanceUntilIdle()
        job.join()

        assertEquals(listOf(listOf(newer, older), emptyList()), emissions)
        verify(exactly = 1) { collection.addSnapshotListener(MetadataChanges.INCLUDE, any()) }
        verify(exactly = 1) { registration.remove() }

        val errorListenerSlot = slot<EventListener<QuerySnapshot>>()
        every {
          collection.addSnapshotListener(MetadataChanges.INCLUDE, capture(errorListenerSlot))
        } returns registration
        var caught: Throwable? = null
        val errorJob = launch {
          try {
            repository.observeWishlist().first()
          } catch (exception: Throwable) {
            caught = exception
          }
        }
        advanceUntilIdle()
        val fsError =
            FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        errorListenerSlot.captured.onEvent(null, fsError)
        advanceUntilIdle()
        errorJob.join()

        assertTrue(caught is WishlistRepositoryException.FirestoreFailure)
        assertEquals("Denied", generateSequence(caught) { it.cause }.last().message)
      }

  @Test
  fun getResolvedWishlist_emptyShortCircuitsAndMixedBatchCoversAllOutcomes() = runTest {
    val wishlistCol = stubWishlistCollection(CURRENT_UID)
    every { wishlistCol.get() } returns Tasks.forResult(querySnapshot(emptyList()))
    assertEquals(emptyList<ResolvedWishlistItem>(), repository.getResolvedWishlist())
    verify(exactly = 0) { firestore.collectionGroup(any()) }

    val itemAvail =
        sampleItem(monumentKey = "k1", sourceOwnerUid = "o1", sourceGuideId = "g-ok", addedAt = 70L)
    val itemMissing =
        sampleItem(
            monumentKey = "k2",
            sourceOwnerUid = "o1",
            sourceGuideId = "g-miss",
            addedAt = 60L,
        )
    val itemNullData =
        sampleItem(
            monumentKey = "k3",
            sourceOwnerUid = "o1",
            sourceGuideId = "g-null",
            addedAt = 50L,
        )
    val itemRevoked =
        sampleItem(
            monumentKey = "k4",
            sourceOwnerUid = "o2",
            sourceGuideId = "g-perm",
            addedAt = 40L,
        )
    val itemNotFound =
        sampleItem(
            monumentKey = "k5",
            sourceOwnerUid = "o2",
            sourceGuideId = "g-404",
            addedAt = 30L,
        )
    val itemUnavailable =
        sampleItem(
            monumentKey = "k6",
            sourceOwnerUid = "o2",
            sourceGuideId = "g-unavail",
            addedAt = 20L,
        )
    val itemCorrupt =
        sampleItem(
            monumentKey = "k7",
            sourceOwnerUid = "o2",
            sourceGuideId = "g-corrupt",
            addedAt = 10L,
        )

    val allItems =
        listOf(
            itemAvail,
            itemMissing,
            itemNullData,
            itemRevoked,
            itemNotFound,
            itemUnavailable,
            itemCorrupt,
        )
    every { wishlistCol.get() } returns
        Tasks.forResult(
            querySnapshot(allItems.map { wishlistDocSnapshot(it.monumentKey, itemToMap(it)) })
        )

    stubSavedGuideGet(
        "o1",
        "g-ok",
        Tasks.forResult(guideDocSnapshot("g-ok", true, validGuideFields())),
    )
    stubSavedGuideGet("o1", "g-miss", Tasks.forResult(guideDocSnapshot("g-miss", false, null)))
    stubSavedGuideGet("o1", "g-null", Tasks.forResult(guideDocSnapshot("g-null", true, null)))
    stubSavedGuideGet(
        "o2",
        "g-perm",
        Tasks.forException(fsException(FirebaseFirestoreException.Code.PERMISSION_DENIED)),
    )
    stubSavedGuideGet(
        "o2",
        "g-404",
        Tasks.forException(fsException(FirebaseFirestoreException.Code.NOT_FOUND)),
    )
    stubSavedGuideGet(
        "o2",
        "g-unavail",
        Tasks.forException(fsException(FirebaseFirestoreException.Code.UNAVAILABLE)),
    )
    stubSavedGuideGet(
        "o2",
        "g-corrupt",
        Tasks.forResult(guideDocSnapshot("g-corrupt", true, mapOf("monumentName" to 12345))),
    )

    val resolved = repository.getResolvedWishlist()

    assertEquals(7, resolved.size)
    assertEquals(itemAvail, (resolved[0] as ResolvedWishlistItem.Available).item)
    assertEquals("Eiffel Tower", (resolved[0] as ResolvedWishlistItem.Available).guide.monumentName)
    assertEquals(
        listOf(
            ResolvedWishlistItem.Unavailable(itemMissing, WishlistUnavailableReason.GUIDE_DELETED),
            ResolvedWishlistItem.Unavailable(itemNullData, WishlistUnavailableReason.GUIDE_DELETED),
            ResolvedWishlistItem.Unavailable(itemRevoked, WishlistUnavailableReason.ACCESS_REVOKED),
            ResolvedWishlistItem.Unavailable(itemNotFound, WishlistUnavailableReason.GUIDE_DELETED),
            ResolvedWishlistItem.Unavailable(itemUnavailable, WishlistUnavailableReason.UNKNOWN),
            ResolvedWishlistItem.Unavailable(itemCorrupt, WishlistUnavailableReason.UNKNOWN),
        ),
        resolved.drop(1),
    )
  }

  @Test
  fun getUsersWhoWishlisted_handlesShortCircuitsMixedErrorsAndAlphabeticalTieBreak() = runTest {
    assertEquals(
        emptyList<PublicProfile>(),
        repository.getUsersWhoWishlisted(VALID_KEY, emptySet()),
    )
    assertEquals(
        emptyList<PublicProfile>(),
        repository.getUsersWhoWishlisted(VALID_KEY, setOf(CURRENT_UID)),
    )
    verify(exactly = 0) { firestore.collection("users") }

    val candidates =
        setOf(
            CURRENT_UID,
            "u-alice-2",
            "u-alice-1",
            "u-bob",
            "u-null-profile",
            "u-profile-err",
            "u-not-wishlisted",
            "u-blocked",
            "u-not-found",
            "u-transient",
        )
    for (uid in listOf("u-alice-2", "u-alice-1", "u-bob", "u-null-profile", "u-profile-err")) {
      val doc = stubWishlistDoc(uid, VALID_KEY)
      every { doc.get() } returns Tasks.forResult(existenceSnapshot(true))
    }
    val notWishlistedDoc = stubWishlistDoc("u-not-wishlisted", VALID_KEY)
    every { notWishlistedDoc.get() } returns Tasks.forResult(existenceSnapshot(false))
    val blockedDoc = stubWishlistDoc("u-blocked", VALID_KEY)
    every { blockedDoc.get() } returns
        Tasks.forException(
            FirebaseFirestoreException("Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )
    val notFoundDoc = stubWishlistDoc("u-not-found", VALID_KEY)
    every { notFoundDoc.get() } returns
        Tasks.forException(
            FirebaseFirestoreException("Missing", FirebaseFirestoreException.Code.NOT_FOUND)
        )
    val transientDoc = stubWishlistDoc("u-transient", VALID_KEY)
    every { transientDoc.get() } returns
        Tasks.forException(
            FirebaseFirestoreException("Offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        )

    val alice1 = PublicProfile("u-alice-1", "Alice")
    val alice2 = PublicProfile("u-alice-2", "alice")
    val bob = PublicProfile("u-bob", "Bob")
    coEvery { publicProfileRepository.getProfile("u-alice-1") } returns alice1
    coEvery { publicProfileRepository.getProfile("u-alice-2") } returns alice2
    coEvery { publicProfileRepository.getProfile("u-bob") } returns bob
    coEvery { publicProfileRepository.getProfile("u-null-profile") } returns null
    coEvery { publicProfileRepository.getProfile("u-profile-err") } throws
        IllegalStateException("Profile lookup failed")

    val result = repository.getUsersWhoWishlisted(VALID_KEY, candidates)

    assertEquals(listOf(alice1, alice2, bob), result)
    verify(exactly = 0) { firestore.collectionGroup(any()) }
  }

  @Test
  fun getFriendsWhoWishlisted_excludesSelfHandlesEmptyAndMapsFriendRepositoryErrors() = runTest {
    coEvery { friendRepository.getFriends() } returns emptyList()
    assertEquals(emptyList<PublicProfile>(), repository.getFriendsWhoWishlisted(VALID_KEY))

    coEvery { friendRepository.getFriends() } returns
        listOf(
            Friendship("pair-self", CURRENT_UID),
            Friendship("pair-1", "friend-1"),
        )
    val friendDoc = stubWishlistDoc("friend-1", VALID_KEY)
    every { friendDoc.get() } returns Tasks.forResult(existenceSnapshot(true))
    val friendProfile = PublicProfile("friend-1", "Traveler")
    coEvery { publicProfileRepository.getProfile("friend-1") } returns friendProfile

    assertEquals(listOf(friendProfile), repository.getFriendsWhoWishlisted(VALID_KEY))
    verify(exactly = 0) { firestore.collectionGroup(any()) }

    coEvery { friendRepository.getFriends() } throws FriendRepositoryException.UnauthenticatedUser()
    assertTrue(
        captureFailure { repository.getFriendsWhoWishlisted(VALID_KEY) }
            is WishlistRepositoryException.UnauthenticatedUser
    )

    val failure = IllegalStateException("Friends failed")
    coEvery { friendRepository.getFriends() } throws failure
    val error = captureFailure { repository.getFriendsWhoWishlisted(VALID_KEY) }
    assertTrue(error is WishlistRepositoryException.FirestoreFailure)
    assertTrue(error.cause === failure)

    coEvery { friendRepository.getFriends() } throws PublicProfileException.UnauthenticatedUser()
    assertTrue(
        captureFailure { repository.getFriendsWhoWishlisted(VALID_KEY) }
            is WishlistRepositoryException.UnauthenticatedUser
    )

    coEvery { friendRepository.getFriends() } throws WishlistRepositoryException.InvalidUid()
    assertTrue(
        captureFailure { repository.getFriendsWhoWishlisted(VALID_KEY) }
            is WishlistRepositoryException.InvalidUid
    )
  }

  @Test
  fun cancellationPropagatesFromResolveItemHasWishlistedAndLoadProfile() = runTest {
    val wishlistCol = stubWishlistCollection(CURRENT_UID)
    val item =
        sampleItem(monumentKey = "k-cancel", sourceOwnerUid = "o1", sourceGuideId = "g-cancel")
    every { wishlistCol.get() } returns
        Tasks.forResult(
            querySnapshot(listOf(wishlistDocSnapshot(item.monumentKey, itemToMap(item))))
        )
    stubSavedGuideGet("o1", "g-cancel", Tasks.forCanceled())
    assertTrue(captureFailure { repository.getResolvedWishlist() } is CancellationException)

    val cancelWishlistDoc = stubWishlistDoc("u-cancel", VALID_KEY)
    every { cancelWishlistDoc.get() } returns Tasks.forCanceled()
    assertTrue(
        captureFailure { repository.getUsersWhoWishlisted(VALID_KEY, setOf("u-cancel")) }
            is CancellationException
    )

    val okWishlistDoc = stubWishlistDoc("u-cancel-prof", VALID_KEY)
    every { okWishlistDoc.get() } returns Tasks.forResult(existenceSnapshot(true))
    coEvery { publicProfileRepository.getProfile("u-cancel-prof") } throws
        CancellationException("Cancelled")
    assertTrue(
        captureFailure { repository.getUsersWhoWishlisted(VALID_KEY, setOf("u-cancel-prof")) }
            is CancellationException
    )
  }

  private fun stubUserDoc(uid: String): DocumentReference =
      userDocs.getOrPut(uid) {
        val doc = mockk<DocumentReference>()
        every { users.document(uid) } returns doc
        doc
      }

  private fun stubWishlistCollection(uid: String): CollectionReference =
      wishlistCollections.getOrPut(uid) {
        val userDoc = stubUserDoc(uid)
        val col = mockk<CollectionReference>()
        every { userDoc.collection("wishlist") } returns col
        col
      }

  private fun stubWishlistDoc(uid: String, monumentKey: String): DocumentReference {
    val col = stubWishlistCollection(uid)
    val doc = mockk<DocumentReference>()
    every { col.document(monumentKey) } returns doc
    return doc
  }

  private fun stubSavedGuideGet(uid: String, guideId: String, task: Task<DocumentSnapshot>) {
    val collection =
        savedGuideCollections.getOrPut(uid) {
          val userDoc = stubUserDoc(uid)
          val col = mockk<CollectionReference>()
          every { userDoc.collection("savedGuides") } returns col
          col
        }
    val doc = mockk<DocumentReference>()
    every { collection.document(guideId) } returns doc
    every { doc.get() } returns task
  }

  private fun guideDocSnapshot(
      id: String,
      exists: Boolean,
      data: Map<String, Any>?,
  ): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { exists() } returns exists
    every { this@mockk.data } returns data
  }

  private fun querySnapshot(docs: List<DocumentSnapshot>): QuerySnapshot = mockk {
    every { documents } returns docs
  }

  private fun existenceSnapshot(exists: Boolean): DocumentSnapshot = mockk {
    every { exists() } returns exists
  }

  private fun wishlistDocSnapshot(id: String, data: Map<String, Any?>?): DocumentSnapshot = mockk {
    every { this@mockk.id } returns id
    every { this@mockk.data } returns data
  }

  private fun sampleItem(
      monumentKey: String = VALID_KEY,
      sourceOwnerUid: String = "owner-1",
      sourceGuideId: String = "guide-1",
      monumentName: String = "Eiffel Tower",
      addedAt: Long = 1_700_000_000_000L,
      location: Coordinates? = null,
  ) = WishlistItem(monumentKey, sourceOwnerUid, sourceGuideId, monumentName, addedAt, location)

  private fun itemToMap(item: WishlistItem): Map<String, Any> = buildMap {
    put("monumentKey", item.monumentKey)
    put("sourceOwnerUid", item.sourceOwnerUid)
    put("sourceGuideId", item.sourceGuideId)
    put("monumentName", item.monumentName)
    put("addedAt", item.addedAt)
    item.location?.let {
      put("location", mapOf("latitude" to it.latitude, "longitude" to it.longitude))
    }
  }

  private fun validWishlistMap(
      monumentKey: String? = VALID_KEY,
      sourceOwnerUid: String? = "owner-1",
      sourceGuideId: String? = "guide-1",
      monumentName: String? = "Eiffel Tower",
      addedAt: Any? = 100L,
      location: Any? = null,
      includeLocation: Boolean = false,
  ): Map<String, Any?> = buildMap {
    monumentKey?.let { put("monumentKey", it) }
    sourceOwnerUid?.let { put("sourceOwnerUid", it) }
    sourceGuideId?.let { put("sourceGuideId", it) }
    monumentName?.let { put("monumentName", it) }
    addedAt?.let { put("addedAt", it) }
    if (includeLocation) put("location", location)
  }

  private fun validGuideFields(): Map<String, Any> =
      mapOf(
          "photo" to Blob.fromBytes(byteArrayOf(1, 2, 3)),
          "monumentName" to "Eiffel Tower",
          "confidence" to 0.95,
          "history" to "Built in 1889.",
          "facts" to listOf("330m tall"),
          "culture" to "Icon of Paris.",
          "sections" to emptyList<Map<String, String>>(),
          "interestCount" to 0L,
          "isFavorite" to false,
          "shareAudience" to "ALL_FRIENDS",
      )

  private fun fsException(code: FirebaseFirestoreException.Code) =
      FirebaseFirestoreException(code.name, code)

  private suspend fun captureFailure(action: suspend () -> Any?): Exception {
    try {
      action()
    } catch (exception: Exception) {
      return exception
    }
    throw AssertionError("Expected operation to fail")
  }

  private companion object {
    const val CURRENT_UID = "current-user"
    const val VALID_KEY = "48858_2294"
  }
}
