// Test file for PostsOverviewScreen search functionality
package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.posts.PostOverviewViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.posts.FeedsOverviewTestTags
import com.github.meeplemeet.ui.posts.PostsOverviewScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostsOverviewScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var viewModel: PostOverviewViewModel
  private lateinit var nav: NavigationActions
  private lateinit var navVM: MainActivityViewModel
  private lateinit var account: Account

  private lateinit var post1: Post
  private lateinit var post2: Post
  private lateinit var post3: Post

  @Before
  fun setup() = runBlocking {
    viewModel = PostOverviewViewModel()
    nav = mockk(relaxed = true)
      navVM = MainActivityViewModel(inTests = true, accountRepository = accountRepository)


      val uid = "uid_" + UUID.randomUUID().toString().take(8)
    account = accountRepository.createAccount(uid, "Tester", "test@x.com", null)

    // Create test posts
    post1 =
        postRepository.createPost(
            authorId = account.uid,
            title = "Catan Strategy Guide",
            content = "Best strategies for Catan",
            tags = listOf("strategy", "catan"))

    post2 =
        postRepository.createPost(
            authorId = account.uid,
            title = "Gloomhaven Tips",
            content = "Tips for Gloomhaven players",
            tags = listOf("tips", "gloomhaven"))

    post3 =
        postRepository.createPost(
            authorId = account.uid,
            title = "Board Game Night",
            content = "Planning a board game night",
            tags = listOf("planning"))

    compose.setContent {
        AppTheme {
          PostsOverviewScreen(
              viewModel = viewModel, verified = true, navigation = nav, account = account, unreadCount = 0)
        }
      }
  }

  @After
  fun cleanup() = runBlocking {
    try {
      postRepository.deletePost(post1.id)
      postRepository.deletePost(post2.id)
      postRepository.deletePost(post3.id)
      accountRepository.deleteAccount(account.uid)
    } catch (_: Exception) {
      // Ignore cleanup errors
    }
  }

  @Test
  fun search_functionality() = runBlocking {
    checkpoint("Search bar is displayed") {
      compose
          .onNodeWithTag(FeedsOverviewTestTags.SEARCH_TEXT_FIELD)
          .assertExists()
          .assertIsDisplayed()
    }

    checkpoint("Search filters posts by title") {
      compose.waitUntil(2000) { compose.onNodeWithText("Catan Strategy Guide").isDisplayed() }

      compose.onNodeWithTag(FeedsOverviewTestTags.SEARCH_TEXT_FIELD).performTextInput("Catan")
      compose.waitForIdle()

      compose.onNodeWithText("Catan Strategy Guide").assertIsDisplayed()
      compose.onNodeWithText("Gloomhaven Tips").assertDoesNotExist()
      compose.onNodeWithText("Board Game Night").assertDoesNotExist()
    }

    checkpoint("Search is case-insensitive for posts") {
      compose.onNodeWithTag(FeedsOverviewTestTags.SEARCH_TEXT_FIELD).performTextClearance()
      compose.onNodeWithTag(FeedsOverviewTestTags.SEARCH_TEXT_FIELD).performTextInput("gloom")
      compose.waitForIdle()

      compose.onNodeWithText("Gloomhaven Tips").assertIsDisplayed()
      compose.onNodeWithText("Catan Strategy Guide").assertDoesNotExist()
    }

    checkpoint("Clear button works for posts") {
      compose.onNodeWithTag(FeedsOverviewTestTags.SEARCH_CLEAR).assertExists().performClick()
      compose.waitForIdle()

      compose.onNodeWithText("Catan Strategy Guide").assertIsDisplayed()
      compose.onNodeWithText("Gloomhaven Tips").assertIsDisplayed()
      compose.onNodeWithText("Board Game Night").assertIsDisplayed()
    }
  }
}
