// Co-authored-by: Codex <codex@openai.com>
package com.github.roamswent.roam.ui.monumentDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.ai.GuideMilestone
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.guideCalloutContainer
import com.github.roamswent.roam.ui.theme.guideEyebrow
import com.github.roamswent.roam.ui.theme.guideHeading
import com.github.roamswent.roam.ui.theme.guideMatchAccent
import com.github.roamswent.roam.ui.theme.guideText
import kotlin.math.roundToInt

@Composable
internal fun GuideHeader(guide: MonumentGuide) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = stringResource(R.string.monument_detail_ai_guide_label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.guideEyebrow,
    )
    Text(
        text = guide.monumentName,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.guideHeading,
        modifier = Modifier.testTag(C.Tag.monument_detail_title).semantics { heading() },
    )
    guide.overview
        ?.takeIf { it.isNotBlank() }
        ?.let { GuideBody(it, Modifier.testTag(C.Tag.monument_detail_overview)) }
    GuideMatchBadge((guide.confidence * 100).roundToInt())
  }
}

@Composable
private fun GuideMatchBadge(percent: Int) {
  val color = MaterialTheme.guideMatchAccent(percent)
  Text(
      text = stringResource(R.string.monument_detail_match_percent, percent),
      style = MaterialTheme.typography.labelLarge,
      color = color,
      modifier =
          Modifier.testTag(C.Tag.monument_detail_confidence)
              .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

@Composable
internal fun GuideContent(guide: MonumentGuide) {
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    if (guide.history.isNotBlank()) {
      GuideParagraph(
          title = stringResource(R.string.monument_detail_history_title),
          content = guide.history,
          contentTag = C.Tag.monument_detail_history,
      )
    }
    val facts = guide.facts.map { it.replace(factBullet, "") }.filter(String::isNotBlank)
    if (facts.isNotEmpty()) {
      GuideCallout(
          title =
              stringResource(
                  if (facts.size == 1) R.string.monument_detail_facts_title
                  else R.string.monument_detail_facts_plural_title
              ),
          modifier = Modifier.testTag(C.Tag.monument_detail_facts),
      ) {
        facts.forEach { fact ->
          GuideBody(
              text = fact,
              modifier = Modifier.testTag(C.Tag.monument_detail_fact_item),
          )
        }
      }
    }
    if (guide.culture.isNotBlank()) {
      GuideParagraph(
          title = stringResource(R.string.monument_detail_culture_title),
          content = guide.culture,
          contentTag = C.Tag.monument_detail_culture,
      )
    }
    guide.sections.forEach { section ->
      if (section.title.isNotBlank() && section.content.isNotBlank()) {
        GuideCallout(section.title, Modifier.testTag(C.Tag.monument_detail_interest)) {
          GuideBody(section.content)
        }
      }
    }
    if (guide.timeline.isNotEmpty()) {
      GuideTimeline(guide.timeline)
    }
  }
}

@Composable
private fun GuideParagraph(title: String, content: String, contentTag: String) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    GuideHeading(title)
    GuideBody(content, Modifier.testTag(contentTag))
  }
}

@Composable
private fun GuideHeading(title: String, modifier: Modifier = Modifier) {
  Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.guideHeading,
      modifier = modifier.semantics { heading() },
  )
}

@Composable
private fun GuideCallout(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .background(MaterialTheme.guideCalloutContainer, RoundedCornerShape(12.dp))
              .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.guideHeading,
        modifier = Modifier.semantics { heading() },
    )
    content()
  }
}

@Composable
private fun GuideTimeline(milestones: List<GuideMilestone>) {
  val ordered = remember(milestones) { milestones.sortedBy { it.year } }
  val markerColor = MaterialTheme.guideEyebrow
  Column(modifier = Modifier.testTag(C.Tag.monument_detail_timeline)) {
    GuideHeading(
        title = stringResource(R.string.monument_detail_timeline_title),
        modifier = Modifier.padding(bottom = 8.dp),
    )
    ordered.forEachIndexed { index, milestone ->
      val first = index == 0
      val last = index == ordered.lastIndex
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .testTag(C.Tag.monument_detail_timeline_item)
                  .drawBehind {
                    val inset = 5.dp.toPx()
                    val x =
                        if (layoutDirection == LayoutDirection.Ltr) inset else size.width - inset
                    val y = 10.dp.toPx()
                    if (!first || !last) {
                      drawLine(
                          color = markerColor,
                          start = Offset(x, if (first) y else 0f),
                          end = Offset(x, if (last) y else size.height),
                          strokeWidth = 1.dp.toPx(),
                      )
                    }
                    drawCircle(markerColor, radius = 4.dp.toPx(), center = Offset(x, y))
                  }
                  .padding(start = 22.dp, bottom = if (last) 0.dp else 10.dp),
      ) {
        Text(
            text =
                if (milestone.year < 0) {
                  stringResource(R.string.monument_detail_year_bce, -milestone.year)
                } else {
                  milestone.year.toString()
                },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.guideHeading,
        )
        GuideBody(milestone.event)
      }
    }
  }
}

@Composable
private fun GuideBody(text: String, modifier: Modifier = Modifier) {
  Text(
      text = remember(text) { markdownText(text) },
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.guideText,
      modifier = modifier,
  )
}

private val factBullet = Regex("""(?m)^[ \t]*[-*•][ \t]+""")
private val boldPattern = Regex("\\*\\*(.+?)\\*\\*")

private fun markdownText(markdown: String): AnnotatedString = buildAnnotatedString {
  markdown.lines().forEachIndexed { index, line ->
    if (index > 0) append("\n")
    val trimmed = line.trim()
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      append("• ")
      appendMarkdownInline(trimmed.drop(2))
    } else {
      appendMarkdownInline(trimmed)
    }
  }
}

private fun AnnotatedString.Builder.appendMarkdownInline(text: String) {
  var cursor = 0
  boldPattern.findAll(text).forEach { match ->
    append(text.substring(cursor, match.range.first))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
    cursor = match.range.last + 1
  }
  append(text.substring(cursor))
}
