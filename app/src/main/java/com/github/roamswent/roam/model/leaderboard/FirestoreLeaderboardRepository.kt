// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed [LeaderboardRepository].
 *
 * Schema:
 * - `publicProfiles/{uid}.verifiedMonumentCount`: the user's total, an integer. A missing field
 *   means 0. It sits next to the username so that friends, who can already read public profiles,
 *   get both in one read.
 * - `users/{uid}/verifiedMonuments/{monumentKey.id}`: one empty document per counted monument,
 *   readable only by its owner. It holds no data: its ID, the coarsened cell of the monument's own
 *   position, is all that uniqueness needs. Saved guides are not used, as one monument can have
 *   several of them.
 *
 * Recording runs in a transaction because the check and the increment must be one step: without it,
 * two scans of the same monument, or of neighbouring cells, could both find nothing counted and
 * both increment. The transaction reads the profile and every cell of the neighbourhood, so if
 * another recording changes any of them before the commit, Firestore retries it with the new data.
 *
 * Security rules only let the owner raise the count, by exactly one per write, and only let a
 * counted-monument document be created together with that increment. They cannot prove that the
 * user was at the monument: a modified client can still report fabricated visits.
 */
class FirestoreLeaderboardRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : LeaderboardRepository {

  /**
   * Records [monument] for the signed-in user in one transaction. Nothing is written when a
   * monument in its [MonumentKey.neighbourhood] was counted before.
   *
   * @throws LeaderboardException.UnauthenticatedUser if no user is signed in.
   * @throws LeaderboardException.ProfileNotRegistered if the user has no public profile to hold the
   *   count.
   * @throws LeaderboardException.FirestoreFailure if the transaction failed.
   */
  override suspend fun recordVerifiedMonument(monument: MonumentKey): VerifiedMonumentResult {
    val uid = requireCurrentUid()
    val profileReference = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(uid)
    val monuments =
        firestore
            .collection(USERS_COLLECTION)
            .document(uid)
            .collection(VERIFIED_MONUMENTS_COLLECTION)
    val neighbourReferences =
        monument.neighbourhood().map { it.id }.sorted().map { monuments.document(it) }
    return withFirestoreErrors {
      firestore
          .runTransaction { transaction ->
            // Firestore requires every read of a transaction to come before its writes.
            val profile = transaction.get(profileReference)
            if (!profile.exists()) throw LeaderboardException.ProfileNotRegistered()
            val count = profile.getLong(VERIFIED_MONUMENT_COUNT_FIELD)?.toInt() ?: 0
            val alreadyCounted = neighbourReferences.any { transaction.get(it).exists() }
            if (alreadyCounted) return@runTransaction VerifiedMonumentResult.AlreadyCounted(count)

            transaction.set(monuments.document(monument.id), emptyMap<String, Any>())
            transaction.update(profileReference, VERIFIED_MONUMENT_COUNT_FIELD, count + 1)
            VerifiedMonumentResult.Counted(count + 1)
          }
          .await()
    }
  }

  /** Not implemented yet; ranking friends is the next step of issue #265. */
  override suspend fun getFriendsLeaderboard(): List<LeaderboardEntry> =
      TODO("Ranking friends is not implemented yet.")

  private fun requireCurrentUid(): String =
      auth.currentUser?.uid?.takeIf(String::isNotBlank)
          ?: throw LeaderboardException.UnauthenticatedUser()

  private suspend fun <T> withFirestoreErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: LeaderboardException) {
        throw exception
      } catch (exception: Exception) {
        throw LeaderboardException.FirestoreFailure(exception)
      }

  private companion object {
    const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
    const val USERS_COLLECTION = "users"
    const val VERIFIED_MONUMENTS_COLLECTION = "verifiedMonuments"
    const val VERIFIED_MONUMENT_COUNT_FIELD = "verifiedMonumentCount"
  }
}
