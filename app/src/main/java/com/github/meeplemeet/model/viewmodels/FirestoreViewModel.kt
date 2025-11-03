package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
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
class FirestoreViewModel(
    private val repository: FirestoreRepository = FirestoreRepository(FirebaseProvider.db)
) : ViewModel() {
  private val _handleSuggestions = MutableStateFlow<List<Account>>(emptyList())

  /** The currently loaded handle suggestions */
  val handleSuggestions: StateFlow<List<Account>> = _handleSuggestions

  private fun isAdmin(account: Account, discussion: Discussion): Boolean {
    return discussion.admins.contains(account.uid) || account.uid == discussion.creatorId
  }

  /** Create a new discussion. */
  fun createDiscussion(
      name: String,
      description: String,
      creator: Account,
      vararg participants: Account
  ) {
    viewModelScope.launch {
      repository.createDiscussion(
          name.ifBlank { "${creator.name}'s discussion" },
          description,
          creator.uid,
          participants.map { it.uid })
    }
  }

  /** Update discussion name (admin-only). */
  fun setDiscussionName(discussion: Discussion, changeRequester: Account, name: String) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      /*_discussion.value =*/
      repository.setDiscussionName(
          discussion.uid,
          name.ifBlank { "Discussion with: ${discussion.participants.joinToString(", ")}" })
    }
  }

  /** Update discussion description (admin-only). */
  fun setDiscussionDescription(
      discussion: Discussion,
      changeRequester: Account,
      description: String
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.setDiscussionDescription(discussion.uid, description) }
  }

  /** Delete a discussion (admin-only). */
  fun deleteDiscussion(discussion: Discussion, changeRequester: Account) {
    if (discussion.creatorId != changeRequester.uid)
        throw PermissionDeniedException("Only discussion owner can perform this operation")

    viewModelScope.launch { repository.deleteDiscussion(discussion) }
  }

  /** Add a user to a discussion (admin-only). */
  fun addUserToDiscussion(discussion: Discussion, changeRequester: Account, user: Account) {
    if (discussion.participants.contains(user.uid)) return
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.addUserToDiscussion(discussion, user.uid) }
  }

  /** Remove a user from a discussion (admin-only). */
  fun removeUserFromDiscussion(discussion: Discussion, changeRequester: Account, user: Account) {
    if (changeRequester != user && !isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (discussion.creatorId == user.uid && changeRequester.uid != discussion.creatorId)
        throw PermissionDeniedException("Cannot remove the owner of this discussion")

    viewModelScope.launch { repository.removeUserFromDiscussion(discussion, user.uid) }
  }

  /** Add multiple users (admin-only). */
  fun addUsersToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg users: Account
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    val usersToAdd = users.filter { it -> !discussion.participants.contains(it.uid) }.map { it.uid }
    if (usersToAdd.isEmpty()) return

    viewModelScope.launch { repository.addUsersToDiscussion(discussion, usersToAdd) }
  }

  /** Remove multiple users (admin-only). */
  fun removeUsersFromDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg users: Account
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (users.any { user -> discussion.creatorId == user.uid })
        throw PermissionDeniedException("Cannot remove the owner of this discussion")

    viewModelScope.launch { repository.removeUsersFromDiscussion(discussion, users.map { it.uid }) }
  }

  /** Add a single admin (admin-only). */
  fun addAdminToDiscussion(discussion: Discussion, changeRequester: Account, admin: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.addAdminToDiscussion(discussion, admin.uid) }
  }

  /** Remove a single admin (admin-only). */
  fun removeAdminFromDiscussion(discussion: Discussion, changeRequester: Account, admin: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (discussion.creatorId == admin.uid)
        throw PermissionDeniedException("Cannot demote the owner of this discussion")

    viewModelScope.launch { repository.removeAdminFromDiscussion(discussion, admin.uid) }
  }

  /** Add multiple admins (admin-only). */
  fun addAdminsToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg admins: Account
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.addAdminsToDiscussion(discussion, admins.map { it.uid }) }
  }

  /** Remove multiple admins (admin-only). */
  fun removeAdminsFromDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg admins: Account
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      repository.removeAdminsFromDiscussion(discussion, admins.map { it.uid })
    }
  }

  /** Send a message to a discussion. */
  fun sendMessageToDiscussion(discussion: Discussion, sender: Account, content: String) {
    if (content.isBlank()) throw IllegalArgumentException("Message content cannot be blank")

    viewModelScope.launch {
      readDiscussionMessages(sender, discussion)
      repository.sendMessageToDiscussion(discussion, sender, content)
    }
  }

  fun signOut() {
    discussionFlows.clear()
    accountFlows.clear()
  }

  /** Retrieve an account by ID. */
  fun getAccount(id: String) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")

    viewModelScope.launch { repository.getAccount(id) }
  }

  /** Retrieve an account by ID without changing the current account state. */
  fun getOtherAccount(id: String, onResult: (Account) -> Unit) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")

    viewModelScope.launch { onResult(repository.getAccount(id)) }
  }

  fun getAccounts(uids: List<String>, onResult: (List<Account>) -> Unit) {
    viewModelScope.launch { onResult(repository.getAccounts(uids)) }
  }

  /** Update account name. */
  fun setAccountName(account: Account, newName: String) {
    viewModelScope.launch { repository.setAccountName(account.uid, newName.ifBlank { "~" }) }
  }

  /** Delete an account. */
  fun deleteAccount(account: Account) {
    viewModelScope.launch { repository.deleteAccount(account.uid) }
  }

  /** Mark all messages as read for a given discussion. */
  fun readDiscussionMessages(account: Account, discussion: Discussion) {
    // Return early if user is not part of discussion (can happen during navigation transitions)
    if (!discussion.participants.contains(account.uid)) return

    if (discussion.messages.isEmpty()) return
    if (account.previews[discussion.uid]!!.unreadCount == 0) return

    viewModelScope.launch {
      repository.readDiscussionMessages(account.uid, discussion.uid, discussion.messages.last())
    }
  }

  // ---------- Real-time flows ----------

  /** Holds a [StateFlow] of discussion preview maps keyed by account ID. */
  private val accountFlows = mutableMapOf<String, StateFlow<Account?>>()

  /**
   * Real-time flow of all discussion previews for an account.
   *
   * Emits a new map whenever any preview changes in Firestore.
   */
  fun accountFlow(accountId: String): StateFlow<Account?> {
    if (accountId.isBlank()) return MutableStateFlow(null)
    return accountFlows.getOrPut(accountId) {
      repository
          .listenAccount(accountId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = null)
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
      repository
          .listenDiscussion(discussionId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = null)
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
      repository.createPoll(discussion, creatorId, question, options, allowMultipleVotes)
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
  fun voteOnPoll(
      discussion: Discussion,
      pollMessage: com.github.meeplemeet.model.structures.Message,
      voter: Account,
      optionIndex: Int
  ) {
    viewModelScope.launch { repository.voteOnPoll(discussion, pollMessage, voter.uid, optionIndex) }
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
      pollMessage: com.github.meeplemeet.model.structures.Message,
      voter: Account,
      optionIndex: Int
  ) {
    viewModelScope.launch {
      repository.removeVoteFromPoll(discussion, pollMessage, voter.uid, optionIndex)
    }
  }
}
