package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class FirestoreViewModel(
    private val repository: FirestoreRepository = FirestoreRepository(Firebase.firestore)
) : ViewModel() {
  suspend fun createDiscussion(
      name: String,
      description: String,
      creator: Account,
      participants: List<String> = emptyList()
  ): Discussion {
    return repository.createDiscussion(
        name.ifBlank { "${creator.name}'s discussion" }, description, creator.uid, participants)
  }

  suspend fun getDiscussion(id: String): Discussion {
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
    return repository.sendMessageToDiscussion(discussion, sender, content)
  }
}
