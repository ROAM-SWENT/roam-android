/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.HomeAccentGreen

@Composable
fun ScanPromptCard(
    onPromptClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = 140.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(HomeAccentGreen)
              .clickable { onPromptClick() }
              .semantics { testTag = C.Tag.home_scan_card },
  ) {
    Column(modifier = Modifier.padding(20.dp).fillMaxWidth(0.82f)) {
      Text(
          text = stringResource(R.string.home_scan_card_title),
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          modifier = Modifier.semantics { testTag = C.Tag.home_scan_card_title },
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          text = stringResource(R.string.home_scan_card_body),
          fontSize = 13.sp,
          lineHeight = 18.sp,
          fontWeight = FontWeight.Normal,
          color = Color.White.copy(alpha = 0.85f),
          modifier = Modifier.semantics { testTag = C.Tag.home_scan_card_body },
      )
    }
    Icon(
        imageVector = Icons.Filled.AccountBalance,
        contentDescription = stringResource(R.string.home_scan_card_icon_description),
        tint = Color.White.copy(alpha = 0.12f),
        modifier =
            Modifier.align(Alignment.BottomEnd).size(84.dp).offset(x = 8.dp, y = 10.dp).semantics {
              testTag = C.Tag.home_scan_card_icon
            },
    )
  }
}
