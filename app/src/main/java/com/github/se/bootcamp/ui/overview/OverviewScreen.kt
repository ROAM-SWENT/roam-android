package com.github.se.bootcamp.ui.overview

import android.icu.text.SimpleDateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.ui.navigation.BottomNavigationMenu
import com.github.se.bootcamp.ui.navigation.NavigationActions
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.ui.navigation.Tab
import java.util.Locale

object OverviewScreenTestTags {
  const val CREATE_TODO_BUTTON = "createTodoFab"
  const val LOGOUT_BUTTON = "logoutButton"
  const val EMPTY_TODO_LIST_MSG = "emptyTodoList"
  const val TODO_LIST = "todoList"

  fun getTestTagForTodoItem(todo: ToDo): String = "todoItem${todo.uid}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    overviewViewModel: OverviewViewModel = viewModel(),
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedOut: () -> Unit = {},
    onSelectTodo: (ToDo) -> Unit = {},
    onAddTodo: () -> Unit = {},
    navigationActions: NavigationActions? = null,
) {

  val context = LocalContext.current
  val uiState by overviewViewModel.uiState.collectAsState()
  val todos = uiState.todos

  // Fetch todos when the screen is recomposed
  LaunchedEffect(Unit) { overviewViewModel.refreshUIState() }

  // Show error message if fetching todos fails
  LaunchedEffect(uiState.errorMsg) {
    uiState.errorMsg?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      overviewViewModel.clearErrorMsg()
    }
  }

  LaunchedEffect(uiState.signedOut) {
    if (uiState.signedOut) {
      onSignedOut()
      Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text("Overview", modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
            },
            actions = {
              // Logout Icon Button
              IconButton(
                  onClick = { overviewViewModel.signOut(credentialManager) },
                  modifier = Modifier.testTag(OverviewScreenTestTags.LOGOUT_BUTTON)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log out")
                  }
            })
      },
      floatingActionButton = {
        FloatingActionButton(
            onClick = { onAddTodo() },
            modifier = Modifier.testTag(OverviewScreenTestTags.CREATE_TODO_BUTTON)) {
              Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
            }
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.Overview,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU))
      },
      content = { pd ->
        if (todos.isNotEmpty()) {
          LazyColumn(
              contentPadding = PaddingValues(vertical = 8.dp),
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(horizontal = 16.dp)
                      .padding(pd)
                      .testTag(OverviewScreenTestTags.TODO_LIST)) {
                items(todos.size) { index ->
                  ToDoItem(todo = todos[index], onClick = { onSelectTodo(todos[index]) })
                }
              }
        } else {
          Text(
              modifier = Modifier.padding(pd).testTag(OverviewScreenTestTags.EMPTY_TODO_LIST_MSG),
              text = "You have no ToDo yet.")
        }
      })
}

@Composable
fun ToDoItem(todo: ToDo, onClick: () -> Unit) {
  Card(
      modifier =
          Modifier.testTag(OverviewScreenTestTags.getTestTagForTodoItem(todo))
              .fillMaxWidth()
              .padding(vertical = 4.dp)
              .clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
      // Date and Status Row
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text =
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(todo.dueDate.toDate()),
            style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
              text =
                  when (todo.status) {
                    ToDoStatus.CREATED -> "Created"
                    ToDoStatus.STARTED -> "Started"
                    ToDoStatus.ENDED -> "Ended"
                    ToDoStatus.ARCHIVED -> "Archived"
                  },
              style = MaterialTheme.typography.bodySmall,
              color =
                  when (todo.status) {
                    ToDoStatus.CREATED -> Color.Blue
                    ToDoStatus.STARTED -> Color(0xFFFFA500) // Orange
                    ToDoStatus.ENDED -> Color.Green
                    ToDoStatus.ARCHIVED -> Color.Gray
                  })
          Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Task Name
      Text(
          text = todo.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold)

      // Assignee Name
      Text(text = todo.assigneeName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
  }
}
//
// class TodoRepositoryImpl : ToDosRepository {
//  override fun getNewUid(): String {
//    TODO("Not yet implemented")
//  }
//
//  override suspend fun getAllTodos(): List<ToDo> {
//    return listOf(
//        ToDo(
//            uid = "1",
//            name = "Sample Task",
//            description = "This is a sample task description.",
//            assigneeName = "John Doe",
//            status = ToDoStatus.CREATED,
//            dueDate = com.google.firebase.Timestamp.now(),
//            location = null,
//            ownerId = "0"))
//  }
//
//  override suspend fun getTodo(todoID: String): ToDo {
//    TODO("Not yet implemented")
//  }
//
//  override suspend fun addTodo(toDo: ToDo) {
//    TODO("Not yet implemented")
//  }
//
//  override suspend fun editTodo(todoID: String, newValue: ToDo) {
//    TODO("Not yet implemented")
//  }
//
//  override suspend fun deleteTodo(todoID: String) {
//    TODO("Not yet implemented")
//  }
// }

// @Preview
// @Composable
// fun OverviewScreenPreview() {
//  val repository = TodoRepositoryImpl()
//  val viewModel = OverviewViewModel(repository)
//  BootcampTheme { Surface(modifier = Modifier.fillMaxSize()) { OverviewScreen(viewModel) } }
//  OverviewScreen(overviewViewModel = viewModel)
// }
