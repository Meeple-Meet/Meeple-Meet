// Class based on DiscussionsIntegrationTest.kt and adapted for DiscussionsOverviewScreen
// Tests were partially done using ChatGPT-5 Thinking Extended and partially done manually
package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.ui.discussions.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class DiscussionsOverviewScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  private lateinit var vm: DiscussionViewModel
  private lateinit var nav: NavigationActions

  private lateinit var me: Account
  private lateinit var bob: Account
  private lateinit var zoe: Account

  private lateinit var d1: Discussion
  private lateinit var d2: Discussion
  private lateinit var d3: Discussion

  @Before
  fun setup() = runBlocking {
    vm = DiscussionViewModel()
    nav = mockk(relaxed = true)

    // Create test users using repository
    me =
        accountRepository.createAccount(
            userHandle = "marco_${System.currentTimeMillis()}",
            name = "Marco",
            email = "test_marco@epfl.ch",
            photoUrl = null)

    bob =
        accountRepository.createAccount(
            userHandle = "bob_${System.currentTimeMillis()}",
            name = "Bob",
            email = "test_bob@epfl.ch",
            photoUrl = null)

    zoe =
        accountRepository.createAccount(
            userHandle = "zoe_${System.currentTimeMillis()}",
            name = "Zoe",
            email = "test_zoe@epfl.ch",
            photoUrl = null)

    // Create discussions using repository
    d1 =
        discussionRepository.createDiscussion(
            name = "Catan Crew",
            description = "",
            creatorId = me.uid,
            participants = listOf(bob.uid))

    delay(50)
    d2 =
        discussionRepository.createDiscussion(
            name = "Gloomhaven",
            description = "",
            creatorId = me.uid,
            participants = listOf(bob.uid))

    delay(50)
    d3 =
        discussionRepository.createDiscussion(
            name = "Weekend Plan", description = "", creatorId = me.uid, participants = emptyList())

    // Send messages using repository to create previews
    discussionRepository.sendMessageToDiscussion(d1, me, "Bring snacks")
    delay(100)
    discussionRepository.sendMessageToDiscussion(d2, bob, "Ready at 7?")

    // Fetch updated accounts and discussions from repository
    me = accountRepository.getAccount(me.uid)
    bob = accountRepository.getAccount(bob.uid)
    zoe = accountRepository.getAccount(zoe.uid)

    d1 = discussionRepository.getDiscussion(d1.uid)
    d2 = discussionRepository.getDiscussion(d2.uid)
    d3 = discussionRepository.getDiscussion(d3.uid)
  }

  @After
  fun cleanup() = runBlocking {
    try {
      discussionRepository.deleteDiscussion(d1)
      discussionRepository.deleteDiscussion(d2)
      discussionRepository.deleteDiscussion(d3)
      accountRepository.deleteAccount(me.uid)
      accountRepository.deleteAccount(bob.uid)
      accountRepository.deleteAccount(zoe.uid)
    } catch (_: Exception) {
      // Ignore cleanup errors
    }
  }

  /* ================================================================
   * Tests
   * ================================================================ */

  @Test
  fun should_pass_test_setup() {
    compose.setContent { AppTheme { DiscussionsOverviewScreen(account = me, navigation = nav) } }
    assert(me.name == "Marco")
    assert(bob.name == "Bob")
    assert(d1.name == "Catan Crew")
    assert(me.previews.containsKey(d1.uid))
    assert(me.previews.containsKey(d2.uid))
  }

  @Test
  fun overview_shows_discussion_cards_with_names_and_messages() {
    compose.setContent { AppTheme { DiscussionsOverviewScreen(account = me, navigation = nav) } }
    compose.waitForIdle()

    compose.onNodeWithText("Catan Crew").assertIsDisplayed()
    compose.onNodeWithText("Gloomhaven").assertIsDisplayed()

    compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
    compose.onNodeWithText("Bob: Ready at 7?", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_empty_state_shows_no_discussions_text() = runBlocking {
    // Create a new user with no discussions
    val emptyUser =
        accountRepository.createAccount(
            userHandle = "empty_${System.currentTimeMillis()}",
            name = "Empty",
            email = "empty@test.com",
            photoUrl = null)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(account = emptyUser, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertIsDisplayed()

    // Cleanup
    accountRepository.deleteAccount(emptyUser.uid)
  }

  @Test
  fun overview_non_empty_state_hides_empty_message() {
    compose.setContent { AppTheme { DiscussionsOverviewScreen(account = me, navigation = nav) } }
    compose.waitForIdle()
    compose.onNodeWithText("No discussions yet").assertDoesNotExist()
  }

  @Test
  fun overview_renders_no_messages_placeholder_when_discussion_has_no_messages() = runTest {
    // Fetch the account to get updated preview info
    val updatedMe = accountRepository.getAccount(me.uid)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(account = updatedMe, navigation = nav) }
    }
    compose.waitForIdle()

    // d3 (Weekend Plan) has no messages
    compose.onNodeWithText("Weekend Plan").assertIsDisplayed()
    compose.onNodeWithText("(No messages yet)").assertIsDisplayed()
  }

  @Ignore("Random behavior on GitHub CLI")
  @Test
  fun overview_prefixes_non_me_sender_name_when_known() = runBlocking {
    // Create a new discussion with zoe
    val d4 =
        discussionRepository.createDiscussion(
            name = "New Chat", description = "", creatorId = me.uid, participants = listOf(zoe.uid))

    // Send a message from zoe
    discussionRepository.sendMessageToDiscussion(d4, zoe, "See you!")

    // Fetch updated account
    val updatedMe = accountRepository.getAccount(me.uid)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(account = updatedMe, navigation = nav) }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Zoe: See you!", substring = true).assertIsDisplayed()
    compose.onNodeWithText("New Chat").assertIsDisplayed()

    // Cleanup
    discussionRepository.deleteDiscussion(d4)
  }

  @Test
  fun overview_shows_you_prefix_for_own_messages() {
    compose.setContent { AppTheme { DiscussionsOverviewScreen(account = me, navigation = nav) } }
    compose.waitForIdle()

    // d1 has last message from me
    compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_updates_when_new_message_sent() = runTest {
    // Send a new message using ViewModel API
    vm.sendMessageToDiscussion(d1, me, "Changed plan")
    delay(500) // Wait for Firebase update

    // Fetch updated account
    val updatedMe = accountRepository.getAccount(me.uid)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(account = updatedMe, navigation = nav) }
    }
    compose.waitForIdle()

    compose.onNodeWithText("You: Changed plan", substring = true).assertIsDisplayed()
  }

  @Test
  fun overview_sorts_cards_by_latest_message_time() = runBlocking {
    // Send a new message to d2 to make it more recent
    delay(200)
    discussionRepository.sendMessageToDiscussion(d2, bob, "Updated message")
    delay(200)

    // Fetch updated account
    val updatedMe = accountRepository.getAccount(me.uid)

    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(account = updatedMe, navigation = nav) }
    }
    compose.waitForIdle()

    // d2 should now appear before d1 since it has a more recent message
    val d2Top = compose.onNodeWithText("Gloomhaven").fetchSemanticsNode().boundsInRoot.top
    val d1Top = compose.onNodeWithText("Catan Crew").fetchSemanticsNode().boundsInRoot.top
    assert(d2Top < d1Top) { "Gloomhaven should appear before Catan Crew" }
  }

  @Test
  fun overview_displays_all_discussions_with_previews() {
    compose.setContent { AppTheme { DiscussionsOverviewScreen(account = me, navigation = nav) } }
    compose.waitForIdle()

    // All three discussions should be visible
    compose.onNodeWithText("Catan Crew").assertIsDisplayed()
    compose.onNodeWithText("Gloomhaven").assertIsDisplayed()
    compose.onNodeWithText("Weekend Plan").assertIsDisplayed()
  }
}
