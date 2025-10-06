package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionPreview
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel exposing Firestore operations and real-time listeners as flows.
 *
 * All one-shot database operations (CRUD) are suspending functions. Real-time updates from snapshot
 * listeners are exposed as [StateFlow] streams.
 */
class FirestoreViewModel(
    private val repository: FirestoreRepository = FirestoreRepository(Firebase.firestore)
) : ViewModel() {

  /** Create a new discussion. */
  suspend fun createDiscussion(
      name: String,
      description: String,
      creator: Account,
      vararg participants: Account
  ): Pair<Account, Discussion> {
    return repository.createDiscussion(
        name.ifBlank { "${creator.name}'s discussion" },
        description,
        creator.uid,
        participants.map { it.uid })
  }

  /** Retrieve a discussion by ID. */
  suspend fun getDiscussion(id: String): Discussion {
    if (id.isBlank()) throw IllegalArgumentException("Discussion id cannot be blank")
    return repository.getDiscussion(id)
  }

  /** Update discussion name (admin-only). */
  suspend fun setDiscussionName(
      discussion: Discussion,
      changeRequester: Account,
      name: String
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.setDiscussionName(
            discussion.uid,
            name.ifBlank { "Discussion with: ${discussion.participants.joinToString(", ")}" })
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Update discussion description (admin-only). */
  suspend fun setDiscussionDescription(
      discussion: Discussion,
      changeRequester: Account,
      description: String
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.setDiscussionDescription(discussion.uid, description)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Delete a discussion (admin-only). */
  suspend fun deleteDiscussion(discussion: Discussion, changeRequester: Account) {
    if (discussion.admins.contains(changeRequester.uid)) repository.deleteDiscussion(discussion.uid)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Add a user to a discussion (admin-only). */
  suspend fun addUserToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      user: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addUserToDiscussion(discussion, user.uid)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Add multiple users (admin-only). */
  suspend fun addUsersToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg users: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addUsersToDiscussion(discussion, users.map { it.uid })
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Add a single admin (admin-only). */
  suspend fun addAdminToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      admin: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addAdminToDiscussion(discussion, admin.uid)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Add multiple admins (admin-only). */
  suspend fun addAdminsToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg admins: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addAdminsToDiscussion(discussion, admins.map { it.uid })
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  /** Send a message to a discussion. */
  suspend fun sendMessageToDiscussion(
      discussion: Discussion,
      sender: Account,
      content: String
  ): Discussion {
    if (content.isBlank()) throw IllegalArgumentException("Message content cannot be blank")
    readDiscussionMessages(sender, discussion)
    return repository.sendMessageToDiscussion(discussion, sender, content)
  }

  /** Create a new account. */
  suspend fun createAccount(name: String): Account {
    if (name.isBlank()) throw IllegalArgumentException("Account name cannot be blank")
    return repository.createAccount(name)
  }

  /** Retrieve an account by ID. */
  suspend fun getAccount(id: String): Account {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")
    return repository.getAccount(id)
  }

  /** Get the current signed-in account. */
  suspend fun getCurrentAccount(): Account {
    return repository.getCurrentAccount()
  }

  /** Update account name. */
  suspend fun setAccountName(account: Account, newName: String): Account {
    if (newName.isBlank()) throw IllegalArgumentException("Account name cannot be blank")
    return repository.setAccountName(account.uid, newName)
  }

  /** Delete an account. */
  suspend fun deleteAccount(account: Account) {
    repository.deleteAccount(account.uid)
  }

  /** Mark all messages as read for a given discussion. */
  suspend fun readDiscussionMessages(account: Account, discussion: Discussion): Account {
    if (!discussion.participants.contains(account.uid))
        throw PermissionDeniedException(
            "Account: ${account.uid} - ${account.name} is not a part of Discussion: ${discussion.uid} - ${discussion.name}")
    if (discussion.messages.isEmpty()) return account
    return repository.readDiscussionMessages(
        account.uid, discussion.uid, discussion.messages.last())
  }

  // ---------- Real-time flows ----------

  /** Holds a [StateFlow] of discussion preview maps keyed by account ID. */
  private val previewStates = mutableMapOf<String, StateFlow<Map<String, DiscussionPreview>>>()

  /**
   * Real-time flow of all discussion previews for an account.
   *
   * Emits a new map whenever any preview changes in Firestore.
   */
  fun previewsFlow(accountId: String): StateFlow<Map<String, DiscussionPreview>> =
      previewStates.getOrPut(accountId) {
        repository
            .listenMyPreviews(accountId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap())
      }

  /**
   * Real-time flow of a single discussion preview for a specific account.
   *
   * Emits `null` if the preview does not exist.
   */
  fun previewFlow(accountId: String, discussionId: String): StateFlow<DiscussionPreview?> =
      previewsFlow(accountId)
          .map { it[discussionId] }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = previewsFlow(accountId).value[discussionId])

  /** Clear cached preview state for an account to force re-collection. */
  fun previewRemoveFlow(accountId: String) {
    if (previewStates.contains(accountId)) previewStates.remove(accountId)
  }

  /** Holds a [StateFlow] of discussion documents keyed by discussion ID. */
  private val discussionFlows = mutableMapOf<String, StateFlow<Discussion?>>()

  /**
   * Real-time flow of a discussion document.
   *
   * Emits a new [Discussion] on every snapshot change, or `null` if the discussion does not exist
   * yet.
   */
  fun discussionFlow(discussionId: String): StateFlow<Discussion?> =
      discussionFlows.getOrPut(discussionId) {
        repository
            .listenDiscussion(discussionId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null)
      }
}
