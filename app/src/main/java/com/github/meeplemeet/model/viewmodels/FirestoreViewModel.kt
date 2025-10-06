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

class FirestoreViewModel(
    private val repository: FirestoreRepository = FirestoreRepository(Firebase.firestore)
) : ViewModel() {
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

  suspend fun getDiscussion(id: String): Discussion {
    if (id.isBlank()) throw IllegalArgumentException("Discussion id cannot be blank")
    return repository.getDiscussion(id)
  }

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

  suspend fun setDiscussionDescription(
      discussion: Discussion,
      changeRequester: Account,
      description: String
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.setDiscussionDescription(discussion.uid, description)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  suspend fun deleteDiscussion(discussion: Discussion, changeRequester: Account) {
    if (discussion.admins.contains(changeRequester.uid)) repository.deleteDiscussion(discussion.uid)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  suspend fun addUserToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      user: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addUserToDiscussion(discussion, user.uid)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  suspend fun addUsersToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg users: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addUsersToDiscussion(discussion, users.map { it.uid })
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  suspend fun addAdminToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      admin: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addAdminToDiscussion(discussion, admin.uid)
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  suspend fun addAdminsToDiscussion(
      discussion: Discussion,
      changeRequester: Account,
      vararg admins: Account
  ): Discussion {
    if (discussion.admins.contains(changeRequester.uid))
        return repository.addAdminsToDiscussion(discussion, admins.map { it.uid })
    throw PermissionDeniedException("Only discussion admins can perform this operation")
  }

  suspend fun sendMessageToDiscussion(
      discussion: Discussion,
      sender: Account,
      content: String
  ): Discussion {
    if (content.isBlank()) throw IllegalArgumentException("Message content cannot be blank")
    readDiscussionMessages(sender, discussion)
    return repository.sendMessageToDiscussion(discussion, sender, content)
  }

  suspend fun createAccount(name: String): Account {
    if (name.isBlank()) throw IllegalArgumentException("Account name cannot be blank")
    return repository.createAccount(name)
  }

  suspend fun getAccount(id: String): Account {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")
    return repository.getAccount(id)
  }

  suspend fun getCurrentAccount(): Account {
    return repository.getCurrentAccount()
  }

  suspend fun setAccountName(account: Account, newName: String): Account {
    if (newName.isBlank()) throw IllegalArgumentException("Account name cannot be blank")
    return repository.setAccountName(account.uid, newName)
  }

  suspend fun deleteAccount(account: Account) {
    repository.deleteAccount(account.uid)
  }

  suspend fun readDiscussionMessages(account: Account, discussion: Discussion): Account {
    if (!discussion.participants.contains(account.uid))
        throw PermissionDeniedException(
            "Account: ${account.uid} - ${account.name} is not a part of Discussion: ${discussion.uid} - ${discussion.name}")
    if (discussion.messages.isEmpty()) return account
    return repository.readDiscussionMessages(
        account.uid, discussion.uid, discussion.messages.last())
  }

  private val previewStates = mutableMapOf<String, StateFlow<Map<String, DiscussionPreview>>>()

  fun previewsFlow(accountId: String): StateFlow<Map<String, DiscussionPreview>> =
      previewStates.getOrPut(accountId) {
        repository
            .listenMyPreviews(accountId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap())
      }

  fun previewFlow(accountId: String, discussionId: String): StateFlow<DiscussionPreview?> =
      previewsFlow(accountId)
          .map { it[discussionId] }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = previewsFlow(accountId).value[discussionId])

  fun previewRemoveFlow(accountId: String) {
    if (previewStates.contains(accountId)) previewStates.remove(accountId)
  }

  private val discussionFlows = mutableMapOf<String, StateFlow<Discussion?>>()

  fun discussionFlow(discussionId: String): StateFlow<Discussion?> =
      discussionFlows.getOrPut(discussionId) {
        repository
            .listenDiscussion(discussionId)
            .map { it as Discussion? }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null)
      }
}
