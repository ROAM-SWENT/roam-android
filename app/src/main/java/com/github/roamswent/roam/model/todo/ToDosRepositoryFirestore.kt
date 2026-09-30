package com.github.se.bootcamp.model.todo

import android.util.Log
import com.github.se.bootcamp.model.map.Location
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val TODOS_COLLECTION_PATH = "todos"

class ToDosRepositoryFirestore(private val db: FirebaseFirestore) : ToDosRepository {
  private val ownerAttributeName = "ownerId"

  override fun getNewUid(): String {
    return db.collection(TODOS_COLLECTION_PATH).document().id
  }

  override suspend fun getAllTodos(): List<ToDo> {
    val ownerId =
        Firebase.auth.currentUser?.uid
            ?: throw Exception("ToDosRepositoryFirestore: User not logged in.")

    val snapshot =
        db.collection(TODOS_COLLECTION_PATH).whereEqualTo(ownerAttributeName, ownerId).get().await()

    return snapshot.mapNotNull { documentToToDo(it) }
  }

  override suspend fun getTodo(todoID: String): ToDo {
    val document = db.collection(TODOS_COLLECTION_PATH).document(todoID).get().await()
    return documentToToDo(document) ?: throw Exception("ToDosRepositoryFirestore: ToDo not found")
  }

  override suspend fun addTodo(toDo: ToDo) {
    db.collection(TODOS_COLLECTION_PATH).document(toDo.uid).set(toDo).await()
  }

  override suspend fun editTodo(todoID: String, newValue: ToDo) {
    db.collection(TODOS_COLLECTION_PATH).document(todoID).set(newValue).await()
  }

  override suspend fun deleteTodo(todoID: String) {
    db.collection(TODOS_COLLECTION_PATH).document(todoID).delete().await()
  }

  /**
   * Performs a Firestore operation and calls the appropriate callback based on the result.
   *
   * @param task The Firestore task to perform.
   * @param onSuccess The callback to call if the operation is successful.
   * @param onFailure The callback to call if the operation fails.
   */
  private fun performFirestoreOperation(
      task: Task<Void>,
      onSuccess: () -> Unit,
      onFailure: (Exception) -> Unit
  ) {
    task.addOnCompleteListener { result ->
      if (result.isSuccessful) {
        onSuccess()
      } else {
        result.exception?.let { e ->
          Log.e("TodosRepositoryFirestore", "Error performing Firestore operation", e)
          onFailure(e)
        }
      }
    }
  }

  /**
   * Converts a Firestore document to a ToDo object.
   *
   * @param document The Firestore document to convert.
   * @return The ToDo object.
   */
  private fun documentToToDo(document: DocumentSnapshot): ToDo? {
    return try {
      val uid = document.id
      val name = document.getString("name") ?: return null
      val description = document.getString("description") ?: return null
      val assigneeName = document.getString("assigneeName") ?: return null
      val dueDate = document.getTimestamp("dueDate") ?: return null
      val locationData = document.get("location") as? Map<*, *>
      val location =
          locationData?.let {
            Location(
                latitude = it["latitude"] as? Double ?: 0.0,
                longitude = it["longitude"] as? Double ?: 0.0,
                name = it["name"] as? String ?: "")
          }
      val statusString = document.getString("status") ?: return null
      val status = ToDoStatus.valueOf(statusString)
      val ownerId = document.getString("ownerId") ?: return null

      ToDo(
          uid = uid,
          name = name,
          assigneeName = assigneeName,
          dueDate = dueDate,
          location = location,
          status = status,
          description = description,
          ownerId = ownerId)
    } catch (e: Exception) {
      Log.e("TodosRepositoryFirestore", "Error converting document to ToDo", e)
      null
    }
  }
}
