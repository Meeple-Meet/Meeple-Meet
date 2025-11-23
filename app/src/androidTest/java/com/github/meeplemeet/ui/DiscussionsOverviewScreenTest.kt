// Class based on DiscussionsIntegrationTest.kt and adapted for DiscussionsOverviewScreen
// Tests were partially done using ChatGPT-5 Thinking Extended and partially done manually
package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.ui.discussions.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionsOverviewScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private fun checkpointSuspend(name: String, block: suspend () -> Unit) =
      ck.ck(name) { runBlocking { block() } }

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
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    try {
      discussionRepository.deleteDiscussion(context, d1)
      discussionRepository.deleteDiscussion(context, d2)
      discussionRepository.deleteDiscussion(context, d3)
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
  fun all_tests() = runBlocking {
    // Set content once at the beginning
    compose.setContent { AppTheme { DiscussionsOverviewScreen(account = me, navigation = nav) } }

    checkpoint("Test setup") {
      assert(me.name == "Marco")
      assert(bob.name == "Bob")
      assert(d1.name == "Catan Crew")
      assert(me.previews.containsKey(d1.uid))
      assert(me.previews.containsKey(d2.uid))
    }

    checkpoint("Overview display") {
      compose.onNodeWithText("Catan Crew").assertIsDisplayed()
      compose.onNodeWithText("Gloomhaven").assertIsDisplayed()

      compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
      compose.onNodeWithText("Bob: Ready at 7?", substring = true).assertIsDisplayed()
    }

    checkpoint("Non empty overview hides message") {
      compose.onNodeWithText("No discussions yet").assertDoesNotExist()
    }

    checkpoint("Overview prefix display for self") {
      compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
    }

    checkpoint("Overview displays no messages") {
      compose.onNodeWithText("Weekend Plan").assertIsDisplayed()
      compose.onNodeWithText("(No messages yet)").assertIsDisplayed()
    }

    checkpoint("overview_displaysProfilePictureInDiscussionCard") {
      // Discussion cards should be displayed
      // The ProfilePicture composable is used in DiscussionCard
      // We verify that the discussions are displayed, which includes their profile pictures
      compose.onNodeWithText("Catan Crew").assertIsDisplayed()
      compose.onNodeWithText("Gloomhaven").assertIsDisplayed()

      // Note: We can't directly test the ProfilePicture composable rendering
      // but we verify the cards render without crashing with the new profilePictureUrl
      // parameter
    }

    checkpoint("overview_handlesNullProfilePictureUrl") {
      // All test discussions have null profilePictureUrl by default
      compose.waitForIdle()

      // Verify discussions are displayed with default profile picture behavior
      compose.onNodeWithText("Catan Crew").assertIsDisplayed()
      compose.onNodeWithText("Gloomhaven").assertIsDisplayed()
      compose.onNodeWithText("Weekend Plan").assertIsDisplayed()

      // All cards should render successfully even with null profilePictureUrl
    }

    checkpoint("overview_usesDiscussionCommonsConstants") {
      // The changes replaced local constants with DiscussionCommons constants
      // Verify "You" prefix for own messages
      compose.waitForIdle()

      // Should show "You: Bring snacks" using DiscussionCommons.YOU_SENDER_NAME
      compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()

      // Verify "(No messages yet)" text for discussion without messages uses
      // DiscussionCommons.NO_MESSAGES_DEFAULT_TEXT
      compose.onNodeWithText("Weekend Plan").assertIsDisplayed()
      compose.onNodeWithText("(No messages yet)").assertIsDisplayed()
    }
  }
}
