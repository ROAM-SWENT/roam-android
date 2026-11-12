/*
 * Co-authored-by: Mistral Vibe <vibe@mistral.ai>
 */

package com.github.roamswent.roam.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.HomeTextPrimary
import com.github.roamswent.roam.ui.theme.HomeTextSecondary
import com.github.roamswent.roam.ui.theme.isDark

@Composable
fun HomeGreetingHeader(
    userName: String = HomeViewModel.DEFAULT_USER_NAME,
    modifier: Modifier = Modifier,
) {
  val displayName = userName.trim()
  val secondaryTextColor =
      if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurfaceVariant else HomeTextSecondary

  Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp)) {
    Text(
        text =
            if (displayName.isEmpty()) stringResource(R.string.home_greeting_no_name)
            else stringResource(R.string.home_greeting_format, displayName),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        color = secondaryTextColor,
        modifier = Modifier.semantics { testTag = C.Tag.home_greeting_subtitle },
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.home_greeting_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = if (MaterialTheme.isDark) MaterialTheme.colorScheme.onSurface else HomeTextPrimary,
        modifier = Modifier.semantics { testTag = C.Tag.home_greeting_title },
    )
  }
}
