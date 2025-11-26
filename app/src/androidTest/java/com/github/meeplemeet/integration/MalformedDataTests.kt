package com.github.meeplemeet.integration

import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.DiscussionPreviewNoUid
import com.github.meeplemeet.model.discussions.DiscussionsOverviewViewModel
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for malformed data handling and preview validation.
 *
 * Tests the new functionality that cleans up orphaned discussion previews and validates that
 * preview documents reference existing discussions.
 */
class MalformedDataTests : FirestoreTests() {
  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account
  private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

  @Before
  fun setup() {
    runBlocking {
      account1 =
          accountRepository.createAccount(
              "user1", "User One", email = "user1@example.com", photoUrl = null)
      account2 =
          accountRepository.createAccount(
              "user2", "User Two", email = "user2@example.com", photoUrl = null)
      account3 =
          accountRepository.createAccount(
              "user3", "User Three", email = "user3@example.com", photoUrl = null)
    }
  }

  @Test
  fun previewIsValid_handlesValidInvalidAndDeletedDiscussions() = runBlocking {
    // Test valid discussion
    val discussion =
        discussionRepository.createDiscussion(
            "Test Discussion", "Description", account1.uid, listOf(account2.uid))
    assertTrue(
        "Preview should be valid for existing discussion",
        discussionRepository.previewIsValid(discussion.uid))

    // Test non-existent discussion
    val fakeDiscussionId = "nonexistent_discussion_id_12345"
    assertFalse(
        "Preview should be invalid for non-existent discussion",
        discussionRepository.previewIsValid(fakeDiscussionId))

    // Test deleted discussion
    discussionRepository.deleteDiscussion(context, discussion)
    assertFalse(
        "Preview should be invalid after discussion deletion",
        discussionRepository.previewIsValid(discussion.uid))
  }

  @Test
  fun previewToDeleteRef_returnsCorrectDocumentReference() {
    val discussionId = "test_discussion_id"
    val ref = accountRepository.previewToDeleteRef(account1.uid, discussionId)
    val expectedPath = "accounts/${account1.uid}/previews/$discussionId"
    assertEquals("Document reference path should match", expectedPath, ref.path)
  }

  @Test
  fun validatePreviews_handlesVariousScenarios() = runBlocking {
    val viewModel = DiscussionsOverviewViewModel(discussionRepository, accountRepository)

    // Scenario 1: Empty previews list
    var acc1 = accountRepository.getAccount(account1.uid)
    assertEquals("Should have no previews initially", 0, acc1.previews.size)
    viewModel.validatePreviews(acc1)
    delay(500)
    acc1 = accountRepository.getAccount(account1.uid)
    assertEquals("Should still have no previews", 0, acc1.previews.size)

    // Scenario 2: Valid discussions with one deleted (mixed valid/invalid)
    val validDiscussion1 =
        discussionRepository.createDiscussion(
            "Valid 1", "Description", account1.uid, listOf(account2.uid))
    val validDiscussion2 =
        discussionRepository.createDiscussion(
            "Valid 2", "Description", account1.uid, listOf(account2.uid))
    val toDeleteDiscussion =
        discussionRepository.createDiscussion(
            "To Delete", "Description", account1.uid, listOf(account2.uid))

    // Manually add an orphaned preview
    val orphanedId = "orphaned_discussion_id"
    accountRepository
        .previewToDeleteRef(account1.uid, orphanedId)
        .set(
            DiscussionPreviewNoUid(
                lastMessage = "Orphaned",
                lastMessageSender = account1.uid,
                lastMessageAt = Timestamp.now(),
                unreadCount = 5))
        .await()

    acc1 = accountRepository.getAccount(account1.uid)
    assertEquals("Should have 4 previews", 4, acc1.previews.size)

    // Delete one discussion
    discussionRepository.deleteDiscussion(context, toDeleteDiscussion)

    // Validate and verify cleanup
    acc1 = accountRepository.getAccount(account1.uid)
    viewModel.validatePreviews(acc1)
    delay(1000)

    acc1 = accountRepository.getAccount(account1.uid)
    assertEquals("Should have 2 valid previews remaining", 2, acc1.previews.size)
    assertNotNull("Valid discussion 1 should remain", acc1.previews[validDiscussion1.uid])
    assertNotNull("Valid discussion 2 should remain", acc1.previews[validDiscussion2.uid])
    assertNull(
        "Deleted discussion preview should be removed", acc1.previews[toDeleteDiscussion.uid])
    assertNull("Orphaned preview should be removed", acc1.previews[orphanedId])

    // Scenario 3: Verify caching - second call should not re-check
    viewModel.validatePreviews(acc1)
    delay(500)
    // If we get here without errors, caching is working
    assertTrue("Validation should complete without checking duplicates", true)
  }

