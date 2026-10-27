/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.HomeTextPrimary
import com.github.roamswent.roam.ui.theme.HomeTextSecondary

@Composable
fun HomeGreetingHeader(
    userName: String = "Victor",
    modifier: Modifier = Modifier,
) {
  val displayName = userName.ifBlank { "Victor" }

  Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp)) {
    Text(
        text = stringResource(R.string.home_greeting_format, displayName),
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = HomeTextSecondary,
        modifier = Modifier.semantics { testTag = C.Tag.home_greeting_subtitle },
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.home_greeting_title),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = HomeTextPrimary,
        modifier = Modifier.semantics { testTag = C.Tag.home_greeting_title },
    )
  }
}
