package com.github.se.bootcamp.sigchecks

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import com.github.se.bootcamp.model.todo.displayString
import com.github.se.bootcamp.ui.overview.OverviewScreenTestTags
import com.google.firebase.firestore.ktx.firestore
import kotlin.properties.Delegates
import kotlinx.coroutines.runBlocking

// ************************************************************************* //
// ******                                                             ****** //
// ******  THIS FILE SHOULD NOT BE MODIFIED. IT SHOULD BE LOCATED IN  ****** //
// ******  `app/src/main/java/com/github/se/bootcamp/sigchecks`.      ****** //
// ******  DO **NOT** CHANGE ANY SIGNATURE IN THIS FILE               ****** //
// ******                                                             ****** //
// ************************************************************************* //

/**
 * SignatureChecks is a utility class designed for ensuring the consistency and correctness of the
 * app's architecture and data models. It's structured to validate the implementation of the main
 * components used within the Bootcamp's ToDo app. This class is intended for educational purposes,
 * providing a blueprint for students to understand and implement the required components and their
 * interactions within a Jetpack Compose single activity application. You can add more parameters to
 * the classes and methods as long as the following signature checks are correct (e.g. adding an
 * optional parameter).
 */
@SuppressLint("ComposableNaming")
@SuppressWarnings
class SignatureChecks {
  @Composable
  fun checkGreetingScreen() {
    com.github.se.bootcamp.ui.GreetingScreen()
  }

  fun checkGreetingScreenTestTags() {
    com.github.se.bootcamp.ui.GreetingScreenTestTags.BUTTON
    com.github.se.bootcamp.ui.GreetingScreenTestTags.NAME_INPUT
    com.github.se.bootcamp.ui.GreetingScreenTestTags.GREETING_MESSAGE
  }

  @Composable
  fun checkOverviewScreen() {
    com.github.se.bootcamp.ui.overview.OverviewScreen(
        overviewViewModel = overviewViewModel,
    )

    com.github.se.bootcamp.ui.overview.OverviewScreen(
        overviewViewModel,
    )

    com.github.se.bootcamp.ui.overview.OverviewScreen()
  }

  fun checkOverviewScreenTestTags() {
    OverviewScreenTestTags.CREATE_TODO_BUTTON
    OverviewScreenTestTags.LOGOUT_BUTTON
    OverviewScreenTestTags.EMPTY_TODO_LIST_MSG
    OverviewScreenTestTags.TODO_LIST

    val todo =
        com.github.se.bootcamp.model.todo.ToDo(
            uid = "1",
            name = "title",
            description = "description",
            assigneeName = "assignee",
            dueDate = com.google.firebase.Timestamp.now(),
            location = null,
            status = com.github.se.bootcamp.model.todo.ToDoStatus.CREATED,
            ownerId = "ownerId",
        )

    OverviewScreenTestTags.getTestTagForTodoItem(todo)
  }

  @Composable
  fun checkAddToDoScreen() {
    com.github.se.bootcamp.ui.overview.AddTodoScreen()
  }

  fun checkAddToDoScreenTestTags() {
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.INPUT_TODO_TITLE
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.INPUT_TODO_DATE
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.INPUT_TODO_LOCATION
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.TODO_SAVE
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.ERROR_MESSAGE
    com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags.LOCATION_SUGGESTION
  }

  @Composable
  fun checkEditToDoScreen() {
    com.github.se.bootcamp.ui.overview.EditToDoScreen(
        todoUid = todoID,
    )

    com.github.se.bootcamp.ui.overview.EditToDoScreen(
        todoID,
    )
  }

  fun checkEditToDoScreenTestTags() {
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.INPUT_TODO_TITLE
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.INPUT_TODO_DESCRIPTION
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.INPUT_TODO_ASSIGNEE
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.INPUT_TODO_DATE
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.INPUT_TODO_LOCATION
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.INPUT_TODO_STATUS
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.TODO_SAVE
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.TODO_DELETE
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.ERROR_MESSAGE
    com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags.LOCATION_SUGGESTION
  }

  @Composable
  fun checkMapToDoScreen() {
    com.github.se.bootcamp.ui.map.MapScreen()
  }

  fun checkNavigationTestTags() {
    com.github.se.bootcamp.ui.navigation.NavigationTestTags.TOP_BAR_TITLE
    com.github.se.bootcamp.ui.navigation.NavigationTestTags.BOTTOM_NAVIGATION_MENU
    com.github.se.bootcamp.ui.navigation.NavigationTestTags.OVERVIEW_TAB
    com.github.se.bootcamp.ui.navigation.NavigationTestTags.MAP_TAB
    com.github.se.bootcamp.ui.navigation.NavigationTestTags.GO_BACK_BUTTON
  }

  @Composable
  fun checkSignInScreen() {
    com.github.se.bootcamp.ui.authentication.SignInScreen()
  }

