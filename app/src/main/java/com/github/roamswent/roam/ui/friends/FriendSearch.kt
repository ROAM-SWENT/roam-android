/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

/** Returns items whose username contains [query], ignoring case and surrounding whitespace. */
internal fun <T> filterByUsernameQuery(
    items: List<T>,
    query: String,
    username: (T) -> String,
): List<T> {
  val normalizedQuery = query.trim()
  return if (normalizedQuery.isEmpty()) {
    items
  } else {
    items.filter { username(it).contains(normalizedQuery, ignoreCase = true) }
  }
}
