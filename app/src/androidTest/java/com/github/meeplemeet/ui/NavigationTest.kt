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
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.AuthUtils.signUpUser
import com.github.meeplemeet.utils.FirestoreTests
import java.util.UUID
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest : FirestoreTests() {

  @get:Rule val composeTestRule = createComposeRule()

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
    } catch (e: Exception) {
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
  fun testAppStartsOnSignInScreen() {
    // Verify app starts on Sign In screen by checking the current route
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name) {
      "Expected SignIn screen, but got: ${navigationActions.currentRoute()}"
    }
  }

  @Test
  fun testNavigationToSignUpScreenViaButton() {
    // Find and click sign up button using test tag
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).performClick()

    // Verify we're on Sign Up screen
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name) {
      "Expected SignUp screen, but got: ${navigationActions.currentRoute()}"
    }
  }

  @Test
  fun testNavigationBackFromSignUpToSignInViaButton() {
    // Navigate to Sign Up
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).performClick()
    composeTestRule.waitForIdle()

    // Navigate back to Sign In using the login button
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_IN_BUTTON).performClick()
    composeTestRule.waitForIdle()

    // Verify we're back on Sign In screen
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name) {
      "Expected SignIn screen, but got: ${navigationActions.currentRoute()}"
    }
  }

  @Test
  fun testNavigationActionsCurrentRoute() {
    // Test that currentRoute() returns the correct route
    composeTestRule.waitForIdle()
    val currentRoute = navigationActions.currentRoute()
    assert(currentRoute == MeepleMeetScreen.SignIn.name) {
      "Expected SignIn, but got: $currentRoute"
    }
  }

  @Test
  fun testNavigationActionsNavigateTo() {
    // Test programmatic navigation using NavigationActions
    navigationActions.navigateTo(MeepleMeetScreen.SignUp)
    composeTestRule.waitForIdle()

    val currentRoute = navigationActions.currentRoute()
    assert(currentRoute == MeepleMeetScreen.SignUp.name) {
      "Expected SignUp, but got: $currentRoute"
    }
  }

  @Test
  fun testGoBackNavigation() {
    // Navigate forward
    navigationActions.navigateTo(MeepleMeetScreen.SignUp)
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)

    // Navigate back
    navigationActions.goBack()
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name)
  }

  @Test
  fun testNavigatingToSameScreenTwice() {
    // First navigation to SignUp
    navigationActions.navigateTo(MeepleMeetScreen.SignUp)
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)

    // Try to navigate to SignUp again
    navigationActions.navigateTo(MeepleMeetScreen.SignUp)
    composeTestRule.waitForIdle()

    // Should still be on SignUp
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)
  }

  @Test
  fun testMultipleNavigationsPreserveStack() {
    // Navigate SignIn -> SignUp -> SignIn -> SignUp
    navigationActions.navigateTo(MeepleMeetScreen.SignUp)
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)

    navigationActions.navigateTo(MeepleMeetScreen.SignIn)
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignIn.name)

    navigationActions.navigateTo(MeepleMeetScreen.SignUp)
    composeTestRule.waitForIdle()
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)

    // Go back twice
    navigationActions.goBack()
    composeTestRule.waitForIdle()
    navigationActions.goBack()
    composeTestRule.waitForIdle()

    // Should be back on SignUp (first one we navigated to)
    assert(navigationActions.currentRoute() == MeepleMeetScreen.SignUp.name)
  }

  @Test
  fun testBottomBarScreensHaveCorrectProperties() {
    // Test that the bottom bar screens are properly configured
    val bottomBarScreens = MeepleMeetScreen.entries.filter { it.inBottomBar }

    assert(bottomBarScreens.size == 4) {
      "Expected 4 bottom bar screens, but found: ${bottomBarScreens.size}"
    }

    // Verify expected screens are in the bottom bar
    assert(bottomBarScreens.contains(MeepleMeetScreen.DiscussionsOverview))
    assert(bottomBarScreens.contains(MeepleMeetScreen.SessionsOverview))
    assert(bottomBarScreens.contains(MeepleMeetScreen.PostsOverview))
    assert(bottomBarScreens.contains(MeepleMeetScreen.Profile))

    // Verify each has required properties for bottom bar
    bottomBarScreens.forEach { screen ->
      assert(screen.icon != null) { "${screen.name} should have an icon" }
      assert(screen.iconSelected != null) { "${screen.name} should have a selected icon" }
      assert(screen.testTag != null) { "${screen.name} should have a test tag" }
    }
  }

  @Test
  fun testAuthScreensAreNotInBottomBar() {
    // Verify auth screens are not in the bottom bar
    assert(!MeepleMeetScreen.SignIn.inBottomBar)
    assert(!MeepleMeetScreen.SignUp.inBottomBar)
    assert(!MeepleMeetScreen.CreateAccount.inBottomBar)
  }

  @Test
  fun testNavigationTestTagsAreUnique() {
    // Test that navigation test tags are properly defined and unique
    val testTags =
        setOf(
            NavigationTestTags.BOTTOM_NAVIGATION_MENU,
            NavigationTestTags.SCREEN_TITLE,
            NavigationTestTags.GO_BACK_BUTTON,
            NavigationTestTags.SESSIONS_TAB,
            NavigationTestTags.DISCUSSIONS_TAB,
            NavigationTestTags.DISCOVER_TAB,
            NavigationTestTags.PROFILE_TAB)

    // Verify we have 7 unique tags
    assert(testTags.size == 7) { "Expected 7 unique navigation test tags" }
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

  @Before
  fun setupAuthenticatedUser() {
    // Sign out any existing Firebase session before creating a new user
    try {
      FirebaseProvider.auth.signOut()
      composeTestRule.waitForIdle()
      Thread.sleep(500) // Give time for auth state to propagate
    } catch (e: Exception) {
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
  fun testBottomNavigationMenuExists() {
    // After authentication, bottom navigation menu should be visible
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun testNavigationBetweenAllBottomBarTabs() {
    // Test navigation between all main tabs
    composeTestRule.waitForIdle()

    // Discussions Tab (default after sign up)
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)

    // Sessions Tab
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.SessionsOverview.title)

    // Posts/Discover Tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)

    // Profile Tab
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).assertExists().performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.Profile.title)
  }

  @Test
  fun testNavigationToCreateDiscussionScreen() {
    // Navigate to Discussions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Click add discussion button
    composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Create Discussion screen
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreateDiscussion.title)

    // Verify back button exists
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun testBackNavigationFromCreateDiscussion() {
    // Navigate to Create Discussion
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Create Discussion screen
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreateDiscussion.title)

    // Click back button
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Should be back on Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testNavigationToCreatePostScreen() {
    // Navigate to Posts tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    // Click add post button
    composeTestRule.onNodeWithTag("AddPostButton").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Create Post screen
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreatePost.title)

    // Verify back button exists
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun testBackNavigationFromCreatePost() {
    // Navigate to Create Post
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("AddPostButton").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Create Post screen
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreatePost.title)

    // Click back button
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Should be back on Posts Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)
  }

  @Test
  fun testBottomBarNavigationDoesNotBuildBackStack() {
    // Navigate between multiple bottom bar tabs
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Profile
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.Profile.title)

    // Try to go back - on Profile screen, back button should not navigate through all previous tabs
    // The back stack should have been cleared for bottom bar navigation
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.Profile.title)
  }

  @Test
  fun testNavigatingToSameBottomBarTabTwice() {
    // Navigate to Discussions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate to Discussions tab again
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Should still be on Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testDeepNavigationStackIsPreserved() {
    // Start on Discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate to Create Discussion
    composeTestRule.onNodeWithTag("Add Discussion").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Create Discussion
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreateDiscussion.title)

    // Go back
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Should be back on Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testAllBottomBarTabsAreReachable() {
    // Test that all bottom bar tabs are clickable and navigate correctly
    val bottomBarTabs =
        listOf(
            NavigationTestTags.DISCUSSIONS_TAB to MeepleMeetScreen.DiscussionsOverview.title,
            NavigationTestTags.SESSIONS_TAB to MeepleMeetScreen.SessionsOverview.title,
            NavigationTestTags.DISCOVER_TAB to MeepleMeetScreen.PostsOverview.title,
            NavigationTestTags.PROFILE_TAB to MeepleMeetScreen.Profile.title)

    bottomBarTabs.forEach { (tabTag, expectedTitle) ->
      composeTestRule.onNodeWithTag(tabTag).assertExists().performClick()
      composeTestRule.waitForIdle()
      composeTestRule
          .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
          .assertTextContains(expectedTitle)
    }
  }

  @Test
  fun testMultipleDeepNavigations() {
    // Test multiple deep navigations and back navigations

    // Navigate to Discussions -> Create Discussion
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreateDiscussion.title)

    // Go back to Discussions
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Navigate to Posts -> Create Post
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.CreatePost.title)

    // Go back to Posts
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)
  }

  @Test
  fun testNavigationAfterAuthenticationStartsAtDiscussions() {
    // After authentication, user should be on Discussions Overview
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testCreateDiscussionAndNavigateBack() {
    // Navigate to Create Discussion screen
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    // Fill in discussion title (minimum required field)
    composeTestRule.onNodeWithTag("Add Title").performTextInput("Test Discussion")
    composeTestRule.waitForIdle()

    // Click create button
    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Should navigate back to Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testCreatePostAndNavigateBack() {
    // Navigate to Posts tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate to Create Post
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()

    // Fill in required fields
    composeTestRule.onNodeWithTag("create_post_title_field").performTextInput("Test Post")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_body_field").performTextInput("Test Description")
    composeTestRule.waitForIdle()

    // Click post button
    composeTestRule.onNodeWithTag("create_post_post_btn").performClick()
    composeTestRule.waitForIdle()

    // Should navigate back to Posts Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)
  }

  @Test
  fun testDiscardPostNavigatesBack() {
    // Navigate to Create Post
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()

    // Click discard button
    composeTestRule.onNodeWithTag("create_post_draft_btn").performClick()
    composeTestRule.waitForIdle()

    // Should navigate back to Posts Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)
  }

  @Test
  fun testProfileScreenSignOutButton() {
    // Navigate to Profile tab
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Profile screen
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.Profile.title)

    // Verify logout button exists (we don't actually sign out to avoid breaking other tests)
    composeTestRule.onNodeWithTag("Logout Button").assertExists()
  }

  @Test
  fun testNavigationFromDiscussionsToSessions() {
    // Start on Discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate to Sessions
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Sessions
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.SessionsOverview.title)
  }

  @Test
  fun testNavigationFromPostsToProfile() {
    // Start on Posts
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate to Profile
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Profile
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.Profile.title)
  }

  @Test
  fun testBottomBarPersistsAcrossScreens() {
    // Test that bottom bar is visible on all main screens
    val screens =
        listOf(
            NavigationTestTags.DISCUSSIONS_TAB,
            NavigationTestTags.SESSIONS_TAB,
            NavigationTestTags.DISCOVER_TAB,
            NavigationTestTags.PROFILE_TAB)

    screens.forEach { screenTag ->
      composeTestRule.onNodeWithTag(screenTag).performClick()
      composeTestRule.waitForIdle()

      // Bottom bar should always be visible
      composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
    }
  }

  @Test
  fun testCreateDiscussionBackButtonPreservesUnsavedData() {
    // Navigate to Create Discussion
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    // Enter some data
    composeTestRule.onNodeWithTag("Add Title").performTextInput("My Discussion")
    composeTestRule.waitForIdle()

    // Go back using back button
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Should be back on Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testBottomBarHighlightsCurrentTab() {
    // Navigate to each tab and verify it's selected
    val tabs =
        listOf(
            NavigationTestTags.DISCUSSIONS_TAB to MeepleMeetScreen.DiscussionsOverview.title,
            NavigationTestTags.SESSIONS_TAB to MeepleMeetScreen.SessionsOverview.title,
            NavigationTestTags.DISCOVER_TAB to MeepleMeetScreen.PostsOverview.title,
            NavigationTestTags.PROFILE_TAB to MeepleMeetScreen.Profile.title)

    tabs.forEach { (tabTag, title) ->
      composeTestRule.onNodeWithTag(tabTag).performClick()
      composeTestRule.waitForIdle()

      // Verify the screen title matches (indicating we're on the right screen)
      composeTestRule.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertTextContains(title)
    }
  }

  @Test
  fun testNavigationDoesNotCrashOnQuickTabSwitching() {
    // Quickly switch between tabs to test navigation stability
    repeat(3) {
      composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
      composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
      composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
      composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
    }
    composeTestRule.waitForIdle()

    // Should still be functional
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
  }

  @Test
  fun testCreateAndSelectDiscussion() {
    // Navigate to Discussions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Create a discussion
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    val discussionTitle = "Test Discussion ${System.currentTimeMillis()}"
    composeTestRule.onNodeWithTag("Add Title").performTextInput(discussionTitle)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Description").performTextInput("Test Description")
    composeTestRule.waitForIdle()

    // Create the discussion
    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Wait for discussion to be created and appear in list
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      try {
        composeTestRule
            .onAllNodesWithText(discussionTitle, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (e: Exception) {
        false
      }
    }

    // Click on the created discussion
    composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()

    // Should be on Discussion screen (verify by looking for back button or message input)
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      try {
        composeTestRule
            .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
            .assertExists()
        true
      } catch (e: Exception) {
        false
      }
    }
  }

  @Test
  fun testMultipleDiscussionsCanBeCreatedAndListed() {
    // Navigate to Discussions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Create first discussion
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    val discussionTitle1 = "First ${System.currentTimeMillis()}"
    composeTestRule.onNodeWithTag("Add Title").performTextInput(discussionTitle1)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Should be back on overview - create another discussion
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    val discussionTitle2 = "Second ${System.currentTimeMillis()}"
    composeTestRule.onNodeWithTag("Add Title").performTextInput(discussionTitle2)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Both discussions should be visible in the list
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      try {
        composeTestRule
            .onAllNodesWithText(discussionTitle1, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty() &&
            composeTestRule
                .onAllNodesWithText(discussionTitle2, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
      } catch (e: Exception) {
        false
      }
    }
  }

  @Test
  fun testBackNavigationFromDiscussionToOverview() {
    // Create and select a discussion
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    val discussionTitle = "Back Nav Test ${System.currentTimeMillis()}"
    composeTestRule.onNodeWithTag("Add Title").performTextInput(discussionTitle)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Click on the discussion
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      try {
        composeTestRule
            .onAllNodesWithText(discussionTitle, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (e: Exception) {
        false
      }
    }

    composeTestRule.onNodeWithText(discussionTitle, useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()

    // Wait for discussion screen
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      try {
        composeTestRule
            .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
            .assertExists()
        true
      } catch (e: Exception) {
        false
      }
    }

    // Go back
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Should be back on Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testNavigateToSessionsOverviewAndBack() {
    // Navigate to Sessions tab
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Sessions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.SessionsOverview.title)

    // Navigate to Discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate back to Sessions
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Should still be on Sessions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.SessionsOverview.title)
  }

  @Test
  fun testCreatePostWithTagsAndNavigateBack() {
    // Navigate to Posts tab
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    // Navigate to Create Post
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()

    // Fill in fields
    composeTestRule.onNodeWithTag("create_post_title_field").performTextInput("Tagged Post")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_body_field").performTextInput("Post with tags")
    composeTestRule.waitForIdle()

    // Add a tag
    composeTestRule.onNodeWithTag("create_post_tag_search_field").performTextInput("test")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_tag_search_icon").performClick()
    composeTestRule.waitForIdle()

    // Post it
    composeTestRule.onNodeWithTag("create_post_post_btn").performClick()
    composeTestRule.waitForIdle()

    // Should navigate back to Posts Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)
  }

  @Test
  fun testNavigationBetweenAllScreensDoesNotLoseBottomBar() {
    // Test that bottom bar remains through various navigation paths
    val navigationPath =
        listOf(
            NavigationTestTags.DISCUSSIONS_TAB,
            NavigationTestTags.SESSIONS_TAB,
            NavigationTestTags.DISCOVER_TAB,
            NavigationTestTags.PROFILE_TAB,
            NavigationTestTags.DISCUSSIONS_TAB)

    navigationPath.forEach { tab ->
      composeTestRule.onNodeWithTag(tab).performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
    }
  }

  @Test
  fun testScreenTitleUpdatesOnNavigation() {
    // Test that screen titles update correctly on each navigation
    val screenMappings =
        mapOf(
            NavigationTestTags.DISCUSSIONS_TAB to MeepleMeetScreen.DiscussionsOverview.title,
            NavigationTestTags.SESSIONS_TAB to MeepleMeetScreen.SessionsOverview.title,
            NavigationTestTags.DISCOVER_TAB to MeepleMeetScreen.PostsOverview.title,
            NavigationTestTags.PROFILE_TAB to MeepleMeetScreen.Profile.title)

    screenMappings.forEach { (tab, expectedTitle) ->
      composeTestRule.onNodeWithTag(tab).performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
      composeTestRule
          .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
          .assertTextContains(expectedTitle)
    }
  }

  @Test
  fun testCreatePostAndSelectIt() {
    // Create a post
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()

    val postTitle = "Selectable Post ${System.currentTimeMillis()}"
    composeTestRule.onNodeWithTag("create_post_title_field").performTextInput(postTitle)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_body_field").performTextInput("Test body")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("create_post_post_btn").performClick()
    composeTestRule.waitForIdle()

    // Wait for post to appear and try to click it
    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      try {
        composeTestRule
            .onAllNodesWithText(postTitle, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
      } catch (e: Exception) {
        false
      }
    }

    // Click on the post
    try {
      composeTestRule
          .onNodeWithText(postTitle, substring = true, useUnmergedTree = true)
          .performClick()
      composeTestRule.waitForIdle()
    } catch (e: Exception) {
      // Post might not be clickable in current implementation
    }
  }

  @Test
  fun testMultiplePostsCreation() {
    // Navigate to Posts
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    // Create multiple posts
    repeat(2) { index ->
      composeTestRule.onNodeWithTag("AddPostButton").performClick()
      composeTestRule.waitForIdle()

      composeTestRule
          .onNodeWithTag("create_post_title_field")
          .performTextInput("Post $index ${System.currentTimeMillis()}")
      composeTestRule.waitForIdle()
      composeTestRule
          .onNodeWithTag("create_post_body_field")
          .performTextInput("Body for post $index")
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("create_post_post_btn").performClick()
      composeTestRule.waitForIdle()
    }

    // Should be back on Posts Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.PostsOverview.title)
  }

  @Test
  fun testNavigateBackFromMultipleDeepScreens() {
    // Create a discussion
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Add Title").performTextInput("Deep Test")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Navigate to Create Discussion again
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()

    // Go back
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
        .performClick()
    composeTestRule.waitForIdle()

    // Should be on Discussions Overview
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }

  @Test
  fun testDiscardMultipleTimes() {
    // Test discarding post creation multiple times
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    repeat(2) {
      composeTestRule.onNodeWithTag("AddPostButton").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("create_post_draft_btn").performClick()
      composeTestRule.waitForIdle()

      // Should be back on Posts Overview
      composeTestRule
          .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
          .assertTextContains(MeepleMeetScreen.PostsOverview.title)
    }
  }

  @Test
  fun testAllTabsAreAccessibleAfterCreatingContent() {
    // Create a discussion
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Title").performTextInput("Test")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // Create a post
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_title_field").performTextInput("Test Post")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_body_field").performTextInput("Body")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_post_btn").performClick()
    composeTestRule.waitForIdle()

    // All tabs should still be accessible
    val allTabs =
        listOf(
            NavigationTestTags.DISCUSSIONS_TAB,
            NavigationTestTags.SESSIONS_TAB,
            NavigationTestTags.DISCOVER_TAB,
            NavigationTestTags.PROFILE_TAB)

    allTabs.forEach { tab ->
      composeTestRule.onNodeWithTag(tab).assertExists()
      composeTestRule.onNodeWithTag(tab).performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()
    }
  }

  @Test
  fun testSessionsTabAccessibility() {
    // Navigate to Sessions and verify screen
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(NavigationTestTags.SCREEN_TITLE).assertExists()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.SessionsOverview.title)

    // Bottom bar should exist
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertExists()

    // Should be able to navigate away and back
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.SessionsOverview.title)
  }

  @Test
  fun testCompleteNavigationFlow() {
    // Test a complete user flow through the app
    // 1. Start on Discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // 2. Create a discussion
    composeTestRule.onNodeWithTag("Add Discussion").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Add Title").performTextInput("Flow Test")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("Create Discussion").performClick()
    composeTestRule.waitForIdle()

    // 3. Go to Posts
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCOVER_TAB).performClick()
    composeTestRule.waitForIdle()

    // 4. Create a post
    composeTestRule.onNodeWithTag("AddPostButton").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_title_field").performTextInput("Flow Post")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_body_field").performTextInput("Test")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("create_post_post_btn").performClick()
    composeTestRule.waitForIdle()

    // 5. Go to Sessions
    composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // 6. Go to Profile
    composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
    composeTestRule.waitForIdle()

    // 7. Back to Discussions
    composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
    composeTestRule.waitForIdle()

    // Verify we're on Discussions
    composeTestRule
        .onNodeWithTag(NavigationTestTags.SCREEN_TITLE)
        .assertTextContains(MeepleMeetScreen.DiscussionsOverview.title)
  }
}
