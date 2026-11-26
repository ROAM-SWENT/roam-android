/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendSearchTest {
  private val users = listOf("Alice", "Bob", "Alina")

  @Test
  fun emptyOrWhitespaceQueryReturnsAllItems() {
    assertEquals(users, filterByUsernameQuery(users, "  ") { it })
  }

  @Test
  fun queryMatchesUsernamesIgnoringCaseAndSurroundingWhitespace() {
    assertEquals(listOf("Alice", "Alina"), filterByUsernameQuery(users, " ali ") { it })
  }

  @Test
  fun queryMatchesPartialUsernames() {
    assertEquals(listOf("Bob"), filterByUsernameQuery(users, "o") { it })
  }

  @Test
  fun typoMatchesUsernameWhenSubstringMatchingFindsNothing() {
    assertEquals(listOf("Alice"), filterByUsernameQuery(users, "Alicee") { it })
  }

  @Test
  fun typoMatchingAllowsDigitsInUsernames() {
    val numberedUsers = listOf("Alex42", "Bob7")

    assertEquals(listOf("Alex42"), filterByUsernameQuery(numberedUsers, "Alex43") { it })
  }
}
