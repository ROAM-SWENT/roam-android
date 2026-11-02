package com.github.roamswent.roam.ui.monumentDetail

import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.guide.Guide

/**
 * The guide text shown by [MonumentDetailScreen]. It holds only what the screen displays, so both a
 * freshly generated [MonumentGuide] and a saved [Guide] can fill it without inventing the rest.
 */
data class MonumentDetailContent(
    val monumentName: String,
    val confidence: Double,
    val history: String,
    val facts: List<String>,
    val culture: String,
)

fun MonumentGuide.toDetailContent() =
    MonumentDetailContent(
        monumentName = monumentName,
        confidence = confidence,
        history = history,
        facts = facts,
        culture = culture,
    )

fun Guide.toDetailContent() =
    MonumentDetailContent(
        monumentName = monumentName,
        confidence = confidence,
        history = history,
        facts = facts,
        culture = culture,
    )
