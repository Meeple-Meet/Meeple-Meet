package com.github.meeplemeet.model.discussions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.CreateAccountViewModel
import kotlinx.coroutines.launch

class DiscussionDetailsViewModel(
    private val repository: DiscussionRepository = RepositoryProvider.discussions
) : CreateAccountViewModel() {
  private fun isAdmin(account: Account, discussion: Discussion): Boolean {
    return discussion.admins.contains(account.uid) || account.uid == discussion.creatorId
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
}
