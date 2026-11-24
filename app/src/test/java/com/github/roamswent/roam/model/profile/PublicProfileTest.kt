// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
    assertNotEquals(profile, PublicProfile("uid1", "Alice", byteArrayOf(1)))
    assertNotEquals(profile, null)
    assertNotEquals(profile, "not a profile")
  }

  @Test
  fun equals_comparesAvatarBytesByContent() {
    val profile = PublicProfile("uid1", "Alice", byteArrayOf(1, 2, 3))

    assertEquals(
        profile,
        PublicProfile("uid1", "Alice", byteArrayOf(1, 2, 3)),
    )

    assertNotEquals(
        profile,
        PublicProfile("uid1", "Alice", byteArrayOf(1, 2, 4)),
    )

    assertNotEquals(
        profile,
        PublicProfile("uid1", "Alice"),
    )
  }

  @Test
  fun hashCode_usesAvatarContentAndHandlesNull() {
    val withoutAvatar = PublicProfile("uid1", "Alice")
    val withAvatar = PublicProfile("uid1", "Alice", byteArrayOf(1, 2, 3))
    val sameAvatar = PublicProfile("uid1", "Alice", byteArrayOf(1, 2, 3))

    val baseHash = 31 * (31 * "uid1".hashCode() + "Alice".hashCode())

    assertEquals(baseHash, withoutAvatar.hashCode())
    assertEquals(
        baseHash + byteArrayOf(1, 2, 3).contentHashCode(),
        withAvatar.hashCode(),
    )
    assertEquals(withAvatar.hashCode(), sameAvatar.hashCode())
  }
}
