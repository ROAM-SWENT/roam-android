// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.guide

import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.location.Coordinates

/**
 * Builds the [Guide] to save for this discovery under [id], with its [photoBytes] and
 * [discoveryDate] (Unix epoch milliseconds). The guide starts as not favourite and the guide
 * language is not stored.
 *
 * The scan location is deliberately not copied: a saved guide is on Explore only when its location
 * is set, so the caller passes [location] explicitly, `null` to keep the guide off Explore.
 */
fun MonumentGuide.toSavedGuide(
    id: String,
    photoBytes: ByteArray,
    discoveryDate: Long,
    location: Coordinates?,
): Guide =
    Guide(
        id = id,
        photoBytes = photoBytes,
        monumentName = monumentName,
        confidence = confidence,
        history = history,
        facts = facts,
        culture = culture,
        location = location,
        discoveryDate = discoveryDate,
    )

/**
 * Builds the [MonumentGuide] the detail screen shows for this saved guide. The guide language is
 * not stored, so it is left empty; the detail screen does not display it. The photo is passed to
 * the screen separately from [Guide.photoBytes].
 */
fun Guide.toMonumentGuide(): MonumentGuide =
    MonumentGuide(
        monumentName = monumentName,
        confidence = confidence,
        history = history,
        facts = facts,
        culture = culture,
        language = "",
        location = location,
    )
