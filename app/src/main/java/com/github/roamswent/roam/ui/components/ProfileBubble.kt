/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R

@Composable
internal fun ProfileBubble(
    name: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarBytes: ByteArray? = null,
    size: Dp = 48.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
  val description = stringResource(R.string.home_profile_button_description)
  ProfilePicture(
      name = name,
      avatarBytes = avatarBytes,
      onClick = onClick,
      modifier =
          modifier.size(size).semantics {
            contentDescription = description
            this.testTag = testTag
          },
      textStyle = textStyle,
  )
}
