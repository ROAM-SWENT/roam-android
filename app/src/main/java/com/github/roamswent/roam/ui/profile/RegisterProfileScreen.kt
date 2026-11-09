/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.github.roamswent.roam.R
import com.github.roamswent.roam.resources.C

@Composable
fun RegisterProfileScreen(viewModel: RegisterProfileViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  var username by remember { mutableStateOf("") }

  Scaffold(
      modifier =
          Modifier.fillMaxSize().semantics { testTag = C.Tag.register_profile_screen_container },
      containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(padding).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(
          text = stringResource(R.string.register_profile_title),
          style = MaterialTheme.typography.headlineSmall,
      )
      Spacer(Modifier.height(24.dp))
      OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          modifier =
              Modifier.fillMaxWidth().semantics { testTag = C.Tag.register_profile_username },
          label = { Text(stringResource(R.string.register_profile_username_label)) },
          singleLine = true,
          enabled = !uiState.isLoading,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
      )
      uiState.errorMessage?.let { errorMessage ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMessage.ifBlank { stringResource(R.string.register_profile_error) },
            color = MaterialTheme.colorScheme.error,
        )
      }
      Spacer(Modifier.height(16.dp))
      Button(
          onClick = { viewModel.registerUsername(username) },
          modifier = Modifier.fillMaxWidth().semantics { testTag = C.Tag.register_profile_submit },
          enabled = !uiState.isLoading && username.isNotBlank(),
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator()
        } else {
          Text(stringResource(R.string.register_profile_submit))
        }
      }
    }
  }
}
