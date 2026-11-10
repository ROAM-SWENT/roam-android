// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.MonumentGuide

/**
 * Builds the [Guide] to save for this discovery, with its [photoBytes] and [discoveryDate] (Unix
 * epoch milliseconds). The id is left blank so the repository generates one, and the guide starts
 * as not favourite. The guide language is not stored.
 */
fun MonumentGuide.toSavedGuide(photoBytes: ByteArray, discoveryDate: Long): Guide =
    Guide(
        photoBytes = photoBytes,
        monumentName = monumentName,
        confidence = confidence,
        history = history,
        facts = facts,
        culture = culture,
        location = location,
        discoveryDate = discoveryDate,
    )
