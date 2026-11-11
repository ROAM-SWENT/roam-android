/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.roamswent.roam.model.friend.FriendRepository
import com.github.roamswent.roam.model.friend.FriendRequest as RepositoryFriendRequest
import com.github.roamswent.roam.model.friend.Friendship
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.model.profile.PublicProfileRepository
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.theme.SampleAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class FriendsRouteTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun routeLoadsRepositoriesAndConnectsFriendActions() {
    val friendsRepository = mockk<FriendRepository>()
    val profilesRepository = mockk<PublicProfileRepository>()
    coEvery { profilesRepository.getCurrentProfile() } returns PublicProfile("self", "Traveler")
    coEvery { profilesRepository.getProfile("friend") } returns PublicProfile("friend", "Ada")
    coEvery { profilesRepository.getProfile("requester") } returns
        PublicProfile("requester", "Grace")
    coEvery { friendsRepository.getFriends() } returns listOf(Friendship("friendship", "friend"))
    coEvery { friendsRepository.getIncomingRequests() } returns
        listOf(RepositoryFriendRequest("request-id", "requester", "self"))
    coEvery { friendsRepository.getOutgoingRequests() } returns emptyList()
    coEvery { friendsRepository.acceptFriendRequest("request-id") } returns Unit
    coEvery { friendsRepository.removeFriend("friend") } returns Unit
    var profileClicked = false
    composeTestRule.setContent {
      SampleAppTheme {
        FriendsRoute(
            profileName = "Traveler",
            onProfileClicked = { profileClicked = true },
            friendRepository = friendsRepository,
            publicProfileRepository = profilesRepository,
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Ada").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.friends_accept_request).performClick()
    composeTestRule.onNodeWithContentDescription("Open profile").performClick()
    composeTestRule.onNodeWithContentDescription("More options for Ada").performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_remove_friend).performClick()
    composeTestRule.onNodeWithText("Delete friend").performClick()
    composeTestRule.waitForIdle()

    coVerify { friendsRepository.acceptFriendRequest("request-id") }
    coVerify { friendsRepository.removeFriend("friend") }
    assert(profileClicked)
  }

  @Test
  fun routeSupportsDefaultNavigationAndProfileArguments() {
    val friendsRepository = mockk<FriendRepository>()
    val profilesRepository = mockk<PublicProfileRepository>()
    coEvery { profilesRepository.getCurrentProfile() } returns null
    coEvery { profilesRepository.getProfile("friend") } returns PublicProfile("friend", "Ada")
    coEvery { friendsRepository.getFriends() } returns listOf(Friendship("friendship", "friend"))
    coEvery { friendsRepository.getIncomingRequests() } returns emptyList()
    coEvery { friendsRepository.getOutgoingRequests() } returns emptyList()
    var profileName by mutableStateOf<String?>(null)

    composeTestRule.setContent {
      SampleAppTheme {
        FriendsRoute(
            profileName = profileName,
            friendRepository = friendsRepository,
            publicProfileRepository = profilesRepository,
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { profileName = "Ada" }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.home_profile_button).assertIsDisplayed()
    composeTestRule.onNodeWithText("Ada").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Open profile").performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_friend_row).performClick()
  }

  @Test
  fun routeBuildsDefaultRepositoriesAndShowsTheirAuthenticationError() {
    val firestore = mockk<FirebaseFirestore>()
    val auth = mockk<FirebaseAuth>()
    mockkStatic(FirebaseFirestore::class)
    mockkStatic(FirebaseAuth::class)
    try {
      every { FirebaseFirestore.getInstance() } returns firestore
      every { firestore.firestoreSettings = any() } just Runs
      every { FirebaseAuth.getInstance() } returns auth
      every { auth.currentUser } returns null

      composeTestRule.setContent { SampleAppTheme { FriendsRoute() } }
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule
            .onAllNodesWithText("A signed-in user is required for this operation.")
            .fetchSemanticsNodes()
            .isNotEmpty()
      }

      composeTestRule
          .onNodeWithText("A signed-in user is required for this operation.")
          .assertIsDisplayed()
      composeTestRule.onNodeWithContentDescription("Open profile").performClick()
    } finally {
      unmockkStatic(FirebaseAuth::class)
      unmockkStatic(FirebaseFirestore::class)
    }
  }

  @Test
  fun routeBuildsDefaultFriendRepositoryWhenProfileRepositoryIsInjected() {
    val firestore = mockk<FirebaseFirestore>()
    val auth = mockk<FirebaseAuth>()
    val profilesRepository = mockk<PublicProfileRepository>()
    mockkStatic(FirebaseFirestore::class)
    mockkStatic(FirebaseAuth::class)
    try {
      every { FirebaseFirestore.getInstance() } returns firestore
      every { firestore.firestoreSettings = any() } just Runs
      every { FirebaseAuth.getInstance() } returns auth
      every { auth.currentUser } returns null
      coEvery { profilesRepository.getCurrentProfile() } returns null

      composeTestRule.setContent {
        SampleAppTheme { FriendsRoute(publicProfileRepository = profilesRepository) }
      }
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule
            .onAllNodesWithText("A signed-in user is required for this operation.")
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
    } finally {
      unmockkStatic(FirebaseAuth::class)
      unmockkStatic(FirebaseFirestore::class)
    }
  }

  @Test
  fun routeBuildsDefaultProfileRepositoryWhenFriendRepositoryIsInjected() {
    val firestore = mockk<FirebaseFirestore>()
    val auth = mockk<FirebaseAuth>()
    val friendsRepository = mockk<FriendRepository>()
    mockkStatic(FirebaseFirestore::class)
    mockkStatic(FirebaseAuth::class)
    try {
      every { FirebaseFirestore.getInstance() } returns firestore
      every { firestore.firestoreSettings = any() } just Runs
      every { FirebaseAuth.getInstance() } returns auth
      every { auth.currentUser } returns null

      composeTestRule.setContent {
        SampleAppTheme { FriendsRoute(friendRepository = friendsRepository) }
      }
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        composeTestRule
            .onAllNodesWithText("A signed-in user is required for this operation.")
            .fetchSemanticsNodes()
            .isNotEmpty()
      }
    } finally {
      unmockkStatic(FirebaseAuth::class)
      unmockkStatic(FirebaseFirestore::class)
    }
  }

  @Test
  fun routeRecomposesWhenItsPresentationArgumentsChange() {
    val initialFriendsRepository = mockk<FriendRepository>()
    val initialProfilesRepository = mockk<PublicProfileRepository>()
    val replacementFriendsRepository = mockk<FriendRepository>()
    val replacementProfilesRepository = mockk<PublicProfileRepository>()
    coEvery { initialProfilesRepository.getCurrentProfile() } returns null
    coEvery { initialFriendsRepository.getFriends() } returns emptyList()
    coEvery { initialFriendsRepository.getIncomingRequests() } returns emptyList()
    coEvery { initialFriendsRepository.getOutgoingRequests() } returns emptyList()
    coEvery { replacementProfilesRepository.getCurrentProfile() } returns null
    coEvery { replacementFriendsRepository.getFriends() } returns emptyList()
    coEvery { replacementFriendsRepository.getIncomingRequests() } returns emptyList()
    coEvery { replacementFriendsRepository.getOutgoingRequests() } returns emptyList()
    var profileClicks = 0
    var profileName by mutableStateOf("Traveler")
    var friendRepository by mutableStateOf(initialFriendsRepository)
    var profilesRepository by mutableStateOf(initialProfilesRepository)
    var profileClickAction by mutableStateOf<() -> Unit>({ profileClicks++ })

    composeTestRule.setContent {
      SampleAppTheme {
        FriendsRoute(
            profileName = profileName,
            onProfileClicked = profileClickAction,
            friendRepository = friendRepository,
            publicProfileRepository = profilesRepository,
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle {
      profileName = "Ada"
      friendRepository = replacementFriendsRepository
      profilesRepository = replacementProfilesRepository
      profileClickAction = { profileClicks += 2 }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.home_profile_button).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Open profile").performClick()
    assertEquals(2, profileClicks)
  }

  @Test
  fun routeSupportsExplicitNoOpNavigationCallbacks() {
    val friendsRepository = mockk<FriendRepository>()
    val profilesRepository = mockk<PublicProfileRepository>()
    coEvery { profilesRepository.getCurrentProfile() } returns null
    coEvery { profilesRepository.getProfile("friend") } returns PublicProfile("friend", "Ada")
    coEvery { friendsRepository.getFriends() } returns listOf(Friendship("friendship", "friend"))
    coEvery { friendsRepository.getIncomingRequests() } returns emptyList()
    coEvery { friendsRepository.getOutgoingRequests() } returns emptyList()

    composeTestRule.setContent {
      SampleAppTheme {
        FriendsRoute(
            modifier = Modifier,
            profileName = null,
            onProfileClicked = {},
            onFriendClicked = {},
            friendRepository = friendsRepository,
            publicProfileRepository = profilesRepository,
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithContentDescription("Open profile").performClick()
    composeTestRule.onNodeWithTag(C.Tag.friends_friend_row).performClick()
  }
}
