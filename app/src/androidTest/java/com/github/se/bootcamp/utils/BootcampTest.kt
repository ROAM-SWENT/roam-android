package com.github.se.bootcamp.utils

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.se.bootcamp.HttpClientProvider
import com.github.se.bootcamp.model.map.Location
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.model.todo.ToDosRepository
import com.github.se.bootcamp.model.todo.ToDosRepositoryProvider
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.ui.overview.AddToDoScreenTestTags
import com.github.se.bootcamp.ui.overview.EditToDoScreenTestTags
import com.github.se.bootcamp.ui.overview.OverviewScreenTestTags
import com.github.se.bootcamp.utils.FakeHttpClient.FakeLocation
import com.github.se.bootcamp.utils.FakeHttpClient.locationSuggestions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before

const val UI_WAIT_TIMEOUT = 5_000L

enum class BootcampMilestone {
  B1,
  B2,
  B3
}

/**
 * Base class for all Bootcamp tests, providing common setup and utility functions.
 *
 * It also handles gracefully automatic sign-in when required by the milestone.
 *
 * For the B1 tests, it is quite tricky. During the first week, emulators are not set up, so we
 * can't simply sign-in anonymously. However, during week 3, B1 tests won't pass if we do not
 * sign-in automatically. Hence, to detect that we are running B1 tests during the first week, we
 * check if the Firebase emulators are running. If they are not running *by mistake*, B2 and B3
 * tests will fail, notifying the user that they need to start the emulators.
 */
abstract class BootcampTest(val milestone: BootcampMilestone) {

  abstract fun createInitializedRepository(): ToDosRepository

  open fun initializeHTTPClient(): OkHttpClient = FakeHttpClient.getClient()

  val repository: ToDosRepository
    get() = ToDosRepositoryProvider.repository

  val httpClient
    get() = HttpClientProvider.client

  val shouldSignInAnounymously: Boolean =
      when (milestone) {
        BootcampMilestone.B3 -> false
        BootcampMilestone.B2 -> true
        BootcampMilestone.B1 -> FirebaseEmulator.isRunning
      }

  val currentUser: FirebaseUser
    get() {
      assert(milestone != BootcampMilestone.B1) {
        "currentUser should not be read in B1 tests when Firebase Auth is not used."
      }
      return FirebaseEmulator.auth.currentUser!!
    }

  init {
    when (milestone) {
      BootcampMilestone.B2,
      BootcampMilestone.B3 -> {
        assert(FirebaseEmulator.isRunning) {
          "FirebaseEmulator must be running for Milestone $milestone"
        }
      }
      else -> {}
    }
  }

  open val todo1 =
      ToDo(
          uid = "0",
          name = "Buy groceries",
          description = "Milk, eggs, bread, and butter",
          assigneeName = "Alice",
          dueDate = Timestamp.Companion.fromDate(2025, Calendar.SEPTEMBER, 1),
          location = Location(46.5191, 6.5668, "Lausanne Coop"),
          status = ToDoStatus.CREATED,
          ownerId = "user")

  open val todo2 =
      ToDo(
          uid = "1",
          name = "Walk the dog",
          description = "Take Fido for a walk in the park",
          assigneeName = "Bob",
          dueDate = Timestamp.Companion.fromDate(2025, Calendar.OCTOBER, 15),
          location = Location(46.5210, 6.5790, "Parc de Mon Repos"),
          status = ToDoStatus.STARTED,
          ownerId = "user")

  open val todo3 =
      ToDo(
          uid = "2",
          name = "Read a book",
          description = "Finish reading 'Clean Code'",
          assigneeName = "Charlie",
          dueDate = Timestamp.Companion.fromDate(2025, Calendar.NOVEMBER, 10),
          location = Location(46.5200, 6.5800, "City Library"),
          status = ToDoStatus.ARCHIVED,
          ownerId = "user")

  @Before
  open fun setUp() {
    ToDosRepositoryProvider.repository = createInitializedRepository()
    HttpClientProvider.client = initializeHTTPClient()
    if (shouldSignInAnounymously) {
      runTest { FirebaseEmulator.auth.signInAnonymously().await() }
    }
  }

  @After
  open fun tearDown() {
    if (FirebaseEmulator.isRunning) {
      FirebaseEmulator.auth.signOut()
      FirebaseEmulator.clearAuthEmulator()
    }
  }

