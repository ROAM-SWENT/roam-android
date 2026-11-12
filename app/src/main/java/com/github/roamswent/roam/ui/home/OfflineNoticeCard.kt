// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.offlineNoticeContainer
import com.github.roamswent.roam.ui.theme.offlineNoticeContent

@Composable
fun OfflineNoticeCard(modifier: Modifier = Modifier) {
  val backgroundColor = MaterialTheme.offlineNoticeContainer
  val contentColor = MaterialTheme.offlineNoticeContent
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = 76.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(backgroundColor)
              .padding(horizontal = 20.dp, vertical = 16.dp)
              .semantics { testTag = C.Tag.home_offline_card },
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.Filled.WifiOff,
        contentDescription = stringResource(R.string.home_offline_card_icon_description),
        tint = contentColor,
        modifier = Modifier.size(32.dp).semantics { testTag = C.Tag.home_offline_card_icon },
    )
    Text(
        text = stringResource(R.string.home_offline_card_message),
        color = contentColor,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { testTag = C.Tag.home_offline_card_message },
    )
  }
}
