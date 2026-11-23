// Co-authored-by: Gemini 4 Neon <noreply@google.com>

package com.github.roamswent.roam.model.wishlist

import com.github.roamswent.roam.model.guide.Guide
import com.github.roamswent.roam.model.location.Coordinates

/**
 * A lightweight wishlist entry referencing a shared monument guide without duplicating its media or
 * full guide payload.
 *
 * @property monumentKey Canonical monument key used as the Firestore document ID.
 * @property sourceOwnerUid UID of the user who owns the referenced guide.
 * @property sourceGuideId Document ID of the referenced guide under the owner's saved guides.
 * @property monumentName Display name of the monument at the time it was wishlisted.
 * @property addedAt Unix epoch timestamp in milliseconds when the item was wishlisted.
 * @property location Optional GPS coordinates of the monument.
 */
data class WishlistItem(
    val monumentKey: String,
    val sourceOwnerUid: String,
    val sourceGuideId: String,
    val monumentName: String,
    val addedAt: Long,
    val location: Coordinates? = null,
)

/** Reason why a wishlisted guide could not be resolved from its source owner. */
enum class WishlistUnavailableReason {
  /** The viewer no longer has permission to read the referenced guide. */
  ACCESS_REVOKED,

  /** The referenced guide document no longer exists. */
  GUIDE_DELETED,

  /** An unexpected or transient failure prevented resolving the guide. */
  UNKNOWN,
}

/**
 * Result of resolving a [WishlistItem] against its referenced [Guide].
 *
 * @property item The underlying [WishlistItem] metadata.
 */
sealed interface ResolvedWishlistItem {
  val item: WishlistItem

  /**
   * A wishlist entry whose referenced [guide] was successfully loaded.
   *
   * @property item The underlying [WishlistItem] metadata.
   * @property guide The resolved [Guide].
   */
  data class Available(
      override val item: WishlistItem,
      val guide: Guide,
  ) : ResolvedWishlistItem

  /**
   * A wishlist entry whose referenced guide could not be loaded.
   *
   * @property item The underlying [WishlistItem] metadata.
   * @property reason The reason the guide is currently unavailable.
   */
  data class Unavailable(
      override val item: WishlistItem,
      val reason: WishlistUnavailableReason,
  ) : ResolvedWishlistItem
}

/** A distinguishable failure from a wishlist repository operation. */
sealed class WishlistRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
  /** Thrown when no signed-in user is available for the operation. */
  class UnauthenticatedUser :
      WishlistRepositoryException("A signed-in user is required for this operation.")

  /** Thrown when a provided UID does not match the valid Firebase UID format. */
  class InvalidUid : WishlistRepositoryException("The user ID is not a valid Firebase UID.")

  /** Thrown when a provided monument key fails validation. */
  class InvalidMonumentKey : WishlistRepositoryException("The monument key is invalid.")

  /** Thrown when a provided guide ID fails validation. */
  class InvalidGuideId : WishlistRepositoryException("The guide ID is invalid.")

  /** Thrown when a wishlist item's monument name is blank or invalid. */
  class InvalidMonumentName : WishlistRepositoryException("The monument name must not be blank.")

  /** Thrown when an underlying Firestore error occurs. */
  class FirestoreFailure(cause: Throwable) :
      WishlistRepositoryException("The wishlist operation failed in Firestore.", cause)
}
