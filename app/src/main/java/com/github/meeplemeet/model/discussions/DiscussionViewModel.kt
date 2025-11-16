package com.github.meeplemeet.model.discussions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AccountViewModel
import com.github.meeplemeet.model.images.ImageRepository
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
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
    private val imageRepository: ImageRepository = RepositoryProvider.images
) : ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope
  /** Send a text message to a discussion. */
  fun sendMessageToDiscussion(discussion: Discussion, sender: Account, content: String) {
    val cleaned = content.trimEnd()
    if (cleaned.isBlank()) throw IllegalArgumentException("Message content cannot be blank")

    viewModelScope.launch {
      readDiscussionMessages(sender, discussion)
      discussionRepository.sendMessageToDiscussion(discussion, sender, cleaned)
    }
  }

  /**
   * Upload a local image and send it as a photo message.
   *
   * @param discussion The discussion to send the message to.
   * @param sender The account sending the message.
   * @param content Optional text to accompany the photo.
   * @param context Android context for storage/cache access.
   * @param localPath Absolute path to the local image file.
   */
  fun sendMessageWithPhoto(
      discussion: Discussion,
      sender: Account,
      content: String,
      context: Context,
      localPath: String
  ) {
    if (localPath.isBlank()) throw IllegalArgumentException("Photo path cannot be blank")

    viewModelScope.launch {
      readDiscussionMessages(sender, discussion)
      val urls = imageRepository.saveDiscussionPhotoMessages(context, discussion.uid, localPath)
      val downloadUrl = urls.firstOrNull() ?: throw IllegalStateException("Upload failed")
      discussionRepository.sendPhotoMessageToDiscussion(discussion, sender, content, downloadUrl)
    }
  }

  /** Mark all messages as read for a given discussion. */
  fun readDiscussionMessages(account: Account, discussion: Discussion) {
    // Return early if user is not part of discussion (can happen during navigation transitions)
    if (!discussion.participants.contains(account.uid)) return

    val preview = account.previews[discussion.uid] ?: return
    if (preview.unreadCount == 0) return

    viewModelScope.launch {
      // Get the latest messages to find the last one
      val messages = discussionRepository.getMessages(discussion.uid)
      if (messages.isEmpty()) return@launch

      discussionRepository.readDiscussionMessages(account.uid, discussion.uid, messages.last())
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
   * @param discussionId The discussion UID containing the poll.
   * @param messageId The message UID containing the poll.
   * @param voter The account voting.
   * @param optionIndex The index of the option to vote for.
   */
  fun voteOnPoll(discussionId: String, messageId: String, voter: Account, optionIndex: Int) {
    viewModelScope.launch {
      discussionRepository.voteOnPoll(discussionId, messageId, voter.uid, optionIndex)
    }
  }

  /**
   * Remove a user's vote for a specific poll option. Called when user clicks an option they
   * previously selected to deselect it.
   *
   * @param discussionId The discussion UID containing the poll.
   * @param messageId The message UID containing the poll.
   * @param voter The account whose vote to remove.
   * @param optionIndex The specific option to remove.
   */
  fun removeVoteFromPoll(
      discussionId: String,
      messageId: String,
      voter: Account,
      optionIndex: Int
  ) {
    viewModelScope.launch {
      discussionRepository.removeVoteFromPoll(discussionId, messageId, voter.uid, optionIndex)
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

  /** Holds a [StateFlow] of messages keyed by discussion ID. */
  private val messagesFlows = mutableMapOf<String, StateFlow<List<Message>>>()

  /**
   * Real-time flow of messages in a discussion.
   *
   * Emits a new list of messages on every snapshot change.
   */
  fun messagesFlow(discussionId: String): StateFlow<List<Message>> {
    if (discussionId.isBlank()) return MutableStateFlow(emptyList())
    return messagesFlows.getOrPut(discussionId) {
      discussionRepository
          .listenMessages(discussionId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = emptyList())
    }
  }
}
