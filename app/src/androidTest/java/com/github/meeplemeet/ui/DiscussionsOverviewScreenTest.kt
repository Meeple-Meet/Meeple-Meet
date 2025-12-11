// Class based on DiscussionsIntegrationTest.kt and adapted for DiscussionsOverviewScreen
// Tests were partially done using ChatGPT-5 Thinking Extended and partially done manually
package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.ui.discussions.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscussionsOverviewScreenTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var vm: DiscussionViewModel
  private lateinit var nav: NavigationActions
  private lateinit var navVM: MainActivityViewModel

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
    navVM = MainActivityViewModel()

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
    // Use a fixed start time for deterministic testing
    val baseTime = Timestamp.now()
    var offset = 0L

    // Helper to manually create discussion
    suspend fun createManualDiscussion(
        name: String,
        description: String,
        creatorId: String,
        participants: List<String>
    ): Discussion {
      val ts = Timestamp(baseTime.seconds, baseTime.nanoseconds + (++offset).toInt())
      // Manually create discussion with explicit timestamp
      val discussion =
          Discussion(
              discussionRepository.newUUID(),
              creatorId,
              name,
              description,
              participants + creatorId,
              listOf(creatorId),
              ts,
              null)

      val batch = db.batch()
      batch.set(
          db.collection("discussions").document(discussion.uid),
          com.github.meeplemeet.model.discussions.toNoUid(discussion))
      (participants + creatorId).forEach { id ->
        val ref =
            db.collection("accounts").document(id).collection("previews").document(discussion.uid)
        batch.set(ref, com.github.meeplemeet.model.discussions.DiscussionPreviewNoUid())
      }
      batch.commit().await()
      return discussion
    }

    // Helper to manually send message
    suspend fun sendManualMessage(discussion: Discussion, sender: Account, content: String) {
      val ts = Timestamp(baseTime.seconds, baseTime.nanoseconds + (++offset).toInt())
      val messageNoUid =
          com.github.meeplemeet.model.discussions.MessageNoUid(sender.uid, content, ts)

      val batch = db.batch()
      // Add message to subcollection
      val messageRef =
          db.collection("discussions").document(discussion.uid).collection("messages").document()
      batch.set(messageRef, messageNoUid)

      // Update previews for all participants
      discussion.participants.forEach { userId ->
        val ref =
            db.collection("accounts")
                .document(userId)
                .collection("previews")
                .document(discussion.uid)
        val unreadCountValue =
            if (userId == sender.uid) 0 else com.google.firebase.firestore.FieldValue.increment(1)
        batch.set(
            ref,
            mapOf(
                "lastMessage" to content,
                "lastMessageSender" to sender.uid,
                "lastMessageAt" to ts,
                "unreadCount" to unreadCountValue),
            com.google.firebase.firestore.SetOptions.merge())
      }
      batch.commit().await()
    }

    // Create discussions manually
    d1 =
        createManualDiscussion(
            name = "Catan Crew",
            description = "",
            creatorId = me.uid,
            participants = listOf(bob.uid))

    d2 =
        createManualDiscussion(
            name = "Gloomhaven",
            description = "",
            creatorId = me.uid,
            participants = listOf(bob.uid))

    d3 =
        createManualDiscussion(
            name = "Weekend Plan", description = "", creatorId = me.uid, participants = emptyList())

    // Send messages manually
    sendManualMessage(d1, me, "Bring snacks")
    sendManualMessage(d2, bob, "Ready at 7?")

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
    compose.setContent {
      AppTheme {
        DiscussionsOverviewScreen(
            account = me,
            navigation = nav,
            unreadCount = me.notifications.count { it -> !it.read },
        )
      }
    }

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

  @Test
  fun blockedSender_hidesMessagePreview() = runBlocking {
    // Block Bob
    val meWithBlockedBob =
        me.copy(relationships = me.relationships + (bob.uid to RelationshipStatus.BLOCKED))

    compose.setContent {
      AppTheme {
        DiscussionsOverviewScreen(account = meWithBlockedBob, navigation = nav, unreadCount = 0)
      }
    }

    checkpoint("Blocked sender shows hidden message") {
      compose.waitForIdle()
      compose.onNodeWithText("Gloomhaven").assertIsDisplayed()
      compose.onNodeWithText("Hidden: blocked sender", substring = true).assertIsDisplayed()
      compose.onNodeWithText("Bob: Ready at 7?", substring = true).assertDoesNotExist()
    }

    checkpoint("Non-blocked messages still display") {
      compose.onNodeWithText("Catan Crew").assertIsDisplayed()
      compose.onNodeWithText("You: Bring snacks", substring = true).assertIsDisplayed()
    }
  }
}
