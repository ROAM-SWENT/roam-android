package com.github.se.bootcamp.utils

import android.util.Log
import com.github.se.bootcamp.model.todo.TODOS_COLLECTION_PATH
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDosRepository
import com.github.se.bootcamp.model.todo.ToDosRepositoryFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before

open class FirestoreBootcampTest(milestone: BootcampMilestone) : BootcampTest(milestone) {

  init {
    assert(milestone != BootcampMilestone.B1) {
      "FirestoreBootcampTest should not be used for Milestone 1, which does not use Firestore."
    }
  }

  suspend fun getTodosCount(): Int {
    val user = FirebaseEmulator.auth.currentUser ?: return 0
    return FirebaseEmulator.firestore
        .collection(TODOS_COLLECTION_PATH)
        .whereEqualTo("ownerId", user.uid)
        .get()
        .await()
        .size()
  }

  private suspend fun clearTestCollection() {
    val user = FirebaseEmulator.auth.currentUser ?: return
    val todos =
        FirebaseEmulator.firestore
            .collection(TODOS_COLLECTION_PATH)
            .whereEqualTo("ownerId", user.uid)
            .get()
            .await()

    val batch = FirebaseEmulator.firestore.batch()
    todos.documents.forEach { batch.delete(it.reference) }
    batch.commit().await()

    assert(getTodosCount() == 0) {
      "Test collection is not empty after clearing, count: ${getTodosCount()}"
    }
  }

  override fun createInitializedRepository(): ToDosRepository {
    return ToDosRepositoryFirestore(db = FirebaseEmulator.firestore)
  }

  override val todo1: ToDo
    get() = super.todo1.copy(ownerId = currentUser.uid)

  override val todo2: ToDo
    get() = super.todo2.copy(ownerId = currentUser.uid)

  override val todo3: ToDo
    get() = super.todo3.copy(ownerId = currentUser.uid)

  @Before
  override fun setUp() {
    super.setUp()
    runTest {
      val todosCount = getTodosCount()
      if (todosCount > 0) {
        Log.w(
            "FirebaseEmulatedTest",
            "Warning: Test collection is not empty at the beginning of the test, count: $todosCount",
        )
        clearTestCollection()
      }
    }
  }

  @After
  override fun tearDown() {
    runTest { clearTestCollection() }
    FirebaseEmulator.clearFirestoreEmulator()
    super.tearDown()
  }
}
