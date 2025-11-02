package com.github.meeplemeet.integration

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

class FirestoreRepositoryTests : FirestoreTests() {
  private lateinit var repository: FirestoreRepository
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testAccount3: Account

  @Before
  fun setup() {
    repository = FirestoreRepository()
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

  @Test
  fun createDiscussionCreatesNewDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test Discussion", "Description", testAccount1.uid)

    assertNotNull(discussion.uid)
    assertEquals("Test Discussion", discussion.name)
    assertEquals("Description", discussion.description)
    assertEquals(testAccount1.uid, discussion.creatorId)
    assertTrue(discussion.participants.contains(testAccount1.uid))
    assertTrue(discussion.admins.contains(testAccount1.uid))
    assertTrue(discussion.messages.isEmpty())

    val account = repository.getAccount(testAccount1.uid)
    assertNotNull(account.previews[discussion.uid])
  }

  @Test
  fun getDiscussionRetrievesExistingDiscussion() = runBlocking {
    val created = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val fetched = repository.getDiscussion(created.uid)

    assertEquals(created.uid, fetched.uid)
    assertEquals(created.name, fetched.name)
    assertEquals(created.description, fetched.description)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun getDiscussionThrowsForNonExistentDiscussion() = runTest {
    repository.getDiscussion("nonexistent-id")
  }

  @Test
  fun setDiscussionNameUpdatesName() = runBlocking {
    val discussion = repository.createDiscussion("Old Name", "Desc", testAccount1.uid)

    repository.setDiscussionName(discussion.uid, "New Name")

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals("New Name", updated.name)
    assertEquals(discussion.uid, updated.uid)
  }

  @Test
  fun setDiscussionDescriptionUpdatesDescription() = runBlocking {
    val discussion = repository.createDiscussion("Name", "Old Desc", testAccount1.uid)

    repository.setDiscussionDescription(discussion.uid, "New Description")

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals("New Description", updated.description)
    assertEquals(discussion.uid, updated.uid)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun deleteDiscussionRemovesDiscussion() = runTest {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.deleteDiscussion(discussion)
    repository.getDiscussion(discussion.uid)
  }

  @Test
  fun addUserToDiscussionAddsParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addUserToDiscussion(discussion, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun removeUserFromDiscussionRemovesParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    val withUser = repository.getDiscussion(discussion.uid)
    repository.removeUserFromDiscussion(withUser, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(testAccount2.uid))
  }

  @Test
  fun addUsersToDiscussionAddsMultipleParticipants() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
  }

  @Test
  fun removeUsersFromDiscussionRemovesMultipleParticipants() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val withUsers = repository.getDiscussion(discussion.uid)
    repository.removeUsersFromDiscussion(withUsers, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.participants.contains(testAccount3.uid))
  }

