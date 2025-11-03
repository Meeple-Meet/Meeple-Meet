package com.github.meeplemeet.model.discussions
// Github Copilot used for this file
import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable

/**
 * Represents a poll that can be attached to a message.
 *
 * @property question The poll question or title.
 * @property options List of poll options that users can vote for.
 * @property votes Map of userId to the list of option indices they voted for.
 * @property allowMultipleVotes Whether users can vote for multiple options.
 */
@Serializable
data class Poll(
    val question: String = "",
    val options: List<String> = emptyList(),
    val votes: Map<String, List<Int>> = emptyMap(),
    val allowMultipleVotes: Boolean = false
) {
  /**
   * Get the list of user IDs who voted for a specific option. Useful for displaying voters or
   * checking who voted for what.
   *
   * @param optionIndex The index of the option.
   * @return List of user IDs who voted for this option.
   */
  @Exclude
  fun getUsersWhoVotedFor(optionIndex: Int): List<String> {
    return votes.filter { (_, selectedOptions) -> optionIndex in selectedOptions }.keys.toList()
  }

  /**
   * Get vote counts for each option. Returns a map of optionIndex to vote count.
   *
   * @return Map of option index to number of votes.
   */
  @Exclude
  fun getVoteCountsByOption(): Map<Int, Int> {
    val counts = mutableMapOf<Int, Int>()
    votes.values.forEach { selectedOptions ->
      selectedOptions.forEach { optionIndex ->
        counts[optionIndex] = counts.getOrDefault(optionIndex, 0) + 1
      }
    }
    return counts
  }

  /** Get the total number of votes cast (counts each selection). */
  @Exclude fun getTotalVotes(): Int = votes.values.sumOf { it.size }

  /** Get the total number of users who voted. */
  @Exclude fun getTotalVoters(): Int = votes.size

  /**
   * Get the option indices the user voted for, or null if they haven't voted.
   *
   * @param userId The user's UID.
   * @return The list of option indices or null.
   */
  @Exclude fun getUserVotes(userId: String): List<Int>? = votes[userId]

  /**
   * Check if a user has voted in this poll.
   *
   * @param userId The user's UID.
   * @return True if the user has voted.
   */
  @Exclude fun hasUserVoted(userId: String): Boolean = votes.containsKey(userId)

  /**
   * Check if a user voted for a specific option.
   *
   * @param userId The user's UID.
   * @param optionIndex The option index to check.
   * @return True if the user voted for this option.
   */
  @Exclude
  fun hasUserVotedFor(userId: String, optionIndex: Int): Boolean {
    return votes[userId]?.contains(optionIndex) ?: false
  }
}
