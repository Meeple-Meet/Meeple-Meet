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
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class DiscussionsOverviewScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

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
  fun smoke_old_account() = runBlocking {
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
  }

  @Test
  fun smoke_updated_account() = runBlocking {
    vm.sendMessageToDiscussion(d1, me, "Changed plan")
    delay(200)
    discussionRepository.sendMessageToDiscussion(d2, bob, "Updated message")
    delay(200)
    val updatedMe: Account = accountRepository.getAccount(me.uid)
    compose.setContent {
      AppTheme { DiscussionsOverviewScreen(account = updatedMe, navigation = nav) }
    }
    checkpoint("Overview displays no messages") {
      runBlocking {
        compose.onNodeWithText("Weekend Plan").assertIsDisplayed()
        compose.onNodeWithText("(No messages yet)").assertIsDisplayed()
      }
    }
    checkpoint("Overview updates for new message") {
      runBlocking {
        compose.onNodeWithText("You: Changed plan", substring = true).assertIsDisplayed()
      }
    }
    checkpoint("Overview updates discussion order") {
      runBlocking {
        val d2Top = compose.onNodeWithText("Gloomhaven").fetchSemanticsNode().boundsInRoot.top
        val d1Top = compose.onNodeWithText("Catan Crew").fetchSemanticsNode().boundsInRoot.top
        assert(d2Top < d1Top) { "Gloomhaven should appear before Catan Crew" }
      }
    }
  }

  @Test
  fun overview_empty_state_shows_no_discussions_text() = runBlocking {
    checkpoint("Overview empty state") {
      runBlocking {
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
    }
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
}
