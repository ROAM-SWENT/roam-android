// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>
package com.github.roamswent.roam.model.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PublicProfileTest {

  @Test
  fun equals_comparesProfilesWithNullAvatars() {
    val profile = PublicProfile("uid1", "Alice")

    assertEquals(profile, PublicProfile("uid1", "Alice"))
    assertNotEquals(profile, PublicProfile("uid2", "Alice"))
    assertNotEquals(profile, PublicProfile("uid1", "Bob"))
    assertNotEquals(profile, PublicProfile("uid1", "Alice", avatarBytes = byteArrayOf(1)))
    assertNotEquals(profile, null)
    assertNotEquals(profile, "not a profile")
  }

  @Test
  fun equals_comparesAvatarBytesByContent() {
    val profile = PublicProfile("uid1", "Alice", avatarBytes = byteArrayOf(1, 2, 3))

    assertEquals(
        profile,
        PublicProfile("uid1", "Alice", avatarBytes = byteArrayOf(1, 2, 3)),
    )

    assertNotEquals(
        profile,
        PublicProfile("uid1", "Alice", avatarBytes = byteArrayOf(1, 2, 4)),
    )

    assertNotEquals(
        profile,
        PublicProfile("uid1", "Alice"),
    )
  }

  @Test
  fun equals_comparesVerifiedMonumentCount() {
    val profile = PublicProfile("uid1", "Alice", verifiedMonumentCount = 3)

    assertEquals(profile, PublicProfile("uid1", "Alice", verifiedMonumentCount = 3))
    assertNotEquals(profile, PublicProfile("uid1", "Alice", verifiedMonumentCount = 4))
    assertNotEquals(profile, PublicProfile("uid1", "Alice"))
  }

  @Test
  fun hashCode_isEqualForProfilesWithSameContent() {
    val profile =
        PublicProfile(
            "uid1",
            "Alice",
            verifiedMonumentCount = 3,
            avatarBytes = byteArrayOf(1, 2, 3),
        )
    val sameContent =
        PublicProfile(
            "uid1",
            "Alice",
            verifiedMonumentCount = 3,
            avatarBytes = byteArrayOf(1, 2, 3),
        )

    assertEquals(profile.hashCode(), sameContent.hashCode())
  }

  @Test
  fun hashCode_changesWithAvatarBytes() {
    val profile = PublicProfile("uid1", "Alice", avatarBytes = byteArrayOf(1, 2, 3))

    assertNotEquals(
        profile.hashCode(),
        PublicProfile("uid1", "Alice", avatarBytes = byteArrayOf(1, 2, 4)).hashCode(),
    )
    assertNotEquals(profile.hashCode(), PublicProfile("uid1", "Alice").hashCode())
  }

  @Test
  fun hashCode_changesWithVerifiedMonumentCount() {
    val profile = PublicProfile("uid1", "Alice", verifiedMonumentCount = 3)

    assertNotEquals(
        profile.hashCode(),
        PublicProfile("uid1", "Alice", verifiedMonumentCount = 4).hashCode(),
    )
  }

  @Test
  fun hashCode_handlesNullAvatar() {
    val withoutAvatar = PublicProfile("uid1", "Alice")

    assertEquals(withoutAvatar.hashCode(), PublicProfile("uid1", "Alice").hashCode())
    assertEquals(
        withoutAvatar.hashCode(),
        PublicProfile("uid1", "Alice", avatarBytes = null).hashCode(),
    )
  }

  @Test
  fun equals_comparesPrivacySettings() {
    val profile = PublicProfile("uid1", "Alice", isPublic = true, allowFriendRequests = false)

    assertEquals(
        profile,
        PublicProfile("uid1", "Alice", isPublic = true, allowFriendRequests = false),
    )
    assertNotEquals(
        profile,
        PublicProfile("uid1", "Alice", isPublic = false, allowFriendRequests = false),
    )
    assertNotEquals(
        profile,
        PublicProfile("uid1", "Alice", isPublic = true, allowFriendRequests = true),
    )
  }

  @Test
  fun hashCode_changesWithPrivacySettings() {
    val profile = PublicProfile("uid1", "Alice", isPublic = false, allowFriendRequests = true)

    assertNotEquals(
        profile.hashCode(),
        PublicProfile("uid1", "Alice", isPublic = true, allowFriendRequests = true).hashCode(),
    )
    assertNotEquals(
        profile.hashCode(),
        PublicProfile("uid1", "Alice", isPublic = false, allowFriendRequests = false).hashCode(),
    )
  }
}
