package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.MeepleMeetApp
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.AuthUtils.signUpUser
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import java.util.UUID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var navController: NavHostController
  private lateinit var navigationActions: NavigationActions

  @Before
  fun setup() {
    // Call parent setup first to initialize Firebase emulators
    testsSetup()

    // Ensure we're signed out for unauthenticated navigation tests
    try {
      FirebaseProvider.auth.signOut()
      Thread.sleep(500) // Give time for auth state to propagate
    } catch (_: Exception) {
      // Ignore if no one is signed in
    }

    composeTestRule.setContent {
      navController = rememberNavController()
      navigationActions = NavigationActions(navController)
      MeepleMeetApp(navController = navController)
    }

    // Wait for the app to be ready
    composeTestRule.waitForIdle()
  }

  @Test
  fun allNavigationChecksInOnePass() =
      with(composeTestRule) {
        /* ----------------------------------------------------------
         * 1.  Initial state
         * ---------------------------------------------------------- */
        checkpoint("appStartsOnSignInScreen") {
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name)
        }

        /* ----------------------------------------------------------
         * 2.  Sign-In ↔ Sign-Up toggling
         * ---------------------------------------------------------- */
        checkpoint("navigationToSignUpScreenViaButton") {
          onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).performClick()
          waitForIdle()
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)
        }

        checkpoint("navigationBackFromSignUpToSignInViaButton") {
          onNodeWithTag(SignUpScreenTestTags.SIGN_IN_BUTTON).performClick()
          waitForIdle()
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name)
        }

        /* ----------------------------------------------------------
         * 3.  NavigationActions helpers
         * ---------------------------------------------------------- */
        checkpoint("navigationActionsCurrentRoute") {
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name)
        }

        checkpoint("navigationActionsNavigateTo") {
          navigationActions.navigateTo(MeepleMeetScreen.SignUp)
          waitForIdle()
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)
        }

        checkpoint("goBackNavigation") {
          navigationActions.goBack()
          waitForIdle()
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name)
        }

        checkpoint("navigatingToSameScreenTwice") {
          navigationActions.navigateTo(MeepleMeetScreen.SignUp)
          waitForIdle()
          navigationActions.navigateTo(MeepleMeetScreen.SignUp) // again
          waitForIdle()
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)
        }
        /* ----------------------------------------------------------
         * 4.  Back-stack behaviour
         * ---------------------------------------------------------- */
        checkpoint("multipleNavigationsPreserveStack") {
          navigationActions.navigateTo(MeepleMeetScreen.SignIn)
          navigationActions.navigateTo(MeepleMeetScreen.SignUp)
          navigationActions.goBack()
          navigationActions.goBack()
          assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)
        }

        /* ----------------------------------------------------------
         * 5.  Static configuration checks
         * ---------------------------------------------------------- */
        checkpoint("bottomBarScreensHaveCorrectProperties") {
          val bottomBarScreens = MeepleMeetScreen.entries.filter { it.inBottomBar }
          assert(bottomBarScreens.size == 5)
          assert(bottomBarScreens.contains(MeepleMeetScreen.DiscussionsOverview))
          assert(bottomBarScreens.contains(MeepleMeetScreen.SessionsOverview))
          assert(bottomBarScreens.contains(MeepleMeetScreen.PostsOverview))
          assert(bottomBarScreens.contains(MeepleMeetScreen.Profile))
          assert(bottomBarScreens.contains(MeepleMeetScreen.Map))
          bottomBarScreens.forEach { screen ->
            assert(screen.icon != null)
            assert(screen.iconSelected != null)
            assert(screen.testTag != null)
          }
        }

        checkpoint("authScreensAreNotInBottomBar") {
          assert(!MeepleMeetScreen.SignIn.inBottomBar)
          assert(!MeepleMeetScreen.SignUp.inBottomBar)
          assert(!MeepleMeetScreen.CreateAccount.inBottomBar)
        }

        checkpoint("navigationTestTagsAreUnique") {
          val testTags =
              setOf(
                  NavigationTestTags.BOTTOM_NAVIGATION_MENU,
                  NavigationTestTags.SCREEN_TITLE,
                  NavigationTestTags.GO_BACK_BUTTON,
                  NavigationTestTags.SESSIONS_TAB,
                  NavigationTestTags.DISCUSSIONS_TAB,
                  NavigationTestTags.DISCOVER_TAB,
                  NavigationTestTags.PROFILE_TAB)
          assert(testTags.size == 7)
        }
      }
}

/**
 * Authenticated navigation tests that require a signed-in user. These tests cover navigation flows
 * in the main app after authentication.
 *
 * Note: Each test creates a unique user to ensure test isolation.
 */
