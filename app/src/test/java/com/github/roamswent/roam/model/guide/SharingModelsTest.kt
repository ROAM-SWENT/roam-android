// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>

package com.github.roamswent.roam.model.guide

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingModelsTest {

  @Test
  fun monumentShareSettingsDefaultsToAllFriendsAndEmptyRecipientSets() {
    val settings = MonumentShareSettings()

    assertEquals(DEFAULT_SHARE_AUDIENCE, settings.audience)
    assertEquals(ShareAudience.ALL_FRIENDS, settings.audience)
    assertEquals(MonumentShareSettings.DEFAULT_SHARE_AUDIENCE, settings.audience)
    assertTrue(settings.sharedWithUids.isEmpty())
    assertTrue(settings.dismissedByUids.isEmpty())
  }

  @Test
  fun effectiveRecipientUidsReturnsEmptyWhenAudienceIsNone() {
    val settings =
        MonumentShareSettings(
            audience = ShareAudience.NONE,
            sharedWithUids = setOf("user-1", "user-2"),
            dismissedByUids = setOf("user-1"),
        )

    val recipients =
        settings.effectiveRecipientUids(
            friendUids = setOf("friend-1", "friend-2"),
            followerUids = setOf("follower-1"),
        )

    assertTrue(recipients.isEmpty())
  }

  @Test
  fun effectiveRecipientUidsForSelectedFriendsReturnsSharedExcludingDismissed() {
    val settings =
        MonumentShareSettings(
            audience = ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf("user-1", "user-2", "user-3"),
            dismissedByUids = setOf("user-2"),
        )

    val recipients =
        settings.effectiveRecipientUids(
            friendUids = setOf("friend-1"),
            followerUids = setOf("follower-1"),
        )

    assertEquals(setOf("user-1", "user-3"), recipients)
  }

  @Test
  fun effectiveRecipientUidsForAllFriendsReturnsFriendsExcludingDismissedAndFollowers() {
    val settings =
        MonumentShareSettings(
            audience = ShareAudience.ALL_FRIENDS,
            sharedWithUids = setOf("selected-1"),
            dismissedByUids = setOf("friend-2"),
        )

    val recipients =
        settings.effectiveRecipientUids(
            friendUids = setOf("friend-1", "friend-2", "friend-3"),
            followerUids = setOf("follower-1"),
        )

    assertEquals(setOf("friend-1", "friend-3"), recipients)
    assertFalse(recipients.contains("follower-1"))
  }

  @Test
  fun effectiveRecipientUidsForAllFollowersCombinesFriendsAndFollowersMinusDismissed() {
    val settings =
        MonumentShareSettings(
            audience = ShareAudience.ALL_FOLLOWERS,
            sharedWithUids = emptySet(),
            dismissedByUids = setOf("friend-dismissed", "follower-dismissed"),
        )

    val recipients =
        settings.effectiveRecipientUids(
            friendUids = setOf("friend-only", "friend-dismissed"),
            followerUids = setOf("follower-only", "follower-dismissed"),
        )

    assertEquals(setOf("friend-only", "follower-only"), recipients)
  }

  @Test
  fun accountPrivacyStateDefaults() {
    val state = AccountPrivacyState()

    assertFalse(state.isPublic)
    assertTrue(state.followedUids.isEmpty())
    assertTrue(state.followerUids.isEmpty())
  }

  @Test
  fun availableAudiencesForPrivateAccountExcludesAllFollowers() {
    val privateState = AccountPrivacyState(isPublic = false)

    assertEquals(
        listOf(
            ShareAudience.NONE,
            ShareAudience.ALL_FRIENDS,
            ShareAudience.SELECTED_FRIENDS,
        ),
        privateState.availableAudiences(),
    )
  }

  @Test
  fun availableAudiencesForPublicAccountIncludesAllFollowers() {
    val publicState = AccountPrivacyState(isPublic = true)

    assertEquals(
        listOf(
            ShareAudience.NONE,
            ShareAudience.ALL_FOLLOWERS,
            ShareAudience.ALL_FRIENDS,
            ShareAudience.SELECTED_FRIENDS,
        ),
        publicState.availableAudiences(),
    )
  }

  @Test
  fun stubAccountPrivacyProviderDefaultState() = runTest {
    val provider = StubAccountPrivacyProvider()

    assertEquals(AccountPrivacyState(), provider.getPrivacyState())
    assertFalse(provider.isPublicAccount())
  }

  @Test
  fun stubAccountPrivacyProviderCustomStatePassthrough() = runTest {
    val customState =
        AccountPrivacyState(
            isPublic = true,
            followedUids = setOf("user-a"),
            followerUids = setOf("user-b"),
        )
    val provider = StubAccountPrivacyProvider(customState)

    assertEquals(customState, provider.getPrivacyState())
    assertTrue(provider.isPublicAccount())
  }

  @Test
  fun monumentSharingExceptionSubclassesPreserveMessagesAndCauses() {
    val cause = RuntimeException("underlying-cause")

    val unauthDefault = MonumentSharingException.UnauthenticatedUser()
    assertEquals("A signed-in user is required for this operation.", unauthDefault.message)
    assertNull(unauthDefault.cause)

    val unauthCustom = MonumentSharingException.UnauthenticatedUser("Custom unauth", cause)
    assertEquals("Custom unauth", unauthCustom.message)
    assertSame(cause, unauthCustom.cause)

    val invalidUidDefault = MonumentSharingException.InvalidUid()
    assertEquals("The user ID is not a valid UID.", invalidUidDefault.message)
    assertNull(invalidUidDefault.cause)

    val invalidUidCustom = MonumentSharingException.InvalidUid("Custom invalid", cause)
    assertEquals("Custom invalid", invalidUidCustom.message)
    assertSame(cause, invalidUidCustom.cause)

    val notFoundDefault = MonumentSharingException.GuideNotFound()
    assertEquals("The guide was not found.", notFoundDefault.message)
    assertNull(notFoundDefault.cause)

    val notFoundCustom = MonumentSharingException.GuideNotFound("Custom not found", cause)
    assertEquals("Custom not found", notFoundCustom.message)
    assertSame(cause, notFoundCustom.cause)

    val notOwnerDefault = MonumentSharingException.NotGuideOwner()
    assertEquals("Only the guide owner can perform this operation.", notOwnerDefault.message)
    assertNull(notOwnerDefault.cause)

    val notOwnerCustom = MonumentSharingException.NotGuideOwner("Custom not owner", cause)
    assertEquals("Custom not owner", notOwnerCustom.message)
    assertSame(cause, notOwnerCustom.cause)

    val unauthRecipDefault = MonumentSharingException.UnauthorizedRecipient()
    assertEquals(
        "The recipient is not authorized to access this guide.",
        unauthRecipDefault.message,
    )
    assertNull(unauthRecipDefault.cause)

    val unauthRecipCustom =
        MonumentSharingException.UnauthorizedRecipient("Custom recipient", cause)
    assertEquals("Custom recipient", unauthRecipCustom.message)
    assertSame(cause, unauthRecipCustom.cause)

    val firestoreWithMsg = MonumentSharingException.FirestoreFailure(cause)
    assertEquals("underlying-cause", firestoreWithMsg.message)
    assertSame(cause, firestoreWithMsg.cause)

    val emptyCause = Exception(null as String?)
    val firestoreWithoutMsg = MonumentSharingException.FirestoreFailure(emptyCause)
    assertEquals("The sharing operation failed in Firestore.", firestoreWithoutMsg.message)
    assertSame(emptyCause, firestoreWithoutMsg.cause)
  }

  @Test
  fun relationshipKindEnumValues() {
    assertEquals(
        listOf(RelationshipKind.BIDIRECTIONAL_FRIEND, RelationshipKind.UNIDIRECTIONAL_FOLLOWER),
        RelationshipKind.entries,
    )
  }

  @Test
  fun monumentSharingProviderReturnsRepository() {
    val dummyRepo =
        object : MonumentSharingRepository {
          override suspend fun setShareAudience(
              guideId: String,
              audience: ShareAudience,
              recipientUids: Set<String>,
          ) {}

          override suspend fun grantAccess(guideId: String, recipientUids: Set<String>) {}

          override suspend fun revokeAccess(guideId: String, recipientUids: Set<String>) {}

          override suspend fun dismissSharedGuide(ownerUid: String, guideId: String) {}

          override suspend fun getShareSettings(guideId: String): MonumentShareSettings =
              MonumentShareSettings()

          override suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide> = emptyList()

          override fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>> = emptyFlow()
        }
    val provider = MonumentSharingProvider { dummyRepo }
    assertSame(dummyRepo, provider.repository())
  }

  @Test
  fun monumentSharingRepositoryDefaultMethodArgs() = runTest {
    var invokedWithDefault = false
    val repo =
        object : MonumentSharingRepository {
          override suspend fun setShareAudience(
              guideId: String,
              audience: ShareAudience,
              recipientUids: Set<String>,
          ) {
            if (recipientUids.isEmpty()) {
              invokedWithDefault = true
            }
          }

          override suspend fun grantAccess(guideId: String, recipientUids: Set<String>) {}

          override suspend fun revokeAccess(guideId: String, recipientUids: Set<String>) {}

          override suspend fun dismissSharedGuide(ownerUid: String, guideId: String) {}

          override suspend fun getShareSettings(guideId: String): MonumentShareSettings =
              MonumentShareSettings()

          override suspend fun getSharedGuidesFrom(ownerUid: String): List<Guide> = emptyList()

          override fun observeSharedGuidesFrom(ownerUid: String): Flow<List<Guide>> = emptyFlow()
        }
    repo.setShareAudience("guide-1", ShareAudience.NONE)
    assertTrue(invokedWithDefault)
  }

  @Test
  fun monumentShareSettingsDataClassMethods() {
    val settings =
        MonumentShareSettings(
            audience = ShareAudience.SELECTED_FRIENDS,
            sharedWithUids = setOf("u1"),
            dismissedByUids = setOf("u2"),
        )
    val copy = settings.copy(audience = ShareAudience.ALL_FOLLOWERS)
    assertEquals(ShareAudience.ALL_FOLLOWERS, copy.audience)
    val (aud, shared, dismissed) = settings
    assertEquals(ShareAudience.SELECTED_FRIENDS, aud)
    assertEquals(setOf("u1"), shared)
    assertEquals(setOf("u2"), dismissed)
    assertTrue(settings.toString().contains("SELECTED_FRIENDS"))
  }

  @Test
  fun accountPrivacyStateDataClassMethods() {
    val state =
        AccountPrivacyState(
            isPublic = true,
            followedUids = setOf("u1"),
            followerUids = setOf("u2"),
        )
    val copy = state.copy(isPublic = false)
    assertFalse(copy.isPublic)
    val (pub, followed, follower) = state
    assertTrue(pub)
    assertEquals(setOf("u1"), followed)
    assertEquals(setOf("u2"), follower)
    assertTrue(state.toString().contains("isPublic=true"))
  }
}
