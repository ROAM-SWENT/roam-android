package com.github.se.bootcamp.ui.overview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.github.se.bootcamp.model.todo.ToDo
import com.github.se.bootcamp.model.todo.ToDoStatus
import com.github.se.bootcamp.ui.navigation.NavigationTestTags
import com.github.se.bootcamp.utils.BootcampMilestone
import com.github.se.bootcamp.utils.FakeHttpClient
import com.github.se.bootcamp.utils.InMemoryBootcampTest
import com.google.firebase.Timestamp
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocationBasedTodosB3Test : InMemoryBootcampTest(BootcampMilestone.B3) {

  @get:Rule val composeTestRule = createComposeRule()

  private class RecordingInterceptor : Interceptor {
    val seen = CopyOnWriteArrayList<HttpUrl>()

    override fun intercept(chain: Interceptor.Chain): Response {
      val req = chain.request()
      seen += req.url
      return chain.proceed(req)
    }

    fun sawNominatim(): Boolean = seen.any { it.host.contains("nominatim.openstreetmap.org") }
  }

  private lateinit var recorder: RecordingInterceptor

  override fun initializeHTTPClient(): OkHttpClient {
    recorder = RecordingInterceptor()
    val base = FakeHttpClient.getClient(checkUrl = false)
    val b = base.newBuilder()
    b.interceptors().add(0, recorder)
    return b.build()
  }

  @Before
  override fun setUp() {
    super.setUp()
  }

  @Test
  fun addTodo_allTagsPresent_and_locationTypingHitsNominatim() {
    composeTestRule.setContent { AddTodoScreen() }

    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()

    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_ASSIGNEE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_DATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AddToDoScreenTestTags.TODO_SAVE).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(AddToDoScreenTestTags.INPUT_TODO_LOCATION)
        .performTextInput("EPFL")

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(AddToDoScreenTestTags.LOCATION_SUGGESTION, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule.runOnIdle { assert(recorder.sawNominatim()) }
  }

  @Test
  fun editTodo_allTagsPresent_and_locationTypingHitsNominatim() {
    val seeded =
        ToDo(
            uid = "t-42",
            name = "Seeded",
            description = "For Edit screen",
            assigneeName = "Tester",
            dueDate = Timestamp.now(),
            location = null,
            status = ToDoStatus.CREATED,
            ownerId = "user")
    runBlocking { repository.addTodo(seeded) }

    composeTestRule.setContent { EditToDoScreen(todoUid = "t-42") }

    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_ASSIGNEE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_DATE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_STATUS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.TODO_SAVE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditToDoScreenTestTags.TODO_DELETE).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(EditToDoScreenTestTags.INPUT_TODO_LOCATION)
        .performTextInput("EPFL")

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(EditToDoScreenTestTags.LOCATION_SUGGESTION, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule.runOnIdle { assert(recorder.sawNominatim()) }
  }
}
