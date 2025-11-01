package com.github.meeplemeet.integration

import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
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
}