  fun ComposeTestRule.enterAddTodoTitle(title: String) =
      onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_TITLE).performTextInput(title)

  fun ComposeTestRule.enterAddTodoDescription(description: String) =
      onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION).performTextInput(description)

  fun ComposeTestRule.enterAddTodoAssignee(assignee: String) =
      onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE).performTextInput(assignee)

  fun ComposeTestRule.enterAddTodoDate(date: String) =
      onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).performTextInput(date)

  fun ComposeTestRule.enterAddTodoLocation(location: String) =
      onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION).performTextInput(location)

  fun ComposeTestRule.enterAddTodoLocation(location: FakeLocation) =
      onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION).performTextInput(location.queryName)

  fun ComposeTestRule.enterEditTodoLocation(location: FakeLocation) {
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION).performTextClearance()
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION).performTextInput(location.queryName)
  }

  fun ComposeTestRule.enterEditTodoTitle(title: String) {
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_TITLE).performTextClearance()
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_TITLE).performTextInput(title)
  }

  fun ComposeTestRule.enterEditTodoDescription(description: String) {
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DESCRIPTION).performTextClearance()
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DESCRIPTION).performTextInput(description)
  }

  fun ComposeTestRule.enterEditTodoAssignee(assignee: String) {
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_ASSIGNEE).performTextClearance()
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_ASSIGNEE).performTextInput(assignee)
  }

  fun ComposeTestRule.enterEditTodoDate(date: String) {
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DATE).performTextClearance()
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DATE).performTextInput(date)
  }

  fun ComposeTestRule.enterEditTodoLocation(location: String) {
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION).performTextClearance()
    onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION).performTextInput(location)
  }

  fun ComposeTestRule.enterEditTodoDetails(todo: ToDo, date: String = todo.dueDate.toDateString()) {
    enterEditTodoTitle(todo.name)
    enterEditTodoDescription(todo.description)
    enterEditTodoAssignee(todo.assigneeName)
    enterEditTodoDate(date)
    enterEditTodoLocation(todo.location?.name ?: "Any")
  }

  fun ComposeTestRule.enterAddTodoDetails(todo: ToDo, date: String = todo.dueDate.toDateString()) {
    enterAddTodoTitle(todo.name)
    enterAddTodoDescription(todo.description)
    enterAddTodoAssignee(todo.assigneeName)
    enterAddTodoDate(date)
    enterAddTodoLocation(todo.location?.name ?: "Any")
  }

  fun ComposeTestRule.clickOnSaveForAddTodo(waitForRedirection: Boolean = false) {
    onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE).assertIsDisplayed().performClick()
    waitUntil(UI_WAIT_TIMEOUT) {
      !waitForRedirection ||
          onAllNodesWithTag(AddToDoScreenTestTags.TODO_SAVE).fetchSemanticsNodes().isEmpty()
    }
  }

  fun ComposeTestRule.clickOnSaveForEditTodo(waitForRedirection: Boolean = false) {
    onNodeWithTag(EditToDoScreenTestTags.TODO_SAVE).assertIsDisplayed().performClick()
    waitUntil(UI_WAIT_TIMEOUT) {
      !waitForRedirection ||
          onAllNodesWithTag(EditToDoScreenTestTags.TODO_SAVE).fetchSemanticsNodes().isEmpty()
    }
  }

  fun ComposeTestRule.clickOnDeleteForEditTodo(waitForRedirection: Boolean = false) {
    onNodeWithTag(EditToDoScreenTestTags.TODO_DELETE).assertIsDisplayed().performClick()
    waitUntil(UI_WAIT_TIMEOUT) {
      !waitForRedirection ||
          onAllNodesWithTag(EditToDoScreenTestTags.TODO_DELETE).fetchSemanticsNodes().isEmpty()
    }
  }

  fun ComposeTestRule.navigateToAddToDoScreen() {
    onNodeWithTag(OverviewScreenTestTags.CREATE_TODO_BUTTON).assertIsDisplayed().performClick()
  }

  private fun ComposeTestRule.waitUntilTodoIsDisplayed(todo: ToDo): SemanticsNodeInteraction {
    checkOverviewScreenIsDisplayed()
    waitUntil(UI_WAIT_TIMEOUT) {
      onAllNodesWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todo))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    return checkTodoItemIsDisplayed(todo)
  }

  fun ComposeTestRule.clickOnTodoItem(todo: ToDo) {
    waitUntilTodoIsDisplayed(todo).performClick()
  }

  fun ComposeTestRule.checkTodoItemIsDisplayed(todo: ToDo): SemanticsNodeInteraction =
      onNodeWithTag(OverviewScreenTestTags.getTestTagForTodoItem(todo)).assertIsDisplayed()

  fun ComposeTestRule.navigateToEditToDoScreen(editedToDo: ToDo) {
    // Wait for the todo item to be displayed before trying to click it
    clickOnTodoItem(editedToDo)
  }

  fun ComposeTestRule.navigateBack() {
    onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed().performClick()
  }

  fun ComposeTestRule.checkAddToDoScreenIsDisplayed() {
    onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("Create a new task", substring = false, ignoreCase = true)
  }

  fun ComposeTestRule.checkOverviewScreenIsNotDisplayed() {
    onNodeWithTag(OverviewScreenTestTags.TODO_LIST).assertDoesNotExist()
  }

  fun ComposeTestRule.checkOverviewScreenIsDisplayed() {
    onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("overview", substring = true, ignoreCase = true)
  }

  fun ComposeTestRule.checkEditToDoScreenIsDisplayed() {
    onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("Edit Todo", substring = false, ignoreCase = true)
  }

  fun ComposeTestRule.checkBottomBarIsNotDisplayed() {
    onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsNotDisplayed()
    onNodeWithTag(NavigationTestTags.OVERVIEW_TAB).assertIsNotDisplayed()
    onNodeWithTag(NavigationTestTags.MAP_TAB).assertIsNotDisplayed()
  }

  fun ComposeTestRule.checkErrorMessageIsDisplayedForAddTodo() =
      onNodeWithTag(AddToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true).assertIsDisplayed()

  fun ComposeTestRule.checkErrorMessageIsDisplayedForEditTodo() =
      onNodeWithTag(EditToDoScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
          .assertIsDisplayed()

  fun checkNoTodoWereAdded(action: () -> Unit) {
    val numberOfTodos = runBlocking { repository.getAllTodos().size }
    action()
    runTest { assertEquals(numberOfTodos, repository.getAllTodos().size) }
  }

  fun checkTodoWasNotEdited(editingTodo: ToDo = todo1, block: () -> Unit) {
    val todoBeforeEdit = runBlocking { repository.getTodo(editingTodo.uid) }
    block()
    runTest {
      val todoAfterEdit = repository.getTodo(editingTodo.uid)
      assertEquals(todoBeforeEdit, todoAfterEdit)
    }
  }

  fun ComposeTestRule.enterEditTodoStatus(currentStatus: ToDoStatus, status: ToDoStatus) {
    val numberOfClick =
        (status.ordinal - currentStatus.ordinal + ToDoStatus.entries.size) % ToDoStatus.entries.size
    for (i in 0 until numberOfClick) {
      onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_STATUS).assertIsDisplayed().performClick()
    }
  }

  fun ComposeTestRule.onTodoItem(todo: ToDo, matcher: SemanticsMatcher) {
    onNode(
            hasTestTag(OverviewScreenTestTags.getTestTagForTodoItem(todo))
                .and(hasAnyDescendant(matcher)),
            useUnmergedTree = true)
        .assertIsDisplayed()
  }

  fun ComposeTestRule.checkMapScreenIsDisplayed() {
    onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("map", substring = true, ignoreCase = true)
  }

  fun ComposeTestRule.onLocationSuggestion(location: Location): SemanticsNodeInteraction {
    val hasTextLocation = hasText(location.name)
    val containsTextLocation = hasTextLocation.or(hasAnyDescendant(hasTextLocation))
    return onNode(
        hasTestTag(AddToDoScreenTestTags.LOCATION_SUGGESTION).and(containsTextLocation),
        useUnmergedTree = true)
  }

  fun ComposeTestRule.assertAllLocationSuggestionsAreDisplayed(fakeLocation: FakeLocation) {
    for (location in fakeLocation.locationSuggestions) {
      onLocationSuggestion(location).assertIsDisplayed()
    }
  }

  fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>
      .checkActivityStateOnPressBack(shouldFinish: Boolean) {
    activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    waitUntil { activity.isFinishing == shouldFinish }
    assertEquals(shouldFinish, activity.isFinishing)
  }

  fun ToDo.b2Equals(other: ToDo): Boolean =
      name == other.name &&
          description == other.description &&
          assigneeName == other.assigneeName &&
          dueDate.toDateString() == other.dueDate.toDateString() &&
          status == other.status

  fun ToDosRepository.getTodoByName(name: String): ToDo = runBlocking {
    getAllTodos().first { it.name == name }
  }

  companion object {

    const val MAX_LOCATION_SUGGESTIONS_DISPLAYED = 10

    fun Timestamp.toDateString(): String {
      val date = this.toDate()
      val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
      return dateFormat.format(date)
    }

    fun Timestamp.Companion.fromDate(year: Int, month: Int, day: Int): Timestamp {
      val calendar = Calendar.getInstance()
      calendar.set(year, month, day, 0, 0, 0)
      return Timestamp(calendar.time)
    }
  }
}
