package com.github.roamswent.roam.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.roamswent.roam.BootcampApp
import com.github.roamswent.roam.model.map.Location
import com.github.roamswent.roam.model.todo.ToDo
import com.github.roamswent.roam.model.todo.ToDoStatus
import com.github.roamswent.roam.utils.BootcampMilestone
import com.github.roamswent.roam.utils.InMemoryBootcampTest
import com.github.roamswent.roam.utils.StateCheckerBootcampTest
import com.google.firebase.Timestamp
import java.util.Calendar
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// *****************************************************************************
// ***                                                                       ***
// *** THIS FILE WILL BE OVERWRITTEN DURING GRADING. IT SHOULD BE LOCATED IN ***
// *** app/src/androidTest/java/com/github/se/bootcamp/ui/navigation.        ***
// *** DO **NOT** IMPLEMENT YOUR OWN TESTS IN THIS FILE                      ***
// ***                                                                       ***
// *****************************************************************************

class NavigationB2Test : StateCheckerBootcampTest(InMemoryBootcampTest(BootcampMilestone.B2)) {
  @get:Rule val composeTestRule = createComposeRule()

  private val validTodo =
      ToDo(
          uid = "uid_not_used",
          name = "title",
          description = "description",
          assigneeName = "test",
          dueDate = Timestamp.fromDate(2025, Calendar.SEPTEMBER, 1),
          location = Location(46.5191, 6.5668, "Any"),
          status = ToDoStatus.CREATED,
          ownerId = "user")

  @Before
  override fun setUp() {
    super.setUp()
    composeTestRule.setContent { BootcampApp() }
  }

  @Test
  fun canNavigateToEditToDoScreenFromOverview() {
    composeTestRule.navigateToEditToDoScreen(firstTodo)
    composeTestRule.checkEditToDoScreenIsDisplayed()
    composeTestRule.checkOverviewScreenIsNotDisplayed()
  }

  @Test
  fun addTodo_saveButtonNavigatesToOverviewToDoIfInputIsValid() {
    composeTestRule.navigateToAddToDoScreen()
    composeTestRule.enterAddTodoDetails(todo = validTodo)
    composeTestRule.clickOnSaveForAddTodo(waitForRedirection = true)
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  @Test
  fun editTodo_saveButtonNavigatesToOverviewToDoIfInputIsValid() {
    composeTestRule.navigateToEditToDoScreen(firstTodo)
    composeTestRule.clickOnSaveForEditTodo(waitForRedirection = true)
    composeTestRule.checkOverviewScreenIsDisplayed()
  }

  @Test
  fun topAppTitleIsCorrectOnEditToDoScreen() {
    composeTestRule.checkOverviewScreenIsDisplayed()
    composeTestRule.navigateToEditToDoScreen(firstTodo)
    composeTestRule.checkEditToDoScreenIsDisplayed()
  }

  @Test
  fun bottomBarIsNotDisplayedOnEditToDoScreen() {
    composeTestRule.checkOverviewScreenIsDisplayed()
    composeTestRule.clickOnTodoItem(firstTodo)
    composeTestRule.checkEditToDoScreenIsDisplayed()
    composeTestRule.checkBottomBarIsNotDisplayed()
  }
}