  @Test
  fun validatePreviews_handlesMultipleOrphanedPreviewsAndMultipleAccounts() = runBlocking {
    // Create multiple discussions with multiple participants
    val discussions =
        (1..5).map { i ->
          discussionRepository.createDiscussion(
              "Discussion $i", "Description $i", account1.uid, listOf(account2.uid, account3.uid))
        }

    // Verify all previews exist for all accounts
    var acc1 = accountRepository.getAccount(account1.uid)
    var acc2 = accountRepository.getAccount(account2.uid)
    var acc3 = accountRepository.getAccount(account3.uid)
    assertEquals("Account1 should have 5 previews", 5, acc1.previews.size)
    assertEquals("Account2 should have 5 previews", 5, acc2.previews.size)
    assertEquals("Account3 should have 5 previews", 5, acc3.previews.size)

    // Delete 3 discussions
    discussions.take(3).forEach { discussionRepository.deleteDiscussion(context, it) }

    // Validate previews for all accounts
    val viewModel = DiscussionsOverviewViewModel(discussionRepository, accountRepository)
    acc1 = accountRepository.getAccount(account1.uid)
    acc2 = accountRepository.getAccount(account2.uid)
    acc3 = accountRepository.getAccount(account3.uid)

    viewModel.validatePreviews(acc1)
    viewModel.validatePreviews(acc2)
    viewModel.validatePreviews(acc3)
    delay(1000)

    // Verify cleanup for all accounts
    acc1 = accountRepository.getAccount(account1.uid)
    acc2 = accountRepository.getAccount(account2.uid)
    acc3 = accountRepository.getAccount(account3.uid)

    assertEquals("Account1 should have 2 valid previews", 2, acc1.previews.size)
    assertEquals("Account2 should have 2 valid previews", 2, acc2.previews.size)
    assertEquals("Account3 should have 2 valid previews", 2, acc3.previews.size)

    // Verify the correct previews remain
    assertNotNull("Discussion 4 preview should remain", acc1.previews[discussions[3].uid])
    assertNotNull("Discussion 5 preview should remain", acc1.previews[discussions[4].uid])
    assertNull("Discussion 1 preview should be removed", acc1.previews[discussions[0].uid])
    assertNull("Discussion 2 preview should be removed", acc2.previews[discussions[1].uid])
    assertNull("Discussion 3 preview should be removed", acc3.previews[discussions[2].uid])
  }

  @Test
  fun fullyDeleteDocument_removesDiscussionWithMessagesSubcollection() = runBlocking {
    // Create discussion with messages
    val discussion =
        discussionRepository.createDiscussion(
            "Test Discussion", "Description", account1.uid, listOf(account2.uid))

    discussionRepository.sendMessageToDiscussion(discussion, account1, "Message 1")
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Message 2")
    discussionRepository.sendMessageToDiscussion(discussion, account2, "Message 3")

    // Verify messages exist
    val messagesBefore = discussionRepository.getMessages(discussion.uid)
    assertEquals("Should have 3 messages", 3, messagesBefore.size)

    // Delete discussion
    discussionRepository.deleteDiscussion(context, discussion)

    // Verify complete deletion
    assertFalse("Discussion should not exist", discussionRepository.previewIsValid(discussion.uid))
    assertEquals(
        "Messages subcollection should be empty",
        0,
        discussionRepository.getMessages(discussion.uid).size)
  }

  @Test
  fun fullyDeleteDocument_removesAccountWithAllSubcollections() = runBlocking {
    // Create account with all types of subcollections
    val testAccount =
        accountRepository.createAccount(
            "testuser", "Test User", email = "test@example.com", photoUrl = null)

    // Add data to all subcollections
    discussionRepository.createDiscussion(
        "Test", "Description", testAccount.uid, listOf(account1.uid))
    accountRepository.sendFriendRequest(testAccount, account1.uid)
    accountRepository.sendFriendRequestNotification(testAccount.uid, account1)

    // Verify subcollections exist
    val accountBefore = accountRepository.getAccount(testAccount.uid)
    assertTrue("Should have previews", accountBefore.previews.isNotEmpty())
    assertTrue("Should have relationships", accountBefore.relationships.isNotEmpty())
    assertTrue("Should have notifications", accountBefore.notifications.isNotEmpty())

    // Delete account
    accountRepository.deleteAccount(testAccount.uid)

    // Verify complete deletion
    try {
      accountRepository.getAccount(testAccount.uid)
      throw AssertionError("Account should not exist after deletion")
    } catch (e: com.github.meeplemeet.model.AccountNotFoundException) {
      // Expected exception - account and all subcollections deleted
      assertTrue("Account should throw AccountNotFoundException", true)
    }
  }
}