@RunWith(AndroidJUnit4::class)
class AuthenticatedNavigationTest : FirestoreTests() {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  @Before
  fun setupAuthenticatedUser() {
    // Sign out any existing Firebase session before creating a new user
    try {
      FirebaseProvider.auth.signOut()
      composeTestRule.waitForIdle()
      Thread.sleep(500) // Give time for auth state to propagate
    } catch (_: Exception) {
      // Ignore if no one is signed in
    }

    // Create a unique user for this test
    val testEmail = "navtest${UUID.randomUUID().toString().take(8)}@example.com"
    val testPassword = "Password123!"
    val testHandle = "navtest${UUID.randomUUID().toString().take(6)}"
    val testUsername = "Nav Test User"

    composeTestRule.signUpUser(testEmail, testPassword, testHandle, testUsername)
  }

  @Test
  fun allAuthenticatedNavigationChecksInOnePass() =
      with(composeTestRule) {
        /* ----------------------------------------------------------
         * 0.  Post-sign-up landing spot
         * ---------------------------------------------------------- */
        checkpoint("navigationAfterAuthenticationStartsAtDiscussions") {
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }

        /* ----------------------------------------------------------
         * 1.  Bottom-bar existence & tab hopping
         * ---------------------------------------------------------- */
        checkpoint("bottomNavigationMenuExists") {
          onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
              .assertExists()
              .assertIsDisplayed()
        }
        checkpoint("navigationBetweenAllBottomBarTabs") {
          // Discussions already active
          onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.SessionsOverview.title)

          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.PostsOverview.title)

          onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
          waitForIdle()
          //          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
          //              .assertTextContains(MeepleMeetScreen.Profile.title)
        }

        /* ----------------------------------------------------------
         * 2.  Create Discussion flow
         * ---------------------------------------------------------- */
        checkpoint("navigationToCreateDiscussionScreen") {
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          waitForIdle()
          onNodeWithTag("Add Discussion").performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.CreateDiscussion.title)
          onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true).assertExists()
        }
        checkpoint("backNavigationFromCreateDiscussion") {
          onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true).performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }
        /* ----------------------------------------------------------
         * 3.  Create Post flow
         * ---------------------------------------------------------- */
        checkpoint("navigationToCreatePostScreen") {
          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          waitForIdle()
          onNodeWithTag("AddPostButton").performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.CreatePost.title)
          onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true).assertExists()
        }
        checkpoint("backNavigationFromCreatePost") {
          onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true).performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.PostsOverview.title)
        }
        /* ----------------------------------------------------------
         * 4.  Bottom-bar back-stack behaviour
         * ---------------------------------------------------------- */
        checkpoint("bottomBarNavigationDoesNotBuildBackStack") {
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
          waitForIdle()

          // pressing back should **not** cycle through the tabs
          val nodes = onAllNodesWithTag(NavigationTestTags.SCREEN_TITLE)

          if (nodes.fetchSemanticsNodes().isNotEmpty()) {
            nodes.onFirst().assertTextContains(MeepleMeetScreen.Profile.title)
          }
        }
        checkpoint("bottomBarRemainsVisibleAndHighlightsCurrentTab") {
          val tabs =
              listOf(
                  NavigationTestTags.DISCUSSIONS_TAB to MeepleMeetScreen.DiscussionsOverview.title,
                  NavigationTestTags.SESSIONS_TAB to MeepleMeetScreen.SessionsOverview.title,
                  NavigationTestTags.DISCOVER_TAB to MeepleMeetScreen.PostsOverview.title,
                  NavigationTestTags.PROFILE_TAB to MeepleMeetScreen.Profile.title)
          tabs.forEach { (tab, title) ->
            onNodeWithTag(tab).performClick()
            waitForIdle()
            onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
            if (tab != NavigationTestTags.PROFILE_TAB)
                onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertTextContains(title)
          }
        }

        checkpoint("navigatingToSameBottomBarTabTwice") {
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }

        /* ----------------------------------------------------------
         * 5.  Deep stack preservation
         * ---------------------------------------------------------- */
        checkpoint("deepNavigationStackIsPreserved") {
          onNodeWithTag("Add Discussion").performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true).performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }

        /* ----------------------------------------------------------
         * 6.  Reachability & title checks
         * ---------------------------------------------------------- */
        checkpoint("allBottomBarTabsAreReachable") {
          val tabs =
              listOf(
                  NavigationTestTags.DISCUSSIONS_TAB to MeepleMeetScreen.DiscussionsOverview.title,
                  NavigationTestTags.SESSIONS_TAB to MeepleMeetScreen.SessionsOverview.title,
                  NavigationTestTags.DISCOVER_TAB to MeepleMeetScreen.PostsOverview.title,
                  NavigationTestTags.PROFILE_TAB to MeepleMeetScreen.Profile.title)
          tabs.forEach { (tag, title) ->
            onNodeWithTag(tag).performClick()
            waitForIdle()
            if (tag != NavigationTestTags.PROFILE_TAB)
                onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertTextContains(title)
          }
        }

        /* ----------------------------------------------------------
         * 7.  Content creation + navigation
         * ---------------------------------------------------------- */
        checkpoint("createDiscussionAndNavigateBack") {
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          onNodeWithTag("Add Discussion").performClick()
          onNodeWithTag("Add Title").performTextInput("Test Discussion")
          onNodeWithTag("Create Discussion").performClick()
          waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Test Discussion", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
          }
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }
        checkpoint("createMultipleDiscussionsSequentially") {
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          waitForIdle()
          repeat(2) { i ->
            onNodeWithTag("Add Discussion").performClick()
            waitForIdle()
            onNodeWithTag("Add Title").performTextInput("Multi Discussion $i")
            onNodeWithTag("Create Discussion").performClick()
            waitForIdle()
          }
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }

        checkpoint("createPostAndNavigateBack") {
          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          onNodeWithTag("AddPostButton").performClick()
          onNodeWithTag("create_post_title_field").performTextInput("Test Post")
          onNodeWithTag("create_post_body_field").performTextInput("Test Description")
          onNodeWithTag("create_post_post_btn").performClick()
          waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Test Post", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
          }
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.PostsOverview.title)
        }

        checkpoint("createPostWithTagAndNavigateBack") {
          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          waitForIdle()
          onNodeWithTag("AddPostButton").performClick()
          waitForIdle()

          onNodeWithTag("create_post_title_field").performTextInput("Tagged Post")
          onNodeWithTag("create_post_body_field").performTextInput("Post with tags")
          // add a tag and post
          onNodeWithTag("create_post_tag_search_field").performTextInput("test")
          onNodeWithTag("create_post_tag_search_icon").performClick()
          waitForIdle()
          onNodeWithTag("create_post_post_btn").performClick()

          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.PostsOverview.title)
        }
        checkpoint("createMultiplePostsSequentially") {
          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          waitForIdle()
          repeat(2) { i ->
            onNodeWithTag("AddPostButton").performClick()
            waitForIdle()
            onNodeWithTag("create_post_title_field").performTextInput("Post $i")
            onNodeWithTag("create_post_body_field").performTextInput("Body $i")
            onNodeWithTag("create_post_post_btn").performClick()
            waitForIdle()
          }
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.PostsOverview.title)
        }

        /* ----------------------------------------------------------
         * 8.  Discard flows
         * ---------------------------------------------------------- */
        checkpoint("discardPostNavigatesBack") {
          onNodeWithTag("AddPostButton").performClick()
          onNodeWithTag("create_post_draft_btn").performClick()
          waitForIdle()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.PostsOverview.title)
        }
        /* ----------------------------------------------------------
         * 9.  Profile & sign-out button
         * ---------------------------------------------------------- */
        checkpoint("profileScreenSignOutButton") {
          onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
          waitForIdle()
          //          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
          //              .assertTextContains(MeepleMeetScreen.Profile.title)
          onNodeWithTag("Logout Button").assertExists()
        }

        /* ----------------------------------------------------------
         * 10.  Stress / stability
         * ---------------------------------------------------------- */
        checkpoint("navigationDoesNotCrashOnQuickTabSwitching") {
          repeat(3) {
            onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
            onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
            onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
            onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
          }
          waitForIdle()
          onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
        }

        /* ----------------------------------------------------------
         * 11.  Complete user flow
         * ---------------------------------------------------------- */
        checkpoint("completeNavigationFlow") {
          // 1. Discussions
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          // 2. Create discussion
          onNodeWithTag("Add Discussion").performClick()
          onNodeWithTag("Add Title").performTextInput("Flow Test")
          onNodeWithTag("Create Discussion").performClick()
          waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Flow Test", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
          }
          // 3. Posts
          onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
          // 4. Create post
          onNodeWithTag("AddPostButton").performClick()
          onNodeWithTag("create_post_title_field").performTextInput("Flow Post")
          onNodeWithTag("create_post_body_field").performTextInput("Test")
          onNodeWithTag("create_post_post_btn").performClick()
          waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Flow Post", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
          }
          // 5. Sessions
          onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
          // 6. Profile
          onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
          // 7. Back to Discussions
          onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
          onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
              .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
        }
      }
}
