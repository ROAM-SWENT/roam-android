/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import org.apache.commons.text.similarity.LevenshteinDistance

/**
 * Returns items whose username contains [query], ignoring case and surrounding whitespace.
 *
 * If substring matching finds no results, usernames within a small Levenshtein distance are
 * returned as a typo-tolerant fallback. Digits are intentionally allowed because they are common in
 * usernames.
 */
internal fun <T> filterByUsernameQuery(
    items: List<T>,
    query: String,
    username: (T) -> String,
): List<T> {
  val normalizedQuery = query.trim()
  if (normalizedQuery.isEmpty()) return items

  val substringMatches = items.filter { username(it).contains(normalizedQuery, ignoreCase = true) }
  if (substringMatches.isNotEmpty()) return substringMatches

  val normalizedQueryLowercase = normalizedQuery.lowercase()
  val maxDistance = if (normalizedQueryLowercase.length <= 4) 1 else 2
  val distance = LevenshteinDistance.getDefaultInstance()
  return items.filter { item ->
    val normalizedUsername = username(item).trim().lowercase()
    normalizedUsername.length >= 3 &&
        distance.apply(normalizedQueryLowercase, normalizedUsername) in 0..maxDistance
  }
}
