package com.github.meeplemeet.model.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val SUGGESTIONS_LIMIT = 30

/**
 * ViewModel exposing Firestore operations and real-time listeners as flows.
 *
 * All one-shot database operations (CRUD) are suspending functions. Real-time updates from snapshot
 * listeners are exposed as [StateFlow] streams.
 */
class DiscussionViewModel(
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions
) : ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope
  /** Send a message to a discussion. */
  fun sendMessageToDiscussion(discussion: Discussion, sender: Account, content: String) {
    if (content.isBlank()) throw IllegalArgumentException("Message content cannot be blank")

    viewModelScope.launch {
      readDiscussionMessages(sender, discussion)
      discussionRepository.sendMessageToDiscussion(discussion, sender, content)
    }
  }

  /** Mark all messages as read for a given discussion. */
  fun readDiscussionMessages(account: Account, discussion: Discussion) {
    // Return early if user is not part of discussion (can happen during navigation transitions)
    if (!discussion.participants.contains(account.uid)) return

    if (discussion.messages.isEmpty()) return
    val preview = account.previews[discussion.uid] ?: return
    if (preview.unreadCount == 0) return

    viewModelScope.launch {
      discussionRepository.readDiscussionMessages(
          account.uid, discussion.uid, discussion.messages.last())
    }
  }

  // ---------- Poll Methods ----------

  /**
   * Create a new poll in a discussion.
   *
   * @param discussion The discussion where the poll will be created.
   * @param creatorId The ID of the account creating the poll.
   * @param question The poll question.
   * @param options List of options users can vote for.
   * @param allowMultipleVotes Whether users can vote multiple times.
   */
  fun createPoll(
      discussion: Discussion,
      creatorId: String,
      question: String,
      options: List<String>,
      allowMultipleVotes: Boolean = false
  ) {
    if (question.isBlank()) throw IllegalArgumentException("Poll question cannot be blank")
    if (options.size < 2) throw IllegalArgumentException("Poll must have at least 2 options")

    viewModelScope.launch {
      discussionRepository.createPoll(discussion, creatorId, question, options, allowMultipleVotes)
    }
  }

  /**
   * Vote on a poll option.
   *
   * @param discussion The discussion containing the poll.
   * @param pollMessage The message containing the poll.
   * @param voter The account voting.
   * @param optionIndex The index of the option to vote for.
   */
  fun voteOnPoll(discussion: Discussion, pollMessage: Message, voter: Account, optionIndex: Int) {
    viewModelScope.launch {
      discussionRepository.voteOnPoll(discussion, pollMessage, voter.uid, optionIndex)
    }
  }

  /**
   * Remove a user's vote for a specific poll option. Called when user clicks an option they
   * previously selected to deselect it.
   *
   * @param discussion The discussion containing the poll.
   * @param pollMessage The message containing the poll.
   * @param voter The account whose vote to remove.
   * @param optionIndex The specific option to remove.
   */
  fun removeVoteFromPoll(
      discussion: Discussion,
      pollMessage: Message,
      voter: Account,
      optionIndex: Int
  ) {
    viewModelScope.launch {
      discussionRepository.removeVoteFromPoll(discussion, pollMessage, voter.uid, optionIndex)
    }
  }

  /** Holds a [StateFlow] of discussion documents keyed by discussion ID. */
  private val discussionFlows = mutableMapOf<String, StateFlow<Discussion?>>()

  /**
   * Real-time flow of a discussion document.
   *
   * Emits a new [Discussion] on every snapshot change, or `null` if the discussion does not exist
   * yet.
   */
  fun discussionFlow(discussionId: String): StateFlow<Discussion?> {
    if (discussionId.isBlank()) return MutableStateFlow(null)
    return discussionFlows.getOrPut(discussionId) {
      discussionRepository
          .listenDiscussion(discussionId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = null)
    }
  }
}
