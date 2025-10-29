package com.github.meeplemeet.ui
// Github Copilot used for this file
import com.github.meeplemeet.model.structures.Poll
import org.junit.Assert
import org.junit.Test

/** Unit tests for the Poll data class and its helper functions. */
class PollTest {

  // ==================== Helper Function Tests ====================

  @Test
  fun `getUsersWhoVotedFor returns correct users for single option`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes = mapOf("user1" to listOf(0), "user2" to listOf(1), "user3" to listOf(0)))

    val usersForOptionA = poll.getUsersWhoVotedFor(0)
    Assert.assertEquals(2, usersForOptionA.size)
    Assert.assertTrue(usersForOptionA.contains("user1"))
    Assert.assertTrue(usersForOptionA.contains("user3"))

    val usersForOptionB = poll.getUsersWhoVotedFor(1)
    Assert.assertEquals(1, usersForOptionB.size)
    Assert.assertTrue(usersForOptionB.contains("user2"))
  }

  @Test
  fun `getUsersWhoVotedFor returns correct users for multiple votes`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes = mapOf("user1" to listOf(0, 2), "user2" to listOf(0, 1), "user3" to listOf(2)),
            allowMultipleVotes = true)

    val usersForOptionA = poll.getUsersWhoVotedFor(0)
    Assert.assertEquals(2, usersForOptionA.size)
    Assert.assertTrue(usersForOptionA.containsAll(listOf("user1", "user2")))

    val usersForOptionC = poll.getUsersWhoVotedFor(2)
    Assert.assertEquals(2, usersForOptionC.size)
    Assert.assertTrue(usersForOptionC.containsAll(listOf("user1", "user3")))
  }

  @Test
  fun `getUsersWhoVotedFor returns empty list when no votes`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"), votes = emptyMap())

    val users = poll.getUsersWhoVotedFor(0)
    Assert.assertTrue(users.isEmpty())
  }

  @Test
  fun `getVoteCountsByOption returns correct counts for single votes`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes =
                mapOf(
                    "user1" to listOf(0),
                    "user2" to listOf(0),
                    "user3" to listOf(1),
                    "user4" to listOf(2)))

    val counts = poll.getVoteCountsByOption()
    Assert.assertEquals(3, counts.size)
    Assert.assertEquals(2, counts[0])
    Assert.assertEquals(1, counts[1])
    Assert.assertEquals(1, counts[2])
  }

  @Test
  fun `getVoteCountsByOption returns correct counts for multiple votes`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes =
                mapOf("user1" to listOf(0, 1), "user2" to listOf(0, 2), "user3" to listOf(1, 2)),
            allowMultipleVotes = true)

    val counts = poll.getVoteCountsByOption()
    Assert.assertEquals(3, counts.size)
    Assert.assertEquals(2, counts[0]) // Options 0: 2 votes
    Assert.assertEquals(2, counts[1]) // Option 1: 2 votes
    Assert.assertEquals(2, counts[2]) // Option 2: 2 votes
  }

  @Test
  fun `getVoteCountsByOption returns empty map when no votes`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"), votes = emptyMap())

    val counts = poll.getVoteCountsByOption()
    Assert.assertTrue(counts.isEmpty())
  }

  @Test
  fun `getTotalVotes counts all individual selections`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes =
                mapOf(
                    "user1" to listOf(0, 1, 2), // 3 votes
                    "user2" to listOf(0), // 1 vote
                    "user3" to listOf(1, 2) // 2 votes
                    ),
            allowMultipleVotes = true)

    Assert.assertEquals(6, poll.getTotalVotes()) // Total: 3 + 1 + 2 = 6
  }

  @Test
  fun `getTotalVotes returns zero when no votes`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"), votes = emptyMap())

    Assert.assertEquals(0, poll.getTotalVotes())
  }

  @Test
  fun `getTotalVoters counts number of users who voted`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes =
                mapOf(
                    "user1" to listOf(0, 1, 2), // 1 voter with 3 votes
                    "user2" to listOf(0), // 1 voter
                    "user3" to listOf(1, 2) // 1 voter
                    ),
            allowMultipleVotes = true)

    Assert.assertEquals(3, poll.getTotalVoters()) // 3 users voted
  }

  @Test
  fun `getTotalVoters returns zero when no votes`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"), votes = emptyMap())

    Assert.assertEquals(0, poll.getTotalVoters())
  }

  @Test
  fun `getUserVotes returns correct vote list`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes = mapOf("user1" to listOf(0, 2), "user2" to listOf(1)),
            allowMultipleVotes = true)

    val user1Votes = poll.getUserVotes("user1")
    Assert.assertNotNull(user1Votes)
    Assert.assertEquals(2, user1Votes?.size)
    Assert.assertTrue(user1Votes?.containsAll(listOf(0, 2)) ?: false)

    val user2Votes = poll.getUserVotes("user2")
    Assert.assertNotNull(user2Votes)
    Assert.assertEquals(1, user2Votes?.size)
    Assert.assertEquals(1, user2Votes?.get(0))
  }

  @Test
  fun `getUserVotes returns null when user has not voted`() {
    val poll =
        Poll(question = "Test?", options = listOf("A", "B"), votes = mapOf("user1" to listOf(0)))

    val votes = poll.getUserVotes("user2")
    Assert.assertNull(votes)
  }

  @Test
  fun `hasUserVoted returns true when user has voted`() {
    val poll =
        Poll(question = "Test?", options = listOf("A", "B"), votes = mapOf("user1" to listOf(0)))

    Assert.assertTrue(poll.hasUserVoted("user1"))
  }

  @Test
  fun `hasUserVoted returns false when user has not voted`() {
    val poll =
        Poll(question = "Test?", options = listOf("A", "B"), votes = mapOf("user1" to listOf(0)))

    Assert.assertFalse(poll.hasUserVoted("user2"))
  }

  @Test
  fun `hasUserVotedFor returns true when user voted for specific option`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes = mapOf("user1" to listOf(0, 2)),
            allowMultipleVotes = true)

    Assert.assertTrue(poll.hasUserVotedFor("user1", 0))
    Assert.assertTrue(poll.hasUserVotedFor("user1", 2))
    Assert.assertFalse(poll.hasUserVotedFor("user1", 1))
  }

  @Test
  fun `hasUserVotedFor returns false when user has not voted at all`() {
    val poll =
        Poll(question = "Test?", options = listOf("A", "B"), votes = mapOf("user1" to listOf(0)))

    Assert.assertFalse(poll.hasUserVotedFor("user2", 0))
  }

  // ==================== Vote Behavior Tests ====================

  @Test
  fun `single vote mode stores one vote per user`() {
    val poll =
        Poll(
            question = "What game?",
            options = listOf("Catan", "Pandemic", "Dominion"),
            votes = mapOf("user1" to listOf(0)),
            allowMultipleVotes = false)

    // Simulate vote replacement (should be handled by backend)
    val updatedVotes = poll.votes.toMutableMap()
    updatedVotes["user1"] = listOf(1) // Replace vote

    val updatedPoll = poll.copy(votes = updatedVotes)

    Assert.assertEquals(1, updatedPoll.getUserVotes("user1")?.size)
    Assert.assertEquals(1, updatedPoll.getUserVotes("user1")?.get(0))
  }

  @Test
  fun `multiple vote mode allows multiple votes per user`() {
    val poll =
        Poll(
            question = "Which games do you own?",
            options = listOf("Catan", "Pandemic", "Dominion"),
            votes = mapOf("user1" to listOf(0)),
            allowMultipleVotes = true)

    // Simulate adding another vote (should be handled by backend)
    val updatedVotes = poll.votes.toMutableMap()
    val currentVotes = updatedVotes["user1"]?.toMutableList() ?: mutableListOf()
    currentVotes.add(2) // Add vote for Dominion
    updatedVotes["user1"] = currentVotes

    val updatedPoll = poll.copy(votes = updatedVotes)

    Assert.assertEquals(2, updatedPoll.getUserVotes("user1")?.size)
    Assert.assertTrue(updatedPoll.getUserVotes("user1")?.containsAll(listOf(0, 2)) ?: false)
  }

  @Test
  fun `removing vote from single-vote user removes them from votes map`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B"),
            votes = mapOf("user1" to listOf(0)),
            allowMultipleVotes = false)

    // Simulate removing vote
    val updatedVotes = poll.votes.toMutableMap()
    updatedVotes.remove("user1")

    val updatedPoll = poll.copy(votes = updatedVotes)

    Assert.assertFalse(updatedPoll.hasUserVoted("user1"))
    Assert.assertNull(updatedPoll.getUserVotes("user1"))
  }

  @Test
  fun `removing specific vote from multiple-vote user keeps other votes`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C"),
            votes = mapOf("user1" to listOf(0, 1, 2)),
            allowMultipleVotes = true)

    // Simulate removing one vote
    val updatedVotes = poll.votes.toMutableMap()
    val currentVotes = updatedVotes["user1"]?.toMutableList() ?: mutableListOf()
    currentVotes.remove(1) // Remove option 1
    updatedVotes["user1"] = currentVotes

    val updatedPoll = poll.copy(votes = updatedVotes)

    Assert.assertEquals(2, updatedPoll.getUserVotes("user1")?.size)
    Assert.assertTrue(updatedPoll.getUserVotes("user1")?.containsAll(listOf(0, 2)) ?: false)
    Assert.assertFalse(updatedPoll.hasUserVotedFor("user1", 1))
  }

  @Test
  fun `removing last vote from multiple-vote user removes them from votes map`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B"),
            votes = mapOf("user1" to listOf(0)),
            allowMultipleVotes = true)

    // Simulate removing last vote
    val updatedVotes = poll.votes.toMutableMap()
    val currentVotes = updatedVotes["user1"]?.toMutableList() ?: mutableListOf()
    currentVotes.remove(0)

    if (currentVotes.isEmpty()) {
      updatedVotes.remove("user1")
    } else {
      updatedVotes["user1"] = currentVotes
    }

    val updatedPoll = poll.copy(votes = updatedVotes)

    Assert.assertFalse(updatedPoll.hasUserVoted("user1"))
  }

  // ==================== Edge Cases ====================

  @Test
  fun `poll with no options has no votes`() {
    val poll = Poll(question = "Empty poll?", options = emptyList(), votes = emptyMap())

    Assert.assertEquals(0, poll.getTotalVotes())
    Assert.assertEquals(0, poll.getTotalVoters())
    Assert.assertTrue(poll.getVoteCountsByOption().isEmpty())
  }

  @Test
  fun `poll handles many users voting for same option`() {
    val votes = (1..100).associate { "user$it" to listOf(0) }
    val poll = Poll(question = "Popular option?", options = listOf("A", "B"), votes = votes)

    Assert.assertEquals(100, poll.getUsersWhoVotedFor(0).size)
    Assert.assertEquals(100, poll.getTotalVoters())
    Assert.assertEquals(100, poll.getVoteCountsByOption()[0])
  }

  @Test
  fun `poll handles user voting for all options in multiple-vote mode`() {
    val poll =
        Poll(
            question = "Test?",
            options = listOf("A", "B", "C", "D"),
            votes = mapOf("user1" to listOf(0, 1, 2, 3)),
            allowMultipleVotes = true)

    Assert.assertEquals(4, poll.getUserVotes("user1")?.size)
    Assert.assertEquals(4, poll.getTotalVotes())
    Assert.assertTrue(poll.hasUserVotedFor("user1", 0))
    Assert.assertTrue(poll.hasUserVotedFor("user1", 1))
    Assert.assertTrue(poll.hasUserVotedFor("user1", 2))
    Assert.assertTrue(poll.hasUserVotedFor("user1", 3))
  }

  @Test
  fun `empty question and options creates valid poll`() {
    val poll = Poll(question = "", options = emptyList(), votes = emptyMap())

    Assert.assertNotNull(poll)
    Assert.assertEquals("", poll.question)
    Assert.assertTrue(poll.options.isEmpty())
    Assert.assertFalse(poll.allowMultipleVotes)
  }
}
