// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.model.profile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Firestore-backed public profiles with transactionally unique, case-insensitive usernames. */
class FirestorePublicProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : PublicProfileRepository {

  override suspend fun getCurrentProfile(): PublicProfile? = withFirestoreErrors {
    val uid = requireCurrentUid()
    val snapshot = publicProfiles().document(uid).get().await()
    if (snapshot.exists()) snapshot.toPublicProfile() else null
  }

  override suspend fun registerUsername(username: String): PublicProfile {
    val uid = requireCurrentUid()
    val usernameLower = normalizedUsername(username)
    return withFirestoreErrors {
      val profileReference = publicProfiles().document(uid)
      val claimReference = usernameClaims().document(usernameLower)
      firestore
          .runTransaction { transaction ->
            val profileSnapshot = transaction.get(profileReference)
            val claimSnapshot = transaction.get(claimReference)
            if (profileSnapshot.exists()) {
              val currentProfile = profileSnapshot.toPublicProfile()
              if (currentProfile.username.lowercase(Locale.ROOT) == usernameLower) {
                return@runTransaction currentProfile
              }
              throw PublicProfileException.UsernameAlreadyRegistered()
            }
            if (claimSnapshot.exists()) throw PublicProfileException.UsernameTaken()

            transaction.set(profileReference, profileFields(uid, username, usernameLower))
            transaction.set(claimReference, mapOf(UID_FIELD to uid))
            PublicProfile(uid, username)
          }
          .await()
    }
  }

  override suspend fun renameUsername(username: String): PublicProfile {
    val uid = requireCurrentUid()
    val usernameLower = normalizedUsername(username)
    return withFirestoreErrors {
      val profileReference = publicProfiles().document(uid)
      firestore
          .runTransaction { transaction ->
            val profileSnapshot = transaction.get(profileReference)
            if (!profileSnapshot.exists()) {
              throw PublicProfileException.ProfileNotRegistered()
            }

            val currentProfile = profileSnapshot.toPublicProfile()
            val oldUsernameLower = currentProfile.username.lowercase(Locale.ROOT)
            if (oldUsernameLower == usernameLower) {
              if (currentProfile.username == username) return@runTransaction currentProfile
              transaction.update(profileReference, USERNAME_FIELD, username)
              return@runTransaction currentProfile.copy(username = username)
            }

            val newClaimReference = usernameClaims().document(usernameLower)
            val newClaimSnapshot = transaction.get(newClaimReference)
            if (newClaimSnapshot.exists()) throw PublicProfileException.UsernameTaken()

            val oldClaimReference = usernameClaims().document(oldUsernameLower)
            val oldClaimSnapshot = transaction.get(oldClaimReference)
            check(oldClaimSnapshot.getString(UID_FIELD) == uid) {
              "The current username claim is missing or belongs to another user."
            }

            transaction.delete(oldClaimReference)
            transaction.set(newClaimReference, mapOf(UID_FIELD to uid))
            transaction.update(
                profileReference,
                profileFields(uid, username, usernameLower),
            )
            PublicProfile(uid, username)
          }
          .await()
    }
  }

  override suspend fun searchProfiles(prefix: String): List<PublicProfile> = withFirestoreErrors {
    requireCurrentUid()
    val normalizedPrefix = normalizedPrefix(prefix)
    if (normalizedPrefix.isEmpty()) return@withFirestoreErrors emptyList()

    publicProfiles()
        .orderBy(USERNAME_LOWER_FIELD)
        .startAt(normalizedPrefix)
        .endAt("$normalizedPrefix\uf8ff")
        .limit(MAX_SEARCH_RESULTS.toLong())
        .get()
        .await()
        .documents
        .map { it.toPublicProfile() }
  }

  private fun requireCurrentUid(): String =
      auth.currentUser?.uid?.takeIf(String::isNotBlank)
          ?: throw PublicProfileException.UnauthenticatedUser()

  private fun publicProfiles() = firestore.collection(PUBLIC_PROFILES_COLLECTION)

  private fun usernameClaims() = firestore.collection(USERNAME_CLAIMS_COLLECTION)

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: PublicProfileException) {
        throw exception
      } catch (exception: Exception) {
        throw PublicProfileException.FirestoreFailure(exception)
      }

  private fun DocumentSnapshot.toPublicProfile(): PublicProfile {
    val uid = checkNotNull(getString(UID_FIELD)) { "Public profile is missing its UID." }
    val username =
        checkNotNull(getString(USERNAME_FIELD)) { "Public profile is missing its username." }
    val usernameLower =
        checkNotNull(getString(USERNAME_LOWER_FIELD)) {
          "Public profile is missing its normalized username."
        }
    check(username.matches(USERNAME_PATTERN) && username.lowercase(Locale.ROOT) == usernameLower) {
      "Public profile contains an invalid username."
    }
    return PublicProfile(uid, username)
  }

  private fun profileFields(uid: String, username: String, usernameLower: String) =
      mapOf(
          UID_FIELD to uid,
          USERNAME_FIELD to username,
          USERNAME_LOWER_FIELD to usernameLower,
      )

  private fun normalizedUsername(username: String): String {
    if (!username.matches(USERNAME_PATTERN)) {
      throw PublicProfileException.InvalidUsername()
    }
    return username.lowercase(Locale.ROOT)
  }

  private fun normalizedPrefix(prefix: String): String {
    if (
        prefix.length > MAX_USERNAME_LENGTH ||
            prefix.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '_' }
    ) {
      throw PublicProfileException.InvalidUsername()
    }
    return prefix.lowercase(Locale.ROOT)
  }

  private companion object {
    const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
    const val USERNAME_CLAIMS_COLLECTION = "usernameClaims"
    const val UID_FIELD = "uid"
    const val USERNAME_FIELD = "username"
    const val USERNAME_LOWER_FIELD = "usernameLower"
    const val MAX_USERNAME_LENGTH = 20
    const val MAX_SEARCH_RESULTS = 20
    val USERNAME_PATTERN = Regex("[A-Za-z0-9_]{3,20}")
  }
}