  @Test
  fun addAdminToDiscussionAddsAdminAndParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addAdminToDiscussion(discussion, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun removeAdminFromDiscussionRemovesAdminButKeepsParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addAdminToDiscussion(discussion, testAccount2.uid)

    val withAdmin = repository.getDiscussion(discussion.uid)
    repository.removeAdminFromDiscussion(withAdmin, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun addAdminsToDiscussionAddsMultipleAdmins() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addAdminsToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
    assertTrue(updated.admins.contains(testAccount2.uid))
    assertTrue(updated.admins.contains(testAccount3.uid))
  }

  @Test
  fun removeAdminsFromDiscussionRemovesMultipleAdmins() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addAdminsToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val withAdmins = repository.getDiscussion(discussion.uid)
    repository.removeAdminsFromDiscussion(withAdmins, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount3.uid))
  }

  @Test
  fun sendMessageToDiscussionAppendsMessage() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.sendMessageToDiscussion(discussion, testAccount1, "Hello World")

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(1, updated.messages.size)
    assertEquals("Hello World", updated.messages[0].content)
    assertEquals(testAccount1.uid, updated.messages[0].senderId)
  }

  @Test
  fun createAccountCreatesNewAccount() = runBlocking {
    val account =
        repository.createAccount(
            "TestUser", "TestUser", email = "TestUser@example.com", photoUrl = null)

    assertNotNull(account.uid)
    assertEquals("TestUser", account.name)
    assertTrue(account.previews.isEmpty())
  }

  @Test
  fun getAccountRetrievesExistingAccount() = runBlocking {
    val created =
        repository.createAccount(
            "TestUser", "TestUser", email = "TestUser@example.com", photoUrl = null)

    val fetched = repository.getAccount(created.uid)

    assertEquals(created.uid, fetched.uid)
    assertEquals(created.name, fetched.name)
  }

  @Test(expected = AccountNotFoundException::class)
  fun getAccountThrowsForNonExistentAccount() = runTest { repository.getAccount("nonexistent-id") }

  @Test
  fun setAccountNameUpdatesName() = runBlocking {
    val account =
        repository.createAccount(
            "OldName", "OldName", email = "OldName@example.com", photoUrl = null)

    repository.setAccountName(account.uid, "NewName")

    val updated = repository.getAccount(account.uid)
    assertEquals("NewName", updated.name)
    assertEquals(account.uid, updated.uid)
  }

  @Test(expected = AccountNotFoundException::class)
  fun deleteAccountRemovesAccount() = runTest {
    val account =
        repository.createAccount(
            "TestUser", "TestUser", email = "TestUser@example.com", photoUrl = null)

    repository.deleteAccount(account.uid)
    repository.getAccount(account.uid)
  }

  @Test
  fun readDiscussionMessagesResetsUnreadCount() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    repository.sendMessageToDiscussion(discussion, testAccount1, "Hello")

    val message = Message(testAccount2.uid, "Read")
    repository.readDiscussionMessages(testAccount2.uid, discussion.uid, message)

    val updated = repository.getAccount(testAccount2.uid)
    val preview = updated.previews[discussion.uid]
    assertNotNull(preview)
    assertEquals(0, preview!!.unreadCount)
  }

  @Test
  fun listenDiscussionEmitsUpdates() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val flow = repository.listenDiscussion(discussion.uid)
    val firstEmission = withTimeout(5000) { flow.first() }

    assertEquals(discussion.uid, firstEmission.uid)
    assertEquals("Test", firstEmission.name)
  }

  @Test
  fun listenAccountEmitsPreviewUpdates() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val flow = repository.listenAccount(testAccount1.uid)
    val firstEmission = withTimeout(5000) { flow.first() }

    assertTrue(firstEmission.previews.containsKey(discussion.uid))
    assertNotNull(firstEmission.previews[discussion.uid])
  }

  // Polling Tests

  @Test
  fun createPollCreatesMessageWithPoll() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    val options = listOf("Option 1", "Option 2", "Option 3")
    val question = "What is your favorite?"

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = question,
        options = options,
        allowMultipleVotes = false)

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(1, updated.messages.size)
    val pollMessage = updated.messages[0]
    assertNotNull(pollMessage.poll)
    assertEquals(question, pollMessage.poll?.question)
    assertEquals(options, pollMessage.poll?.options)
    assertFalse(pollMessage.poll?.allowMultipleVotes ?: true)
    assertTrue(pollMessage.poll?.votes?.isEmpty() ?: false)
  }

  @Test
  fun createPollWithMultipleVotesAllowed() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Select all that apply",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = true)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.messages[0].poll?.allowMultipleVotes ?: false)
  }

  @Test
  fun voteOnPollSingleVoteMode() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Pick one",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = false)

    val updatedDiscussion = repository.getDiscussion(discussion.uid)
    val message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 1)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    assertEquals(1, poll?.votes?.size)
    assertEquals(listOf(1), poll?.votes?.get(testAccount2.uid))
  }

  @Test
  fun voteOnPollMultipleVoteMode() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Select all",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = true)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 2)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    assertEquals(1, poll?.votes?.size)
    assertEquals(2, poll?.votes?.get(testAccount2.uid)?.size)
    assertTrue(poll?.votes?.get(testAccount2.uid)?.contains(0) ?: false)
    assertTrue(poll?.votes?.get(testAccount2.uid)?.contains(2) ?: false)
  }

  @Test
  fun voteOnPollSingleVoteReplacesPreviousVote() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Pick one",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = false)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 2)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    assertEquals(listOf(2), poll?.votes?.get(testAccount2.uid))
  }

  @Test
  fun voteOnPollMultipleUsersCanVote() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Vote",
        options = listOf("A", "B"),
        allowMultipleVotes = false)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount1.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 1)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount3.uid, 0)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    assertEquals(3, poll?.votes?.size)
    assertEquals(listOf(0), poll?.votes?.get(testAccount1.uid))
    assertEquals(listOf(1), poll?.votes?.get(testAccount2.uid))
    assertEquals(listOf(0), poll?.votes?.get(testAccount3.uid))
  }

  @Test(expected = IllegalArgumentException::class)
  fun voteOnPollThrowsForInvalidOptionIndex() = runTest {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Pick one",
        options = listOf("A", "B"),
        allowMultipleVotes = false)

    val updatedDiscussion = repository.getDiscussion(discussion.uid)
    val message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 5)
  }

  @Test
  fun removeVoteFromPollRemovesSpecificOption() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Select all",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = true)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 1)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 2)

    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.removeVoteFromPoll(updatedDiscussion, message, testAccount2.uid, 1)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    val userVotes = poll?.votes?.get(testAccount2.uid)
    assertEquals(2, userVotes?.size)
    assertTrue(userVotes?.contains(0) ?: false)
    assertTrue(userVotes?.contains(2) ?: false)
    assertFalse(userVotes?.contains(1) ?: true)
  }

  @Test
  fun removeVoteFromPollRemovesUserIfNoVotesLeft() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Pick one",
        options = listOf("A", "B"),
        allowMultipleVotes = false)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 0)

    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.removeVoteFromPoll(updatedDiscussion, message, testAccount2.uid, 0)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll

    assertNotNull(poll)
    assertTrue(poll?.votes?.isEmpty() ?: false)
    assertFalse(poll?.votes?.containsKey(testAccount2.uid) ?: true)
  }

  @Test(expected = IllegalArgumentException::class)
  fun removeVoteFromPollThrowsIfUserHasNotVoted() = runTest {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Pick one",
        options = listOf("A", "B"),
        allowMultipleVotes = false)

    val updatedDiscussion = repository.getDiscussion(discussion.uid)
    val message = updatedDiscussion.messages[0]
    repository.removeVoteFromPoll(updatedDiscussion, message, testAccount2.uid, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun removeVoteFromPollThrowsIfUserDidNotVoteForThatOption() = runTest {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Select all",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = true)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.removeVoteFromPoll(updatedDiscussion, message, testAccount2.uid, 2)
  }

  @Test
  fun pollVoteCountsAreCorrect() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    repository.createPoll(
        discussion = discussion,
        creatorId = testAccount1.uid,
        question = "Vote",
        options = listOf("A", "B", "C"),
        allowMultipleVotes = false)

    var updatedDiscussion = repository.getDiscussion(discussion.uid)
    var message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount1.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount2.uid, 0)
    updatedDiscussion = repository.getDiscussion(discussion.uid)
    message = updatedDiscussion.messages[0]
    repository.voteOnPoll(updatedDiscussion, message, testAccount3.uid, 1)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    val voteCounts = poll?.getVoteCountsByOption()
    assertEquals(2, voteCounts?.get(0))
    assertEquals(1, voteCounts?.get(1))
    assertEquals(null, voteCounts?.get(2))
    assertEquals(3, poll?.getTotalVotes())
    assertEquals(3, poll?.getTotalVoters())
  }

  // Error Handling Tests

  @Test(expected = IllegalArgumentException::class)
  fun voteOnPollThrowsWhenMessageDoesNotContainPoll() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.sendMessageToDiscussion(discussion, testAccount1, "Regular message")

    val d1 = repository.getDiscussion(discussion.uid)
    val regularMessage = d1.messages[0]

    repository.voteOnPoll(d1, regularMessage, testAccount1.uid, 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun voteOnPollThrowsWhenOptionIndexIsNegative() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]

    repository.voteOnPoll(d1, pollMessage, testAccount1.uid, -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun voteOnPollThrowsWhenOptionIndexTooLarge() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]

    repository.voteOnPoll(d1, pollMessage, testAccount1.uid, 5)
  }

  @Test
  fun voteOnPollDoesNotAddDuplicateVoteInMultipleVoteMode() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C"), true)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]

    // Vote on same option twice
    repository.voteOnPoll(d1, pollMessage, testAccount1.uid, 0)
    val d2 = repository.getDiscussion(discussion.uid)
    repository.voteOnPoll(d2, d2.messages[0], testAccount1.uid, 0) // Same option again

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)
    // Should only have one vote for option 0
    assertEquals(listOf(0), poll!!.votes[testAccount1.uid])
  }

  @Test(expected = IllegalArgumentException::class)
  fun removeVoteFromPollThrowsWhenMessageDoesNotContainPoll() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.sendMessageToDiscussion(discussion, testAccount1, "Regular message")

    val d1 = repository.getDiscussion(discussion.uid)
    val regularMessage = d1.messages[0]

    repository.removeVoteFromPoll(d1, regularMessage, testAccount1.uid, 0)
  }

  // Poll Helper Methods Tests

  @Test
  fun pollGetUserVotesReturnsCorrectVotes() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    repository.createPoll(discussion, testAccount1.uid, "Select all", listOf("A", "B", "C"), true)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]
    repository.voteOnPoll(d1, pollMessage, testAccount1.uid, 0)
    val d2 = repository.getDiscussion(discussion.uid)
    repository.voteOnPoll(d2, d2.messages[0], testAccount1.uid, 2)

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)

    val user1Votes = poll!!.getUserVotes(testAccount1.uid)
    assertNotNull(user1Votes)
    assertEquals(2, user1Votes!!.size)
    assertTrue(user1Votes.contains(0))
    assertTrue(user1Votes.contains(2))

    val user2Votes = poll.getUserVotes(testAccount2.uid)
    assertEquals(null, user2Votes)
  }

  @Test
  fun pollHasUserVotedWorksCorrectly() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    repository.createPoll(discussion, testAccount1.uid, "Question", listOf("A", "B"), false)

    val d1 = repository.getDiscussion(discussion.uid)
    val pollMessage = d1.messages[0]
    repository.voteOnPoll(d1, pollMessage, testAccount1.uid, 0)

    val final = repository.getDiscussion(discussion.uid)
    val poll = final.messages[0].poll
    assertNotNull(poll)

    assertTrue(poll!!.hasUserVoted(testAccount1.uid))
    assertFalse(poll.hasUserVoted(testAccount2.uid))
  }

  // Edge Cases

  @Test
  fun createPollWithEmptyContentWorks() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    val question = "What do you think?"
    val options = listOf("Yes", "No")

    repository.createPoll(discussion, testAccount1.uid, question, options, false)

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(1, updated.messages.size)
    val message = updated.messages[0]
    assertNotNull(message.poll)
    assertEquals(question, message.poll?.question)
    // Content should be set to question
    assertEquals(question, message.content)
  }

  @Test
  fun pollWithManyOptions() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    val options = (1..20).map { "Option $it" }

    repository.createPoll(discussion, testAccount1.uid, "Pick one", options, false)

    val updated = repository.getDiscussion(discussion.uid)
    val poll = updated.messages[0].poll
    assertNotNull(poll)
    assertEquals(20, poll!!.options.size)
  }
}
