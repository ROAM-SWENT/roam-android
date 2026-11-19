// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.basemap

import com.github.roamswent.roam.model.location.Coordinates

/** One land area of the offline base map: an outer ring and the lakes or enclaves cut out of it. */
data class BaseMapPolygon(
    val outline: List<Coordinates>,
    val holes: List<List<Coordinates>> = emptyList(),
)
