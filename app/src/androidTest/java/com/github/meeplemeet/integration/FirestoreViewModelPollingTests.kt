package com.github.meeplemeet.integration

import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
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
  private lateinit var viewModel: FirestoreViewModel
  private lateinit var repository: FirestoreRepository
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testAccount3: Account

  @Before
  fun setup() {
    repository = FirestoreRepository()
    viewModel = FirestoreViewModel(repository)
    runBlocking {
      testAccount1 =
          repository.createAccount("Alice", "Alice", email = "Alice@example.com", photoUrl = null)
      testAccount2 =
          repository.createAccount("Bob", "Bob", email = "Bob@example.com", photoUrl = null)
      testAccount3 =
          repository.createAccount(
              "Charlie", "Charlie", email = "Charlie@example.com", photoUrl = null)
    }
  }

  // Tests for createPoll() - covers validation and repository call
  @Test
  fun createPollCreatesMessageWithPoll() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    val question = "What's your favorite color?"
    val options = listOf("Red", "Blue", "Green")

    viewModel.createPoll(discussion, testAccount1.uid, question, options, false)
    delay(500)

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(1, updated.messages.size)
    assertNotNull(updated.messages[0].poll)
    assertEquals(question, updated.messages[0].poll?.question)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createPollThrowsOnBlankQuestion() {
    val discussion = runBlocking { repository.createDiscussion("Test", "Desc", testAccount1.uid) }
    viewModel.createPoll(discussion, testAccount1.uid, "   ", listOf("A", "B"), false)
  }

  @Test(expected = IllegalArgumentException::class)
  fun createPollThrowsOnInsufficientOptions() {
    val discussion = runBlocking { repository.createDiscussion("Test", "Desc", testAccount1.uid) }
    viewModel.createPoll(discussion, testAccount1.uid, "Question?", listOf("Only One"), false)
  }

  // Tests for voteOnPoll() - covers repository call
  @Test
  fun voteOnPollAddsSingleVote() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)
    delay(500)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]

    viewModel.voteOnPoll(d1, pollMessage, testAccount1, 0)
    delay(500)

    val afterVote = repository.getDiscussion(discussion.uid)
    val poll = afterVote.messages[0].poll
    assertNotNull(poll)
    assertTrue(poll!!.votes.containsKey(testAccount1.uid))
    assertEquals(listOf(0), poll.votes[testAccount1.uid])
  }

  // Tests for removeVoteFromPoll() - covers repository call
  @Test
  fun removeVoteFromPollRemovesSpecificVote() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)
    delay(500)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]
    viewModel.voteOnPoll(d1, pollMessage, testAccount1, 0)
    delay(500)

    val d2 = repository.getDiscussion(discussion.uid)
    val updatedMessage = d2.messages[0]
    viewModel.removeVoteFromPoll(d2, updatedMessage, testAccount1, 0)
    delay(500)

    val afterRemoval = repository.getDiscussion(discussion.uid)
    val poll = afterRemoval.messages[0].poll
    assertNotNull(poll)
    assertTrue(poll!!.votes[testAccount1.uid]?.isEmpty() ?: true)
  }

  // Additional edge cases and validation tests

  @Test(expected = IllegalArgumentException::class)
  fun createPollThrowsOnEmptyQuestion() {
    val discussion = runBlocking { repository.createDiscussion("Test", "Desc", testAccount1.uid) }
    viewModel.createPoll(discussion, testAccount1.uid, "", listOf("A", "B"), false)
  }

  @Test
  fun createPollWithMultipleVotesAllowedWorks() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    viewModel.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C"), true)
    delay(500)

    val updated = repository.getDiscussion(discussion.uid)
    assertNotNull(updated.messages[0].poll)
    assertTrue(updated.messages[0].poll?.allowMultipleVotes ?: false)
  }

  @Test
  fun createPollWithDefaultMultipleVotesParameter() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    // Don't specify allowMultipleVotes (should default to false)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"))
    delay(500)

    val updated = repository.getDiscussion(discussion.uid)
    assertNotNull(updated.messages[0].poll)
    assertFalse(updated.messages[0].poll?.allowMultipleVotes ?: true)
  }

  @Test
  fun voteOnPollWithMultipleUsers() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B", "C"), false)
    delay(500)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]

    viewModel.voteOnPoll(d1, pollMessage, testAccount1, 0)
    delay(500)
    val d2 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d2, d2.messages[0], testAccount2, 1)
    delay(500)

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)
    assertTrue(poll!!.hasUserVoted(testAccount1.uid))
    assertTrue(poll.hasUserVoted(testAccount2.uid))
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 0))
    assertTrue(poll.hasUserVotedFor(testAccount2.uid, 1))
  }

  @Test
  fun voteOnPollMultipleTimesInSingleVoteMode() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B", "C"), false)
    delay(500)

    val d1 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d1, d1.messages[0], testAccount1, 0)
    delay(500)

    val d2 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d2, d2.messages[0], testAccount1, 2)
    delay(500)

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)
    // Should only have vote for option 2 (replaces previous vote)
    assertEquals(listOf(2), poll!!.votes[testAccount1.uid])
  }

  @Test
  fun voteOnPollMultipleTimesInMultipleVoteMode() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C", "D"), true)
    delay(500)

    val d1 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d1, d1.messages[0], testAccount1, 0)
    delay(500)

    val d2 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d2, d2.messages[0], testAccount1, 2)
    delay(500)

    val d3 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d3, d3.messages[0], testAccount1, 3)
    delay(500)

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)
    assertEquals(3, poll!!.votes[testAccount1.uid]?.size)
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 0))
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 2))
    assertTrue(poll.hasUserVotedFor(testAccount1.uid, 3))
  }

  @Test
  fun removeVoteFromPollWithMultipleVotes() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    viewModel.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C"), true)
    delay(500)

    val d1 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d1, d1.messages[0], testAccount1, 0)
    delay(500)
    val d2 = repository.getDiscussion(discussion.uid)
    viewModel.voteOnPoll(d2, d2.messages[0], testAccount1, 1)
    delay(500)

    val d3 = repository.getDiscussion(discussion.uid)
    viewModel.removeVoteFromPoll(d3, d3.messages[0], testAccount1, 0)
    delay(500)

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)
    assertEquals(listOf(1), poll!!.votes[testAccount1.uid])
  }
}
