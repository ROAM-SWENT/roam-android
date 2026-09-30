package com.github.se.bootcamp.model.todo

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Provides a single instance of the repository in the app. `repository` is mutable for testing
 * purposes.
 */
object ToDosRepositoryProvider {
  private val _repository: ToDosRepository by lazy { ToDosRepositoryFirestore(Firebase.firestore) }

  var repository: ToDosRepository = _repository
}