  fun checkSignInScreenTestTags() {
    com.github.se.bootcamp.ui.authentication.SignInScreenTestTags.APP_LOGO
    com.github.se.bootcamp.ui.authentication.SignInScreenTestTags.LOGIN_TITLE
    com.github.se.bootcamp.ui.authentication.SignInScreenTestTags.LOGIN_BUTTON
  }

  fun checkToDoDataModel() {
    val location: com.github.se.bootcamp.model.map.Location =
        com.github.se.bootcamp.model.map.Location(1.0, 1.0, "locationName")

    com.github.se.bootcamp.model.map.Location(
        latitude = 1.0, longitude = 1.0, name = "locationName")

    val todo =
        com.github.se.bootcamp.model.todo.ToDo(
            "1",
            "title",
            "description",
            "assignee",
            com.google.firebase.Timestamp.now(),
            location,
            com.github.se.bootcamp.model.todo.ToDoStatus.CREATED,
            "ownerId",
        )

    com.github.se.bootcamp.model.todo.ToDo(
        uid = "1",
        name = "title",
        description = "description",
        assigneeName = "assignee",
        dueDate = com.google.firebase.Timestamp.now(),
        location = location,
        status = com.github.se.bootcamp.model.todo.ToDoStatus.CREATED,
        ownerId = "ownerId",
    )

    todo.uid
    todo.name
    todo.description
    todo.assigneeName
    todo.dueDate
    todo.status
    todo.ownerId

    com.github.se.bootcamp.model.todo.ToDoStatus.CREATED
    com.github.se.bootcamp.model.todo.ToDoStatus.STARTED
    com.github.se.bootcamp.model.todo.ToDoStatus.ENDED
    com.github.se.bootcamp.model.todo.ToDoStatus.ARCHIVED

    com.github.se.bootcamp.model.todo.ToDoStatus.CREATED.displayString()
  }

  fun checkToDosRepository() {
    val repository: com.github.se.bootcamp.model.todo.ToDosRepository =
        object : com.github.se.bootcamp.model.todo.ToDosRepository {
          override suspend fun addTodo(toDo: com.github.se.bootcamp.model.todo.ToDo) {}

          override suspend fun editTodo(
              todoID: String,
              newValue: com.github.se.bootcamp.model.todo.ToDo
          ) {}

          override suspend fun deleteTodo(todoID: String) {}

          override fun getNewUid(): String {
            return "newId"
          }

          override suspend fun getAllTodos(): List<com.github.se.bootcamp.model.todo.ToDo> {
            return listOf()
          }

          override suspend fun getTodo(todoID: String): com.github.se.bootcamp.model.todo.ToDo {
            return com.github.se.bootcamp.model.todo.ToDo(
                "1",
                "title",
                "description",
                "assignee",
                com.google.firebase.Timestamp.now(),
                null,
                com.github.se.bootcamp.model.todo.ToDoStatus.CREATED,
                "ownerId",
            )
          }
        }

    runBlocking {
      val todo: com.github.se.bootcamp.model.todo.ToDo = repository.getTodo("1")
      repository.getAllTodos()
      repository.addTodo(todo)
      repository.addTodo(toDo = todo)
      repository.editTodo("1", todo)
      repository.editTodo(todoID = "1", newValue = todo)
      repository.deleteTodo("1")
      repository.deleteTodo(todoID = "1")
      repository.getNewUid()
    }
    com.github.se.bootcamp.model.todo.ToDosRepositoryLocal()
    com.github.se.bootcamp.model.todo.ToDosRepositoryFirestore(
        com.google.firebase.ktx.Firebase.firestore)
    com.github.se.bootcamp.model.todo.ToDosRepositoryFirestore(
        db = com.google.firebase.ktx.Firebase.firestore)
    com.github.se.bootcamp.model.todo.TODOS_COLLECTION_PATH
  }

  fun checkProviders() {
    val repository: com.github.se.bootcamp.model.todo.ToDosRepository =
        com.github.se.bootcamp.model.todo.ToDosRepositoryProvider.repository
    com.github.se.bootcamp.model.todo.ToDosRepositoryProvider.repository = repository

    val httpClient = com.github.se.bootcamp.HttpClientProvider.client
    com.github.se.bootcamp.HttpClientProvider.client = httpClient
  }

  @Composable
  fun checkTheme() {
    com.github.se.bootcamp.ui.theme.BootcampTheme {
      // No content needed
    }
  }

  @Composable
  fun checkBootcampApp() {
    com.github.se.bootcamp.BootcampApp(context, credentialManager)

    com.github.se.bootcamp.BootcampApp(context = context, credentialManager = credentialManager)

    com.github.se.bootcamp.MainActivity()
  }

  /* ---------------------------------------------------
  -----------  UI RELATED CLASSED/OBJECTS  ----------
  --------------------------------------------------- */

  // ViewModel for the overview screen
  private val overviewViewModel by
      Delegates.notNull<com.github.se.bootcamp.ui.overview.OverviewViewModel>()

  private val todoID by Delegates.notNull<String>()

  private val context by Delegates.notNull<android.content.Context>()
  private val credentialManager by Delegates.notNull<androidx.credentials.CredentialManager>()
}
