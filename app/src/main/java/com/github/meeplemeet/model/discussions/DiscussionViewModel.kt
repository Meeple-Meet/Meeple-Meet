package com.github.meeplemeet.model.discussions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountViewModel
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val SUGGESTIONS_LIMIT = 30

/**
 * ViewModel for managing discussion messaging and real-time updates.
 *
 * Provides high-level APIs for messaging operations (text, photos, polls) and exposes real-time
 * discussion and message data as StateFlows for UI consumption. All database operations are
 * executed in the viewModelScope.
 *
 * ## Features
 * - **Text messaging**: Send and read text messages
 * - **Photo messaging**: Upload photos and send as message attachments (NEW)
 * - **Poll management**: Create polls, vote, remove votes
 * - **Real-time data**: StateFlow-based reactive streams for discussions and messages
 * - **Unread tracking**: Automatic unread count management
 *
 * ## Photo Messaging Flow
 * 1. User selects/captures photo → cached via [com.github.meeplemeet.model.images.ImageFileUtils]
 * 2. [sendMessageWithPhoto] uploads to Firebase Storage via [ImageRepository]
 * 3. Creates message with photoUrl via [DiscussionRepository]
 * 4. All participants' previews are updated with "📷 Photo" text
 *
 * ## StateFlow Management
 * - Flows are cached per discussion ID to avoid duplicate listeners
 * - Flows use WhileSubscribed sharing policy (stop when no collectors)
 * - Initial values: null for discussions, empty list for messages
 *
 * @property discussionRepository Repository for discussion operations
 * @property imageRepository Repository for photo upload/download operations
 * @see DiscussionRepository for low-level database operations
 * @see ImageRepository for photo storage operations
 * @see DiscussionDetailsViewModel for discussion metadata management
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
   * Upload a photo and send it as a message attachment to the discussion.
   *
   * This method coordinates the entire photo messaging flow:
   * 1. Validates the local photo path
   * 2. Marks discussion as read for the sender
   * 3. Uploads photo to Firebase Storage (WebP format, 800px max, 40% quality)
   * 4. Creates message with photoUrl and optional caption
   * 5. Updates all participants' previews with "📷 Photo" text
   *
   * The operation is executed asynchronously in viewModelScope. Failures during upload or message
   * creation will throw exceptions that should be caught by the caller.
   *
   * ## Typical Usage Flow
   *
   * ```kotlin
   * // After user selects photo
   * val cachedPath = ImageFileUtils.cacheUriToFile(context, photoUri)
   * viewModel.sendMessageWithPhoto(discussion, account, "Check this out!", context, cachedPath)
   * // Clean up cache after
   * File(cachedPath).delete()
   * ```
   *
   * @param discussion The discussion to send the message to.
   * @param sender The account sending the message.
   * @param content Optional caption/description for the photo. Can be empty string.
   * @param context Android context for accessing storage and cache.
   * @param localPath Absolute file path to the local image (typically in app cache directory).
   * @throws IllegalArgumentException if localPath is blank
   * @throws IllegalStateException if photo upload fails to return a download URL
   * @see com.github.meeplemeet.model.images.ImageFileUtils.cacheUriToFile for preparing gallery
   *   photos
   * @see com.github.meeplemeet.model.images.ImageFileUtils.saveBitmapToCache for preparing camera
   *   photos
   * @see ImageRepository.saveDiscussionPhotoMessages for photo upload
   * @see DiscussionRepository.sendPhotoMessageToDiscussion for message creation
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
      // Get the latest message
      val lastMessage = discussionRepository.getLastMessage(discussion.uid) ?: return@launch

      discussionRepository.readDiscussionMessages(account.uid, discussion.uid, lastMessage)
    }
  }

  /**
   * Load older messages for pagination.
   *
   * Fetches messages that were created before the given timestamp. Useful for implementing "load
   * more" functionality when the user scrolls to the top of the message list.
   *
   * ## Typical Usage Flow
   *
   * ```kotlin
   * val currentMessages = messagesFlow(discussionId).value
   * val oldestMessage = currentMessages.firstOrNull()
   * if (oldestMessage != null) {
   *   val olderMessages = viewModel.loadOlderMessages(discussionId, oldestMessage.createdAt)
   *   // Prepend olderMessages to your state
   * }
   * ```
   *
   * @param discussionId The discussion UID.
   * @param beforeTimestamp Load messages created before this timestamp.
   * @param limit Maximum number of messages to fetch (default: 50).
   * @return List of older messages ordered by createdAt timestamp (oldest first).
   * @see messagesFlow for real-time message updates
   */
  suspend fun loadOlderMessages(
      discussionId: String,
      beforeTimestamp: com.google.firebase.Timestamp,
      limit: Int = 50
  ): List<Message> {
    return discussionRepository.getMessagesBeforeTimestamp(discussionId, beforeTimestamp, limit)
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
  suspend fun voteOnPoll(
      discussionId: String,
      messageId: String,
      voter: Account,
      optionIndex: Int
  ) = discussionRepository.voteOnPoll(discussionId, messageId, voter.uid, optionIndex)

  /** Non-suspending wrapper used by UI callbacks. */
  fun voteOnPollAsync(discussionId: String, messageId: String, voter: Account, optionIndex: Int) =
      viewModelScope.launch { voteOnPoll(discussionId, messageId, voter, optionIndex) }

  /**
   * Remove a user's vote for a specific poll option. Called when user clicks an option they
   * previously selected to deselect it.
   *
   * @param discussionId The discussion UID containing the poll.
   * @param messageId The message UID containing the poll.
   * @param voter The account whose vote to remove.
   * @param optionIndex The specific option to remove.
   */
  suspend fun removeVoteFromPoll(
      discussionId: String,
      messageId: String,
      voter: Account,
      optionIndex: Int
  ) = discussionRepository.removeVoteFromPoll(discussionId, messageId, voter.uid, optionIndex)

  /** Non-suspending wrapper used by UI callbacks. */
  fun removeVoteFromPollAsync(
      discussionId: String,
      messageId: String,
      voter: Account,
      optionIndex: Int
  ) = viewModelScope.launch { removeVoteFromPoll(discussionId, messageId, voter, optionIndex) }

  /**
   * Real-time flow of messages in a discussion with offline caching support.
   *
   * Returns a StateFlow that emits the list of messages for the given discussion. The behavior
   * depends on network connectivity:
   * - **Online**: Fetches messages from repository and caches them automatically
   * - **Offline**: Returns cached messages from OfflineModeManager
   *
   * Messages are ordered by creation time (oldest first).
   *
   * ## Offline Caching
   * - **Cache source**: OfflineModeManager.offlineModeFlow
   * - **Auto-caching**: New messages are automatically cached when online
   * - **Cache updates**: Triggered when discussionId changes
   *
   * ## Usage in Composables
   *
   * ```kotlin
   * val context = LocalContext.current
   * val messages by viewModel.messagesFlow(discussionId, context).collectAsState()
   * LazyColumn {
   *   items(messages) { message ->
   *     MessageBubble(message)
   *   }
   * }
   * ```
   *
   * @param discussionId The discussion UID to listen to. Returns empty flow if blank.
   * @param context Android context for image caching operations (required for caching).
   * @return StateFlow emitting lists of messages ordered by createdAt.
   * @see DiscussionRepository.listenMessages for underlying listener
   * @see OfflineModeManager.cacheDiscussionMessages for cache updates
   * @see OfflineModeManager.hasInternetConnection for connectivity status
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun messagesFlow(discussionId: String, context: Context): StateFlow<List<Message>> {
    if (discussionId.isBlank()) return MutableStateFlow(emptyList())

    if (!OfflineModeManager.offlineModeFlow.value.discussions.containsKey(discussionId)) {
      viewModelScope.launch { OfflineModeManager.loadDiscussion(discussionId, context) { _ -> } }
    }

    val cachedMessagesFlow =
        OfflineModeManager.offlineModeFlow.map { offlineMode ->
          offlineMode.discussions[discussionId]?.second
        }

    return combine(
            OfflineModeManager.hasInternetConnection,
            OfflineModeManager.networkMonitorStarted,
            cachedMessagesFlow) { isOnline, monitorStarted, cached ->
              Triple(isOnline, monitorStarted, cached)
            }
        .flatMapLatest { (isOnline, monitorStarted, cachedMessages) ->
          val cacheAvailable = !cachedMessages.isNullOrEmpty()
          val treatAsOnline = isOnline || !monitorStarted
          if (treatAsOnline || !cacheAvailable) {
            discussionRepository.listenMessages(discussionId).onEach { messages ->
              OfflineModeManager.cacheDiscussionMessages(discussionId, messages)
            }
          } else {
            flowOf(cachedMessages!!)
          }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue =
                OfflineModeManager.offlineModeFlow.value.discussions[discussionId]?.second
                    ?: emptyList())
  }
}
