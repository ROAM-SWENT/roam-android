/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
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
import com.github.roamswent.roam.ui.theme.LoadingTextPrimary

@Composable
fun FriendsScreen(modifier: Modifier = Modifier, isDarkTheme: Boolean) {
  Column(
      modifier =
          modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).semantics {
            testTag = C.Tag.friends_screen_container
          }
  ) {
    Text(
        text = stringResource(R.string.friends_title),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = if (isDarkTheme) MaterialTheme.colorScheme.onBackground else LoadingTextPrimary,
        modifier = Modifier.padding(vertical = 16.dp).semantics { testTag = C.Tag.friends_title },
    )
  }
}
