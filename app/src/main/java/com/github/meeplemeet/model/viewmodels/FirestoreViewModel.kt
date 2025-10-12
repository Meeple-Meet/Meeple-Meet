package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.NotSignedInException
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionPreview
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel exposing Firestore operations and real-time listeners as flows.
 *
 * All one-shot database operations (CRUD) are suspending functions. Real-time updates from snapshot
 * listeners are exposed as [StateFlow] streams.
 */
class FirestoreViewModel(
    private val repository: FirestoreRepository = FirestoreRepository(Firebase.firestore)
) : ViewModel() {

  private val _account = MutableStateFlow<Account?>(null)

  /** The currently loaded account */
  val account: StateFlow<Account?> = _account

  private val _discussion = MutableStateFlow<Discussion?>(null)

  /** The currently loaded discussion */
  val discussion: StateFlow<Discussion?> = _discussion

  private fun isAdmin(account: Account, discussion: Discussion): Boolean {
    return discussion.admins.contains(account.uid)
  }

  /** Create a new discussion. */
  fun createDiscussion(
      name: String,
      description: String,
      creator: Account,
      vararg participants: Account
  ) {
    viewModelScope.launch {
      val (acc, disc) =
          repository.createDiscussion(
              name.ifBlank { "${creator.name}'s discussion" },
              description,
              creator.uid,
              participants.map { it.uid })
      _account.value = acc
      _discussion.value = disc
    }
  }

  /** Retrieve a discussion by ID. */
  fun getDiscussion(id: String) {
    if (id.isBlank()) throw IllegalArgumentException("Discussion id cannot be blank")

    viewModelScope.launch { _discussion.value = repository.getDiscussion(id) }
  }

  /** Update discussion name (admin-only). */
  fun setDiscussionName(discussion: Discussion, changeRequester: Account, name: String) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      _discussion.value =
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

    viewModelScope.launch {
      _discussion.value = repository.setDiscussionDescription(discussion.uid, description)
    }
  }

  /** Delete a discussion (admin-only). */
  fun deleteDiscussion(discussion: Discussion, changeRequester: Account) {
    if (discussion.creatorId != changeRequester.uid)
        throw PermissionDeniedException("Only discussion owner can perform this operation")

    viewModelScope.launch {
      repository.deleteDiscussion(discussion.uid)
      _discussion.value = null
    }
  }

  /** Add a user to a discussion (admin-only). */
  fun addUserToDiscussion(discussion: Discussion, changeRequester: Account, user: Account) {
    if (discussion.participants.contains(user.uid)) return
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      _discussion.value = repository.addUserToDiscussion(discussion, user.uid)
    }
  }

  /** Remove a user from a discussion (admin-only). */
  fun removeUserFromDiscussion(discussion: Discussion, changeRequester: Account, user: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (discussion.creatorId == user.uid)
        throw PermissionDeniedException("Cannot remove the owner of this discussion")

    viewModelScope.launch {
      _discussion.value = repository.removeUserFromDiscussion(discussion, user.uid)
    }
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

    viewModelScope.launch {
      _discussion.value = repository.addUsersToDiscussion(discussion, usersToAdd)
    }
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

    viewModelScope.launch {
      _discussion.value = repository.removeUsersFromDiscussion(discussion, users.map { it.uid })
    }
  }

  /** Add a single admin (admin-only). */
  fun addAdminToDiscussion(discussion: Discussion, changeRequester: Account, admin: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      _discussion.value = repository.addAdminToDiscussion(discussion, admin.uid)
    }
  }

  /** Remove a single admin (admin-only). */
  fun removeAdminFromDiscussion(discussion: Discussion, changeRequester: Account, admin: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (discussion.creatorId == admin.uid)
        throw PermissionDeniedException("Cannot demote the owner of this discussion")

    viewModelScope.launch {
      _discussion.value = repository.removeAdminFromDiscussion(discussion, admin.uid)
    }
  }

  /** Add multiple admins (admin-only). */
  fun addAdminsToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg admins: Account
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      _discussion.value = repository.addAdminsToDiscussion(discussion, admins.map { it.uid })
    }
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
      _discussion.value = repository.removeAdminsFromDiscussion(discussion, admins.map { it.uid })
    }
  }

  /** Send a message to a discussion. */
  fun sendMessageToDiscussion(discussion: Discussion, sender: Account, content: String) {
    if (content.isBlank()) throw IllegalArgumentException("Message content cannot be blank")

    viewModelScope.launch {
      readDiscussionMessages(sender, discussion)
      _discussion.value = repository.sendMessageToDiscussion(discussion, sender, content)
    }
  }

  /** Retrieve an account by ID. */
  fun getAccount(id: String) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")

    viewModelScope.launch { _account.value = repository.getAccount(id) }
  }

  /** Retrieve an account by ID without changing the current account state. */
  fun getOtherAccount(id: String, onResult: (Account) -> Unit) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")

    viewModelScope.launch { onResult(repository.getAccount(id)) }
  }

  /** Get the current signed-in account. */
  fun getCurrentAccount() {
    if (Firebase.auth.currentUser == null)
        throw NotSignedInException("A user must be signed in for the UI to call this function")

    viewModelScope.launch {
      _account.value = repository.getAccount(Firebase.auth.currentUser?.uid ?: "")
    }
  }

  /** Update account name. */
  fun setAccountName(account: Account, newName: String) {
    viewModelScope.launch {
      _account.value = repository.setAccountName(account.uid, newName.ifBlank { "~" })
    }
  }

  /** Delete an account. */
  fun deleteAccount(account: Account) {
    viewModelScope.launch { repository.deleteAccount(account.uid) }
  }

  /** Mark all messages as read for a given discussion. */
  fun readDiscussionMessages(account: Account, discussion: Discussion) {
    if (!discussion.participants.contains(account.uid))
        throw PermissionDeniedException(
            "Account: ${account.uid} - ${account.name} is not a part of Discussion: ${discussion.uid} - ${discussion.name}")

    if (discussion.messages.isEmpty()) return

    viewModelScope.launch {
      _account.value =
          repository.readDiscussionMessages(account.uid, discussion.uid, discussion.messages.last())
    }
  }

  // ---------- Real-time flows ----------

  /** Holds a [StateFlow] of discussion preview maps keyed by account ID. */
  private val previewStates = mutableMapOf<String, StateFlow<Map<String, DiscussionPreview>>>()

  /**
   * Real-time flow of all discussion previews for an account.
   *
   * Emits a new map whenever any preview changes in Firestore.
   */
  fun previewsFlow(accountId: String): StateFlow<Map<String, DiscussionPreview>> {
    return previewStates.getOrPut(accountId) {
      repository
          .listenMyPreviews(accountId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = emptyMap())
    }
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
