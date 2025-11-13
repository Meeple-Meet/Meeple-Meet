// Docs generated with Claude Code.
package com.github.meeplemeet.model.discussions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.CreateAccountViewModel
import kotlinx.coroutines.launch

/**
 * ViewModel for managing discussion details and settings.
 *
 * This ViewModel handles all operations related to modifying discussion properties, managing
 * participants, and handling admin permissions. It extends [CreateAccountViewModel] to provide
 * account management functionality.
 *
 * @property repository Repository for discussion operations
 */
class DiscussionDetailsViewModel(
    private val repository: DiscussionRepository = RepositoryProvider.discussions
) : CreateAccountViewModel() {
  /**
   * Checks if an account has admin privileges for a discussion.
   *
   * An account is considered an admin if they are in the discussion's admin list or if they are the
   * creator of the discussion.
   *
   * @param account The account to check
   * @param discussion The discussion to check permissions for
   * @return True if the account has admin privileges, false otherwise
   */
  private fun isAdmin(account: Account, discussion: Discussion): Boolean {
    return discussion.admins.contains(account.uid) || account.uid == discussion.creatorId
  }

  /**
   * Updates the name of a discussion (admin-only operation).
   *
   * If the provided name is blank, it defaults to "Discussion with: {participant names}".
   *
   * @param discussion The discussion to update
   * @param changeRequester The account requesting the change
   * @param name The new discussion name
   * @throws PermissionDeniedException if the requester is not an admin
   */
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

  /**
   * Updates the description of a discussion (admin-only operation).
   *
   * @param discussion The discussion to update
   * @param changeRequester The account requesting the change
   * @param description The new discussion description
   * @throws PermissionDeniedException if the requester is not an admin
   */
  fun setDiscussionDescription(
      discussion: Discussion,
      changeRequester: Account,
      description: String
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.setDiscussionDescription(discussion.uid, description) }
  }

  /**
   * Deletes a discussion (creator-only operation).
   *
   * Only the creator of the discussion can delete it, even if other users are admins.
   *
   * @param discussion The discussion to delete
   * @param changeRequester The account requesting the deletion
   * @throws PermissionDeniedException if the requester is not the creator
   */
  fun deleteDiscussion(discussion: Discussion, changeRequester: Account) {
    if (discussion.creatorId != changeRequester.uid)
        throw PermissionDeniedException("Only discussion owner can perform this operation")

    viewModelScope.launch { repository.deleteDiscussion(discussion) }
  }

  /**
   * Adds a user to a discussion (admin-only operation).
   *
   * If the user is already a participant, this method does nothing.
   *
   * @param discussion The discussion to add the user to
   * @param changeRequester The account requesting the change
   * @param user The account to add as a participant
   * @throws PermissionDeniedException if the requester is not an admin
   */
  fun addUserToDiscussion(discussion: Discussion, changeRequester: Account, user: Account) {
    if (discussion.participants.contains(user.uid)) return
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.addUserToDiscussion(discussion, user.uid) }
  }

  /**
   * Removes a user from a discussion (admin-only or self-removal).
   *
   * Users can remove themselves from a discussion. Admins can remove other users, but cannot remove
   * the discussion creator unless they are the creator themselves.
   *
   * @param discussion The discussion to remove the user from
   * @param changeRequester The account requesting the change
   * @param user The account to remove from the discussion
   * @throws PermissionDeniedException if the requester is not an admin (and not removing
   *   themselves) or if trying to remove the creator
   */
  fun removeUserFromDiscussion(discussion: Discussion, changeRequester: Account, user: Account) {
    if (changeRequester != user && !isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (discussion.creatorId == user.uid && changeRequester.uid != discussion.creatorId)
        throw PermissionDeniedException("Cannot remove the owner of this discussion")

    viewModelScope.launch {
      repository.removeUserFromDiscussion(discussion, user.uid, discussion.creatorId == user.uid)
    }
  }

  /**
   * Adds a user as an admin of a discussion (admin-only operation).
   *
   * @param discussion The discussion to add the admin to
   * @param changeRequester The account requesting the change
   * @param admin The account to grant admin privileges
   * @throws PermissionDeniedException if the requester is not an admin
   */
  fun addAdminToDiscussion(discussion: Discussion, changeRequester: Account, admin: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.addAdminToDiscussion(discussion, admin.uid) }
  }

  /**
   * Removes admin privileges from a user (admin-only operation).
   *
   * The discussion creator cannot have their admin privileges removed.
   *
   * @param discussion The discussion to modify
   * @param changeRequester The account requesting the change
   * @param admin The account to revoke admin privileges from
   * @throws PermissionDeniedException if the requester is not an admin or if trying to demote the
   *   creator
   */
  fun removeAdminFromDiscussion(discussion: Discussion, changeRequester: Account, admin: Account) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
    if (discussion.creatorId == admin.uid)
        throw PermissionDeniedException("Cannot demote the owner of this discussion")

    viewModelScope.launch { repository.removeAdminFromDiscussion(discussion, admin.uid) }
  }
}
