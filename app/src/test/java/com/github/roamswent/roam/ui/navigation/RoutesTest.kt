// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutesTest {

  @Test
  fun monumentDetailPutsTheGuideIdInThePath() {
    assertEquals("monument_detail/abc123", Routes.monumentDetail("abc123"))
  }

  @Test
  fun monumentDetailEncodesCharactersThatWouldBreakThePath() {
    assertEquals("monument_detail/a%2Fb%3Fc", Routes.monumentDetail("a/b?c"))
  }

  @Test
  fun friendCollectionEncodesUidAndOptionalName() {
    assertEquals(
        "friend_collection/uid%2F1?friendName=Alice%20B",
        Routes.friendCollection("uid/1", "Alice B"),
    )
    assertEquals("friend_collection/uid1", Routes.friendCollection("uid1", ""))
  }

  @Test
  fun sharedMonumentDetailPutsEncodedUidAndGuideIdInThePath() {
    assertEquals("shared_monument_detail/uid1/a%2Fb", Routes.sharedMonumentDetail("uid1", "a/b"))
  }
}
