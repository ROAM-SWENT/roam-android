// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
}
