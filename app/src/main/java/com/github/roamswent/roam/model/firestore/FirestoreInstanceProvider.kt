// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

internal class FirestoreInstanceProvider(private val firestoreFactory: () -> FirebaseFirestore) {
  val instance: FirebaseFirestore by lazy {
    firestoreFactory().apply {
      // Firestore rejects settings changes after its first operation.
      firestoreSettings =
          FirebaseFirestoreSettings.Builder()
              .setLocalCacheSettings(
                  PersistentCacheSettings.newBuilder()
                      .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                      .build()
              )
              .build()
    }
  }
}

internal object RoamFirestore {
  private val provider = FirestoreInstanceProvider { FirebaseFirestore.getInstance() }

  val instance: FirebaseFirestore
    get() = provider.instance
}
