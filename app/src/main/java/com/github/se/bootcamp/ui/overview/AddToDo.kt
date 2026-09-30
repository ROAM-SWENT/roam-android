package com.github.se.bootcamp.ui.overview

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.ui.navigation.Screen

object AddToDoScreenTestTags {
  const val INPUT_TODO_TITLE = "inputTodoTitle"
  const val INPUT_TODO_DESCRIPTION = "inputTodoDescription"
  const val INPUT_TODO_ASSIGNEE = "inputTodoAssignee"
  const val INPUT_TODO_LOCATION = "inputTodoLocation"
  const val INPUT_TODO_DATE = "inputTodoDate"
  const val TODO_SAVE = "todoSave"
  const val ERROR_MESSAGE = "errorMessage"
  const val LOCATION_SUGGESTION = "locationSuggestion"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoScreen(
    addTodoViewModel: AddTodoViewModel = viewModel(),
    onGoBack: () -> Unit = {},
    onDone: () -> Unit = {},
) {
  val todoUIState by addTodoViewModel.uiState.collectAsState()
  val errorMsg = todoUIState.errorMsg

  var showDropdown by remember { mutableStateOf(false) }

  val locationSuggestions = todoUIState.locationSuggestions
  val locationQuery = todoUIState.locationQuery

  val context = LocalContext.current

  LaunchedEffect(errorMsg) {
    if (errorMsg != null) {
      Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
      addTodoViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(Screen.AddToDo.name, Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
            },
            navigationIcon = {
              IconButton(
                  onClick = { onGoBack() }, Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back")
                  }
            })
      },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              // Title Input
              OutlinedTextField(
                  value = todoUIState.title,
                  onValueChange = { addTodoViewModel.setTitle(it) },
                  label = { Text("Title") },
                  placeholder = { Text("Name the task") },
                  isError = todoUIState.invalidTitleMsg != null,
                  supportingText =
                      todoUIState.invalidTitleMsg?.let {
                        {
                          Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                        }
                      },
                  modifier =
                      Modifier.fillMaxWidth().testTag(AddToDoScreenTestTags.INPUT_TODO_TITLE))

              // Description Input
              OutlinedTextField(
                  value = todoUIState.description,
                  onValueChange = { addTodoViewModel.setDescription(it) },
                  label = { Text("Description") },
                  placeholder = { Text("Describe the task") },
                  isError = todoUIState.invalidDescriptionMsg != null,
                  supportingText = {
                    todoUIState.invalidDescriptionMsg?.let {
                      Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                    }
                  },
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(200.dp)
                          .testTag(AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION))

              // Assignee Input
              OutlinedTextField(
                  value = todoUIState.assigneeName,
                  onValueChange = { addTodoViewModel.setAssigneeName(it) },
                  label = { Text("Assignee") },
                  placeholder = { Text("Assign a person") },
                  isError = todoUIState.invalidAssigneeNameMsg != null,
                  supportingText = {
                    todoUIState.invalidAssigneeNameMsg?.let {
                      Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                    }
                  },
                  modifier =
                      Modifier.fillMaxWidth().testTag(AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE))

              // Location Input with dropdown using ExposedDropdownMenuBox
              ExposedDropdownMenuBox(
                  expanded = showDropdown && locationSuggestions.isNotEmpty(),
                  onExpandedChange = { showDropdown = it } // Toggle dropdown visibility
                  ) {
                    OutlinedTextField(
                        value = locationQuery,
                        onValueChange = {
                          addTodoViewModel.setLocationQuery(it)
                          showDropdown = true // Show dropdown when user starts typing
                        },
                        label = { Text("Location") },
                        placeholder = { Text("Enter an Address or Location") },
                        modifier =
                            Modifier.menuAnchor() // Anchor the dropdown to this text field
                                .fillMaxWidth()
                                .testTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION),
                        singleLine = true)

                    // Dropdown menu for location suggestions
                    // Another approach using DropdownMenu is in EditToDo.kt
                    ExposedDropdownMenu(
                        expanded = showDropdown && locationSuggestions.isNotEmpty(),
                        onDismissRequest = { showDropdown = false }) {
                          locationSuggestions.filterNotNull().take(3).forEach { location ->
                            DropdownMenuItem(
                                text = {
                                  Text(
                                      text =
                                          location.name.take(30) +
                                              if (location.name.length > 30) "..."
                                              else "", // Limit name length
                                      maxLines = 1 // Ensure name doesn't overflow
                                      )
                                },
                                onClick = {
                                  addTodoViewModel.setLocationQuery(location.name)
                                  addTodoViewModel.setLocation(location)
                                  showDropdown = false // Close dropdown on selection
                                },
                                modifier =
                                    Modifier.padding(8.dp)
                                        .testTag(AddToDoScreenTestTags.LOCATION_SUGGESTION))
                          }

                          if (locationSuggestions.size > 3) {
                            DropdownMenuItem(
                                text = { Text("More...") },
                                onClick = { /* Optionally show more results */},
                                modifier = Modifier.padding(8.dp))
                          }
                        }
                  }

              // Due Date Input
              OutlinedTextField(
                  value = todoUIState.dueDate,
                  onValueChange = { addTodoViewModel.setDueDate(it) },
                  label = { Text("Due date") },
                  placeholder = { Text("01/01/1970") },
                  isError = todoUIState.invalidDueDateMsg != null,
                  supportingText = {
                    todoUIState.invalidDueDateMsg?.let {
                      Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag(AddToDoScreenTestTags.INPUT_TODO_DATE))

              Spacer(modifier = Modifier.height(16.dp))

              // Save Button
              Button(
                  onClick = {
                    if (addTodoViewModel.addTodo()) {
                      onDone()
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag(AddToDoScreenTestTags.TODO_SAVE),
                  enabled = todoUIState.isValid) {
                    Text("Save")
                  }
            }
      })
}
