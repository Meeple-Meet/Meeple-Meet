package com.github.meeplemeet.model.discussions

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AccountRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository wrapping Firestore CRUD operations and snapshot listeners.
 *
 * Provides suspend functions for one-shot reads/writes and Flow-based listeners for real-time
 * updates.
 */
class DiscussionRepository(accountRepository: AccountRepository = RepositoryProvider.accounts) :
    FirestoreRepository("discussions") {
  private val accounts = accountRepository.collection

  /** Create a new discussion and store an empty preview for the creator. */
  suspend fun createDiscussion(
      name: String,
      description: String,
      creatorId: String,
      participants: List<String> = emptyList()
  ): Discussion {
    val discussion =
        Discussion(
            newUUID(),
            creatorId,
            name,
            description,
            emptyList(),
            participants + creatorId,
            listOf(creatorId),
            Timestamp.now(),
            null)

    val batch = db.batch()
    batch.set(collection.document(discussion.uid), toNoUid(discussion))
    (participants + creatorId).forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.set(ref, DiscussionPreviewNoUid())
    }
    batch.commit().await()

    return discussion
  }

  /** Retrieve a discussion document by ID. */
  suspend fun getDiscussion(id: String): Discussion {
    val snapshot = collection.document(id).get().await()
    val discussion = snapshot.toObject(DiscussionNoUid::class.java)
    if (discussion != null) return fromNoUid(id, discussion)
    throw DiscussionNotFoundException()
  }

  /** Update a discussion's name. */
  suspend fun setDiscussionName(id: String, name: String) {
    collection.document(id).update(Discussion::name.name, name).await()
  }

  /** Update a discussion's description. */
  suspend fun setDiscussionDescription(id: String, description: String) {
    collection.document(id).update(Discussion::description.name, description).await()
  }

  /** Delete a discussion document. */
  suspend fun deleteDiscussion(discussion: Discussion) {
    val batch = db.batch()
    batch.delete(collection.document(discussion.uid))
    discussion.participants.forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.delete(ref)
    }
    batch.commit().await()
  }

  /** Add a user to the participants array. */
  suspend fun addUserToDiscussion(discussion: Discussion, userId: String) {
    collection
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(userId))
        .await()
    accounts
        .document(userId)
        .collection(Account::previews.name)
        .document(discussion.uid)
        .set(toPreview(discussion))
        .await()
  }

  /** Remove a user from the participants and admins array */
  suspend fun removeUserFromDiscussion(
      discussion: Discussion,
      userId: String,
      changeOwner: Boolean = false
  ) {
    val updates =
        mutableMapOf<String, Any>(
            Discussion::participants.name to FieldValue.arrayRemove(userId),
            Discussion::admins.name to FieldValue.arrayRemove(userId))

    // If the user being removed is the owner, reassign ownership
    if (changeOwner) {
      val remainingParticipants = discussion.participants.filter { it != userId }

      // Try to find a random admin first (excluding the user being removed)
      val remainingAdmins = discussion.admins.filter { it != userId }
      val newOwner = remainingAdmins.randomOrNull() ?: remainingParticipants.randomOrNull()

      // Only update owner if there's someone to assign it to
      if (newOwner != null) {
        updates[DiscussionNoUid::creatorId.name] = newOwner

        // Add new owner to admins if they're not already an admin
        if (newOwner !in remainingAdmins) {
          updates[Discussion::admins.name] = FieldValue.arrayUnion(newOwner)
        }
      }
    }

    collection.document(discussion.uid).update(updates).await()
    accounts
        .document(userId)
        .collection(Account::previews.name)
        .document(discussion.uid)
        .delete()
        .await()
  }

  /** Add multiple users to the participants array. */
  suspend fun addUsersToDiscussion(discussion: Discussion, userIds: List<String>) {
    collection
        .document(discussion.uid)
        .update(Discussion::participants.name, FieldValue.arrayUnion(*userIds.toTypedArray()))
        .await()
    val batch = db.batch()
    userIds.forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.set(ref, toPreview(discussion))
    }
    batch.commit().await()
  }

  /** Remove multiple users from the participants and admins array. */
  suspend fun removeUsersFromDiscussion(discussion: Discussion, userIds: List<String>) {
    collection
        .document(discussion.uid)
        .update(
            Discussion::participants.name,
            FieldValue.arrayRemove(*userIds.toTypedArray()),
            Discussion::admins.name,
            FieldValue.arrayRemove(*userIds.toTypedArray()))
        .await()
    val batch = db.batch()
    userIds.forEach { id ->
      val ref = accounts.document(id).collection(Account::previews.name).document(discussion.uid)
      batch.delete(ref)
    }
    batch.commit().await()
  }

  /** Add a user as admin (and participant if missing). */
  suspend fun addAdminToDiscussion(discussion: Discussion, userId: String) {
    if (!discussion.participants.contains(userId)) addUserToDiscussion(discussion, userId)
    collection
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayUnion(userId))
        .await()
  }

  /** Remove a user from the admins array */
  suspend fun removeAdminFromDiscussion(discussion: Discussion, userId: String) {
    collection
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayRemove(userId))
        .await()
  }

  /** Add multiple admins (and participants if missing). */
  suspend fun addAdminsToDiscussion(discussion: Discussion, adminIds: List<String>) {
    val current = discussion.participants.toSet()
    val newParticipants = adminIds.filterNot { it in current }
    if (newParticipants.isNotEmpty()) {
      collection
          .document(discussion.uid)
          .update(
              Discussion::participants.name, FieldValue.arrayUnion(*newParticipants.toTypedArray()))
          .await()
    }

    val currentAdmins = discussion.admins.toSet()
    val newAdmins = adminIds.filterNot { it in currentAdmins }
    if (newAdmins.isNotEmpty()) {
      collection
          .document(discussion.uid)
          .update(Discussion::admins.name, FieldValue.arrayUnion(*newAdmins.toTypedArray()))
          .await()
    }
  }

  /** Remove multiple users from the admins array. */
  suspend fun removeAdminsFromDiscussion(discussion: Discussion, userIds: List<String>) {
    collection
        .document(discussion.uid)
        .update(Discussion::admins.name, FieldValue.arrayRemove(*userIds.toTypedArray()))
        .await()
  }

  /**
   * Append a new message to the discussion and update unread counts in all participants' previews.
   * Handles both regular messages and poll messages.
   */
  suspend fun sendMessageToDiscussion(discussion: Discussion, sender: Account, content: String) {
    val message = Message(sender.uid, content)
    val batch = FirebaseFirestore.getInstance().batch()

    // Append message
    batch.update(
        collection.document(discussion.uid),
        Discussion::messages.name,
        FieldValue.arrayUnion(message))

    // Update previews for all participants
    discussion.participants.forEach { userId ->
      val ref =
          accounts.document(userId).collection(Account::previews.name).document(discussion.uid)
      val unreadCountValue = if (userId == sender.uid) 0 else FieldValue.increment(1)
      batch.set(
          ref,
          mapOf(
              "lastMessage" to message.content,
              "lastMessageSender" to message.senderId,
              "lastMessageAt" to message.createdAt,
              "unreadCount" to unreadCountValue),
          SetOptions.merge())
    }

    batch.commit().await()
  }

  /**
   * Append a message (regular or poll) to the discussion and update previews. This overload accepts
   * a pre-constructed Message object.
   */
  private suspend fun sendMessageToDiscussion(discussion: Discussion, message: Message) {
    val batch = FirebaseFirestore.getInstance().batch()

    // Append message
    batch.update(
        collection.document(discussion.uid),
        Discussion::messages.name,
        FieldValue.arrayUnion(message))

    // Update previews for all participants
    discussion.participants.forEach { userId ->
      val ref =
          accounts.document(userId).collection(Account::previews.name).document(discussion.uid)
      val unreadCountValue = if (userId == message.senderId) 0 else FieldValue.increment(1)

      // Use poll-specific preview text if message contains a poll
      val previewText =
          if (message.poll != null) {
            "Poll: ${message.poll.question}"
          } else {
            message.content
          }

      batch.set(
          ref,
          mapOf(
              "lastMessage" to previewText,
              "lastMessageSender" to message.senderId,
              "lastMessageAt" to message.createdAt,
              "unreadCount" to unreadCountValue),
          SetOptions.merge())
    }

    batch.commit().await()
  }

  /** Reset unread count for a given discussion for this account. */
  suspend fun readDiscussionMessages(accountId: String, discussionId: String, message: Message) {
    val ref = accounts.document(accountId).collection(Account::previews.name).document(discussionId)
    val snapshot = ref.get().await()
    val existing = snapshot.toObject(DiscussionPreviewNoUid::class.java)

    if (existing != null) ref.set(existing.copy(unreadCount = 0))
    else ref.set(DiscussionPreviewNoUid(message.content, message.senderId, message.createdAt, 0))
  }

  /**
   * Listen for changes to a specific discussion document.
   *
   * Emits a new [Discussion] every time the Firestore snapshot updates.
   */
  fun listenDiscussion(discussionId: String): Flow<Discussion> = callbackFlow {
    val reg =
        collection.document(discussionId).addSnapshotListener { snap, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (snap != null && snap.exists()) {
            snap.toObject(DiscussionNoUid::class.java)?.let { trySend(fromNoUid(snap.id, it)) }
          }
        }
    awaitClose { reg.remove() }
  }

  // ============================================================================
  // Poll Methods
  // ============================================================================

  /**
   * Create a new poll in a discussion by sending a message with poll data.
   *
   * @param discussion The discussion where the poll will be created.
   * @param creatorId The ID of the account creating the poll.
   * @param question The poll question.
   * @param options List of options users can vote for.
   * @param allowMultipleVotes Whether users can vote multiple times.
   * @return The created poll message.
   */
  suspend fun createPoll(
      discussion: Discussion,
      creatorId: String,
      question: String,
      options: List<String>,
      allowMultipleVotes: Boolean = false
  ): Message {
    val poll =
        Poll(
            question = question,
            options = options,
            votes = emptyMap(),
            allowMultipleVotes = allowMultipleVotes)

    val message =
        Message(senderId = creatorId, content = question, createdAt = Timestamp.now(), poll = poll)

    sendMessageToDiscussion(discussion, message)

    return message
  }

  /**
   * Vote on a poll option.
   *
   * @param discussion The discussion containing the poll.
   * @param pollMessage The message containing the poll.
   * @param userId The user's UID.
   * @param optionIndex The index of the option to vote for.
   */
  suspend fun voteOnPoll(
      discussion: Discussion,
      pollMessage: Message,
      userId: String,
      optionIndex: Int
  ) {
    val poll = pollMessage.poll ?: throw IllegalArgumentException("Message does not contain a poll")

    if (optionIndex !in poll.options.indices) throw IllegalArgumentException("Invalid option index")

    // Find the message index in the discussion
    val messageIndex = discussion.messages.indexOfFirst { it.createdAt == pollMessage.createdAt }
    if (messageIndex == -1) throw IllegalArgumentException("Poll message not found in discussion")

    // Get the latest discussion state
    val latestDiscussion = getDiscussion(discussion.uid)
    val currentMessage = latestDiscussion.messages[messageIndex]
    val currentPoll =
        currentMessage.poll ?: throw IllegalArgumentException("Message does not contain a poll")

    // Calculate updated votes
    val updatedVotes = currentPoll.votes.toMutableMap()
    val currentVotes = updatedVotes[userId]?.toMutableList() ?: mutableListOf()

    if (currentPoll.allowMultipleVotes) {
      // Multiple vote mode: add to user's votes if not already voted for this option
      if (optionIndex !in currentVotes) {
        currentVotes.add(optionIndex)
        updatedVotes[userId] = currentVotes
      }
    } else {
      // Single vote mode: replace with single option
      updatedVotes[userId] = listOf(optionIndex)
    }

    // Update the message with new poll data
    val updatedPoll = currentPoll.copy(votes = updatedVotes)
    val updatedMessage = currentMessage.copy(poll = updatedPoll)
    val updatedMessages = latestDiscussion.messages.toMutableList()
    updatedMessages[messageIndex] = updatedMessage

    // Update the entire messages array
    collection.document(discussion.uid).update(Discussion::messages.name, updatedMessages).await()
  }

  /**
   * Remove a user's vote for a specific poll option. Called when user clicks an option they
   * previously selected to deselect it.
   *
   * @param discussion The discussion containing the poll.
   * @param pollMessage The message containing the poll.
   * @param userId The user's UID.
   * @param optionIndex The specific option index to remove.
   */
  suspend fun removeVoteFromPoll(
      discussion: Discussion,
      pollMessage: Message,
      userId: String,
      optionIndex: Int
  ) {
    // Find the message index in the discussion
    val messageIndex = discussion.messages.indexOfFirst { it.createdAt == pollMessage.createdAt }
    if (messageIndex == -1) throw IllegalArgumentException("Poll message not found in discussion")

    // Get the latest discussion state
    val latestDiscussion = getDiscussion(discussion.uid)
    val currentMessage = latestDiscussion.messages[messageIndex]
    val currentPoll =
        currentMessage.poll ?: throw IllegalArgumentException("Message does not contain a poll")

    // Check if user has voted
    val currentVotes = currentPoll.votes[userId]
    if (currentVotes == null || currentVotes.isEmpty()) {
      throw IllegalArgumentException("User has not voted on this poll")
    }

    // Verify user voted for this specific option
    if (optionIndex !in currentVotes) {
      throw IllegalArgumentException("User did not vote for option $optionIndex")
    }

    // Calculate updated votes
    val updatedVotes = currentPoll.votes.toMutableMap()
    val updatedUserVotes = currentVotes.toMutableList()
    updatedUserVotes.remove(optionIndex)

    if (updatedUserVotes.isEmpty()) {
      // If no votes left, remove user from votes map entirely
      updatedVotes.remove(userId)
    } else {
      updatedVotes[userId] = updatedUserVotes
    }

    // Update the message with new poll data
    val updatedPoll = currentPoll.copy(votes = updatedVotes)
    val updatedMessage = currentMessage.copy(poll = updatedPoll)
    val updatedMessages = latestDiscussion.messages.toMutableList()
    updatedMessages[messageIndex] = updatedMessage

    // Update the entire messages array
    collection.document(discussion.uid).update(Discussion::messages.name, updatedMessages).await()
  }
}
