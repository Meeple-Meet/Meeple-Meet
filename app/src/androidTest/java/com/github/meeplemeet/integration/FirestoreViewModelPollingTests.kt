package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class FirestoreViewModelPollingTests : FirestoreTests() {
  private lateinit var viewModel: DiscussionViewModel
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testAccount3: Account

  @Before
  fun setup() {
    viewModel = DiscussionViewModel()
    runBlocking {
      testAccount1 =
          accountRepository.createAccount(
              "Alice", "Alice", email = "Alice@example.com", photoUrl = null)
      testAccount2 =
          accountRepository.createAccount("Bob", "Bob", email = "Bob@example.com", photoUrl = null)
      testAccount3 =
          accountRepository.createAccount(
              "Charlie", "Charlie", email = "Charlie@example.com", photoUrl = null)
    }
  }

  // Tests for createPoll() - covers validation and repository call
  @Test
  fun createPollCreatesMessageWithPoll() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    val question = "What's your favorite color?"
    val options = listOf("Red", "Blue", "Green")

    viewModel.createPoll(discussion, testAccount1.uid, question, options, false)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    assertEquals(1, messages.size)
    assertNotNull(messages[0].poll)
    assertEquals(question, messages[0].poll?.question)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createPollThrowsOnBlankQuestion() {
    val discussion = runBlocking {
      discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    }
    viewModel.createPoll(discussion, testAccount1.uid, "   ", listOf("A", "B"), false)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createPollThrowsOnInsufficientOptions() {
    val discussion = runBlocking {
      discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    }
    viewModel.createPoll(discussion, testAccount1.uid, "Question?", listOf("Only One"), false)
  }

  // Tests for voteOnPoll() - covers repository call
  @Test
  fun voteOnPollAddsSingleVote() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    val pollMessage = messages[0]

    viewModel.voteOnPoll(discussion.uid, pollMessage.uid, testAccount1, 0)
    delay(500)

    val afterVoteMessages = discussionRepository.getMessages(discussion.uid)
    val poll = afterVoteMessages[0].poll
    assertNotNull(poll)
    assertTrue(poll!!.votes.containsKey(testAccount1.uid))
    assertEquals(listOf(0), poll.votes[testAccount1.uid])
  }

  // Tests for removeVoteFromPoll() - covers repository call
  @Test
  fun removeVoteFromPollRemovesSpecificVote() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    val pollMessage = messages[0]
    viewModel.voteOnPoll(discussion.uid, pollMessage.uid, testAccount1, 0)
    delay(500)

    viewModel.removeVoteFromPoll(discussion.uid, pollMessage.uid, testAccount1, 0)
    delay(500)

    val afterRemovalMessages = discussionRepository.getMessages(discussion.uid)
    val poll = afterRemovalMessages[0].poll
    assertNotNull(poll)
    assertTrue(poll!!.votes[testAccount1.uid]?.isEmpty() ?: true)
  }

  // Additional edge cases and validation tests

  @Test(expected = IllegalArgumentException::class)
  fun createPollThrowsOnEmptyQuestion() {
    val discussion = runBlocking {
      discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    }
    viewModel.createPoll(discussion, testAccount1.uid, "", listOf("A", "B"), false)
  }

  @Test
  fun createPollWithMultipleVotesAllowedWorks() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)

    viewModel.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C"), true)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    assertNotNull(messages[0].poll)
    assertTrue(messages[0].poll?.allowMultipleVotes ?: false)
  }

  @Test
  fun createPollWithDefaultMultipleVotesParameter() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)

    // Don't specify allowMultipleVotes (should default to false)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"))
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    assertNotNull(messages[0].poll)
    assertFalse(messages[0].poll?.allowMultipleVotes ?: true)
  }

  @Test
  fun voteOnPollWithMultipleUsers() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    discussionRepository.addUserToDiscussion(discussion, testAccount2.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B", "C"), false)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    val pollMessageId = messages[0].uid

    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 0)
    delay(500)
    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount2, 1)
    delay(500)

    val finalMessages = discussionRepository.getMessages(discussion.uid)
    val poll = finalMessages[0].poll
    assertNotNull(poll)
    assertTrue(poll!!.hasUserVoted(testAccount1.uid))
    assertTrue(poll.hasUserVoted(testAccount2.uid))
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 0))
    assertTrue(poll.hasUserVotedFor(testAccount2.uid, 1))
  }

  @Test
  fun voteOnPollMultipleTimesInSingleVoteMode() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B", "C"), false)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    val pollMessageId = messages[0].uid
    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 0)
    delay(500)

    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 2)
    delay(500)

    val finalMessages = discussionRepository.getMessages(discussion.uid)
    val poll = finalMessages[0].poll
    assertNotNull(poll)
    // Should only have vote for option 2 (replaces previous vote)
    assertEquals(listOf(2), poll!!.votes[testAccount1.uid])
  }

  @Test
  fun voteOnPollMultipleTimesInMultipleVoteMode() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(
        discussion, testAccount1.uid, "Select all", listOf("A", "B", "C", "D"), true)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    val pollMessageId = messages[0].uid
    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 0)
    delay(500)

    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 2)
    delay(500)

    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 3)
    delay(500)

    val finalMessages = discussionRepository.getMessages(discussion.uid)
    val poll = finalMessages[0].poll
    assertNotNull(poll)
    assertEquals(3, poll!!.votes[testAccount1.uid]?.size)
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 0))
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 2))
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 3))
  }

  @Test
  fun removeVoteFromPollWithMultipleVotes() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C"), true)
    delay(500)

    val messages = discussionRepository.getMessages(discussion.uid)
    val pollMessageId = messages[0].uid
    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 0)
    delay(500)
    viewModel.voteOnPoll(discussion.uid, pollMessageId, testAccount1, 1)
    delay(500)

    viewModel.removeVoteFromPoll(discussion.uid, pollMessageId, testAccount1, 0)
    delay(500)

    val finalMessages = discussionRepository.getMessages(discussion.uid)
    val poll = finalMessages[0].poll
    assertNotNull(poll)
    assertEquals(listOf(1), poll!!.votes[testAccount1.uid])
  }
}
