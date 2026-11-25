// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.guide

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requires an Android emulator and the Firebase auth and Firestore emulators on the host. Run
 * `firebase emulators:start --project demo-roam-rules --only auth,firestore` before this test.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSharedGuideEmulatorTest {
  @Test
  fun deletedAndRevokedGuidesBecomeUnavailableToFriend() = runBlocking {
    val owner = emulatorClient()
    val friend = emulatorClient()
    try {
      val ownerUid = checkNotNull(owner.auth.signInAnonymously().await().user).uid
      val friendUid = checkNotNull(friend.auth.signInAnonymously().await().user).uid
      val username = "u${UUID.randomUUID().toString().replace("-", "").take(19)}"
      val profile = friend.firestore.collection("publicProfiles").document(friendUid)
      val claim = friend.firestore.collection("usernameClaims").document(username)
      friend.firestore
          .batch()
          .set(
              profile,
              mapOf("uid" to friendUid, "username" to username, "usernameLower" to username),
          )
          .set(claim, mapOf("uid" to friendUid))
          .commit()
          .await()

      val participants = listOf(ownerUid, friendUid).sorted()
      val pairId = participants.joinToString("~")
      val ownerFriendship = owner.firestore.collection("friendships").document(pairId)
      ownerFriendship
          .set(
              mapOf(
                  "participantUids" to participants,
                  "requesterUid" to ownerUid,
                  "recipientUid" to friendUid,
                  "status" to "PENDING",
              )
          )
          .await()
      friend.firestore
          .collection("friendships")
          .document(pairId)
          .update("status", "ACCEPTED")
          .await()

      val guide =
          Guide(
              id = "shared-guide",
              photoBytes = byteArrayOf(1, 2, 3),
              monumentName = "Test monument",
              confidence = 0.9,
              history = "History",
              facts = listOf("Fact"),
              culture = "Culture",
              shareSettings = MonumentShareSettings(audience = ShareAudience.ALL_FRIENDS),
          )
      val ownerGuides = FirestoreSavedGuideRepository(owner.firestore, owner.auth)
      val friendSharing = FirestoreMonumentSharingRepository(friend.firestore, friend.auth)
      ownerGuides.addGuide(guide)
      assertNotNull(friendSharing.getSharedGuideFrom(ownerUid, guide.id))

      ownerGuides.removeGuide(guide.id)
      val friendRef =
          friend.firestore
              .collection("users")
              .document(ownerUid)
              .collection("savedGuides")
              .document(guide.id)
      try {
        friendRef.get(Source.SERVER).await()
        fail("The rules must deny a friend reading a deleted guide")
      } catch (exception: FirebaseFirestoreException) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception.code)
      }
      assertNull(friendSharing.getSharedGuideFrom(ownerUid, guide.id))

      ownerGuides.addGuide(guide)
      assertNotNull(friendSharing.getSharedGuideFrom(ownerUid, guide.id))
      FirestoreMonumentSharingRepository(owner.firestore, owner.auth)
          .setShareAudience(guide.id, ShareAudience.NONE)
      assertNull(friendSharing.getSharedGuideFrom(ownerUid, guide.id))

      ownerGuides.removeGuide(guide.id)
      ownerFriendship.delete().await()
    } finally {
      owner.firestore.terminate().await()
      friend.firestore.terminate().await()
      owner.app.delete()
      friend.app.delete()
    }
  }

  private fun emulatorClient(): EmulatorClient {
    val options =
        FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:1234567890abcdef")
            .setApiKey("emulator-key")
            .setProjectId("demo-roam-rules")
            .build()
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            options,
            "shared-guide-${UUID.randomUUID()}",
        )
    val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
    val firestore =
        FirebaseFirestore.getInstance(app).apply {
          useEmulator("10.0.2.2", 8080)
          firestoreSettings =
              FirebaseFirestoreSettings.Builder()
                  .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                  .build()
        }
    return EmulatorClient(app, auth, firestore)
  }

  private data class EmulatorClient(
      val app: FirebaseApp,
      val auth: FirebaseAuth,
      val firestore: FirebaseFirestore,
  )
}
