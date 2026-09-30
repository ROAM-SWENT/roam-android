package com.github.se.bootcamp.ui.overview

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.model.todo.displayString
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.ui.navigation.Screen

object EditToDoScreenTestTags {
  const val INPUT_TODO_TITLE = "inputTodoTitle"
  const val INPUT_TODO_DESCRIPTION = "inputTodoDescription"
  const val INPUT_TODO_ASSIGNEE = "inputTodoAssignee"
  const val INPUT_TODO_LOCATION = "inputTodoLocation"
  const val INPUT_TODO_DATE = "inputTodoDate"
  const val INPUT_TODO_STATUS = "inputTodoStatus"
  const val TODO_SAVE = "todoSave"
  const val TODO_DELETE = "todoDelete"
  const val ERROR_MESSAGE = "errorMessage"
  const val LOCATION_SUGGESTION = "locationSuggestion"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditToDoScreen(
    todoUid: String,
    editTodoViewModel: EditTodoViewModel = viewModel(),
    onDone: () -> Unit = {},
    onGoBack: () -> Unit = {},
) {
  LaunchedEffect(todoUid) { editTodoViewModel.loadTodo(todoUid) }

  val todoUIState by editTodoViewModel.uiState.collectAsState()
  val errorMsg = todoUIState.errorMsg

  // State for dropdown visibility
  var showDropdown by remember { mutableStateOf(false) }

  val locationSuggestions = todoUIState.locationSuggestions
  val locationQuery = todoUIState.locationQuery

  val context = LocalContext.current

  LaunchedEffect(errorMsg) {
    if (errorMsg != null) {
      Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
      editTodoViewModel.clearErrorMsg()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(
                  Screen.EditToDo(todoUid).name,
                  modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
            },
            navigationIcon = {
              IconButton(
                  modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
                  onClick = { onGoBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back")
                  }
            })
      },
      content = { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              // Title Input
              OutlinedTextField(
                  value = todoUIState.title,
                  onValueChange = { editTodoViewModel.setTitle(it) },
                  label = { Text("Title") },
                  placeholder = { Text("Task Title") },
                  isError = todoUIState.invalidTitleMsg != null,
                  supportingText = {
                    todoUIState.invalidTitleMsg?.let {
                      Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                    }
                  },
                  modifier =
                      Modifier.fillMaxWidth().testTag(EditToDoScreenTestTags.INPUT_TODO_TITLE))

              // Description Input
              OutlinedTextField(
                  value = todoUIState.description,
                  onValueChange = { editTodoViewModel.setDescription(it) },
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
                          .height(100.dp)
                          .testTag(EditToDoScreenTestTags.INPUT_TODO_DESCRIPTION))

              // Assignee Input
              OutlinedTextField(
                  value = todoUIState.assigneeName,
                  onValueChange = { editTodoViewModel.setAssigneeName(it) },
                  label = { Text("Assignee") },
                  placeholder = { Text("Assign a person") },
                  isError = todoUIState.invalidAssigneeNameMsg != null,
                  supportingText = {
                    todoUIState.invalidAssigneeNameMsg?.let {
                      Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                    }
                  },
                  modifier =
                      Modifier.fillMaxWidth().testTag(EditToDoScreenTestTags.INPUT_TODO_ASSIGNEE))

              // Location Input with dropdown
              Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = locationQuery,
                    onValueChange = {
                      editTodoViewModel.setLocationQuery(it)
                      showDropdown = true // Show dropdown when user starts typing
                    },
                    label = { Text("Location") },
                    placeholder = { Text("Enter an Address or Location") },
                    modifier =
                        Modifier.fillMaxWidth().testTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION),
                )

                // Dropdown to show location suggestions
                DropdownMenu(
                    expanded = showDropdown && locationSuggestions.isNotEmpty(),
                    onDismissRequest = { showDropdown = false },
                    properties = PopupProperties(focusable = false),
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(
                                max = 200.dp) // Set max height to make it scrollable if more than 3
                    // items
                    ) {
                      locationSuggestions.filterNotNull().take(3).forEach { location ->
                        DropdownMenuItem(
                            text = {
                              Text(
                                  text =
                                      location.name.take(30) +
                                          if (location.name.length > 30) "..."
                                          else "", // Limit name length and add ellipsis
                                  maxLines = 1, // Ensure name doesn't overflow
                              )
                            },
                            onClick = {
                              editTodoViewModel.setLocationQuery(location.name)
                              editTodoViewModel.setLocation(
                                  location) // Store the selected location object
                              showDropdown = false // Close dropdown on selection
                            },
                            modifier =
                                Modifier.padding(8.dp)
                                    .testTag(
                                        EditToDoScreenTestTags
                                            .LOCATION_SUGGESTION) // Add padding for better
                            // separation
                            )
                        Divider() // Separate items with a divider
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
                  onValueChange = { editTodoViewModel.setDueDate(it) },
                  label = { Text("Due date") },
                  placeholder = { Text("01/01/1970") },
                  isError = todoUIState.invalidDueDateMsg != null,
                  supportingText = {
                    todoUIState.invalidDueDateMsg?.let {
                      Text(it, modifier = Modifier.testTag(AddToDoScreenTestTags.ERROR_MESSAGE))
                    }
                  },
                  modifier =
                      Modifier.fillMaxWidth().testTag(EditToDoScreenTestTags.INPUT_TODO_DATE))
              Button(
                  onClick = {
                    // Update status to the next enum value
                    editTodoViewModel.setStatus(getNextStatus(todoUIState.status))
                  },
                  modifier =
                      Modifier.fillMaxWidth()
                          .padding(vertical = 8.dp)
                          .testTag(EditToDoScreenTestTags.INPUT_TODO_STATUS)) {
                    Text(text = todoUIState.status.displayString())
                  }

              Spacer(modifier = Modifier.height(16.dp))

              // Save Button
              Button(
                  onClick = {
                    if (editTodoViewModel.editTodo(todoUid)) {
                      onDone()
                    }
                  },
                  modifier = Modifier.fillMaxWidth().testTag(EditToDoScreenTestTags.TODO_SAVE),
                  enabled = todoUIState.isValid) {
                    Text("Save")
                  }

              Spacer(modifier = Modifier.height(8.dp))

              // Delete Button
              Button(
                  onClick = {
                    editTodoViewModel.deleteToDo(todoUid)
                    onDone()
                  },
                  modifier = Modifier.fillMaxWidth().testTag(EditToDoScreenTestTags.TODO_DELETE),
              ) {
                Text("Delete", color = Color.White)
              }
            }
      })
}

// Function to get the next status in the enum sequence
fun getNextStatus(currentStatus: ToDoStatus): ToDoStatus {
  return when (currentStatus) {
    ToDoStatus.CREATED -> ToDoStatus.STARTED
    ToDoStatus.STARTED -> ToDoStatus.ENDED
    ToDoStatus.ENDED -> ToDoStatus.ARCHIVED
    ToDoStatus.ARCHIVED -> ToDoStatus.CREATED
  }
}
