// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.leaderboard

import com.github.roamswent.roam.model.firestore.RoamFirestore
import com.github.roamswent.roam.model.friend.FirestoreFriendRepository
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRepositoryException
import com.github.roamswent.roam.model.profile.FirestorePublicProfileRepository
import com.github.roamswent.roam.model.profile.PublicProfileException
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * - `users/{uid}/leaderboard/state.lastVerifiedMonumentId`: the ID of the monument counted by the
 *   latest increment, readable only by its owner. It lets the security rules match each increment
 *   to the one counted-monument document it creates.
 *
 * Recording runs in a transaction because the check and the increment must be one step: without it,
 * two scans of the same monument, or of neighbouring cells, could both find nothing counted and
 * both increment. The transaction reads the profile and every cell of the neighbourhood, so if
 * another recording changes any of them before the commit, Firestore retries it with the new data.
 *
 * Security rules only let the owner raise the count, by exactly one per write, and only together
 * with a new counted-monument document and a pointer naming it; that document can only be created
 * with both. So the count always equals the number of counted monuments. The rules cannot prove
 * that the user was at the monument: a modified client can still report fabricated visits.
 *
 * The leaderboard reads no Firestore document itself: accepted friends come from [friendRepository]
 * and usernames and counts from [publicProfileRepository].
 */
class FirestoreLeaderboardRepository(
    private val firestore: FirebaseFirestore = RoamFirestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val friendRepository: FriendRepository = FirestoreFriendRepository(firestore, auth),
    private val publicProfileRepository: PublicProfileRepository =
        FirestorePublicProfileRepository(firestore, auth),
) : LeaderboardRepository {

  /**
   * Records [monument] for the signed-in user in one transaction that creates its counted-monument
   * document, points the leaderboard state at it and increments the count. Nothing is written when
   * a monument in its [MonumentKey.neighbourhood] was counted before. As touching cells are not
   * transitive, the count can depend on scan order and both over- and under-count; see
   * [MonumentKey].
   *
   * @throws LeaderboardException.UnauthenticatedUser if no user is signed in.
   * @throws LeaderboardException.ProfileNotRegistered if the user has no public profile to hold the
   *   count.
   * @throws LeaderboardException.FirestoreFailure if the transaction failed.
   */
  override suspend fun recordVerifiedMonument(monument: MonumentKey): VerifiedMonumentResult {
    val uid = requireCurrentUid()
    val profileReference = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(uid)
    val userDocument = firestore.collection(USERS_COLLECTION).document(uid)
    val monuments = userDocument.collection(VERIFIED_MONUMENTS_COLLECTION)
    val stateReference =
        userDocument.collection(LEADERBOARD_COLLECTION).document(LEADERBOARD_STATE_DOCUMENT)
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
            transaction.set(stateReference, mapOf(LAST_VERIFIED_MONUMENT_ID_FIELD to monument.id))
            transaction.update(profileReference, VERIFIED_MONUMENT_COUNT_FIELD, count + 1)
            VerifiedMonumentResult.Counted(count + 1)
          }
          .await()
    }
  }

  /**
   * Ranks the signed-in user and their accepted friends with [rankLeaderboard]. Profiles are loaded
   * in parallel. A friend without a public profile is left out, as there is no username to show.
   *
   * @throws LeaderboardException.UnauthenticatedUser if no user is signed in.
   * @throws LeaderboardException.ProfileNotRegistered if the signed-in user has no public profile.
   * @throws LeaderboardException.FirestoreFailure if friends or profiles could not be loaded, or a
   *   profile is invalid.
   */
  override suspend fun getFriendsLeaderboard(): List<LeaderboardEntry> {
    val uid = requireCurrentUid()
    return withFirestoreErrors {
      val friendUids = friendRepository.getFriends().map { it.friendUid }.distinct() - uid
      val (currentProfile, friendProfiles) =
          coroutineScope {
            val current = async { publicProfileRepository.getProfile(uid) }
            val friends = friendUids.map { async { publicProfileRepository.getProfile(it) } }
            current.await() to friends.awaitAll()
          }
      if (currentProfile == null) throw LeaderboardException.ProfileNotRegistered()
      rankLeaderboard(listOf(currentProfile) + friendProfiles.filterNotNull())
    }
  }

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
      } catch (exception: FriendRepositoryException.UnauthenticatedUser) {
        throw LeaderboardException.UnauthenticatedUser()
      } catch (exception: PublicProfileException.UnauthenticatedUser) {
        throw LeaderboardException.UnauthenticatedUser()
      } catch (exception: Exception) {
        throw LeaderboardException.FirestoreFailure(exception)
      }

  private companion object {
    const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
    const val USERS_COLLECTION = "users"
    const val VERIFIED_MONUMENTS_COLLECTION = "verifiedMonuments"
    const val VERIFIED_MONUMENT_COUNT_FIELD = "verifiedMonumentCount"
    const val LEADERBOARD_COLLECTION = "leaderboard"
    const val LEADERBOARD_STATE_DOCUMENT = "state"
    const val LAST_VERIFIED_MONUMENT_ID_FIELD = "lastVerifiedMonumentId"
  }
}
