// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.firestore

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FirestoreInstanceProviderTest {
  @Test
  fun configuresAndReusesFirestoreBeforeReturningIt() {
    val firestore = mockk<FirebaseFirestore>()
    val collection = mockk<CollectionReference>()
    every { firestore.firestoreSettings = any() } just Runs
    every { firestore.collection("users") } returns collection
    var factoryCalls = 0
    val provider = FirestoreInstanceProvider {
      factoryCalls++
      firestore
    }

    val first = provider.instance
    val second = provider.instance
    first.collection("users")

    assertSame(firestore, first)
    assertSame(first, second)
    assertEquals(1, factoryCalls)
    verifyOrder {
      firestore.firestoreSettings = any()
      firestore.collection("users")
    }
  }
}
