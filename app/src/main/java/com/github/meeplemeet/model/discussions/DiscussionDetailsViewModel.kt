// Docs generated with Claude Code.
package com.github.meeplemeet.model.discussions

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.CreateAccountViewModel
import com.github.meeplemeet.model.images.ImageRepository
import kotlinx.coroutines.launch

private const val ERROR_ADMIN_PERMISSION = "Only discussion admins can perform this operation"

/**
 * ViewModel for managing discussion details and settings.
 *
 * This ViewModel handles all operations related to modifying discussion properties, managing
 * participants, and handling admin permissions. It extends [CreateAccountViewModel] to provide
 * account management functionality.
 *
 * @property repository Repository for discussion operations
 * @property imageRepository Repository for image operations
 */
class DiscussionDetailsViewModel(
    private val repository: DiscussionRepository = RepositoryProvider.discussions,
    private val imageRepository: ImageRepository = RepositoryProvider.images
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
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

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
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch { repository.setDiscussionDescription(discussion.uid, description) }
  }

  /**
   * Deletes a discussion (creator-only operation).
   *
   * Only the creator of the discussion can delete it, even if other users are admins. This will
   * also delete all associated images including the discussion profile picture and all message
   * photo attachments.
   *
   * @param context Android context for accessing cache directory
   * @param discussion The discussion to delete
   * @param changeRequester The account requesting the deletion
   * @throws PermissionDeniedException if the requester is not the creator
   */
  fun deleteDiscussion(context: Context, discussion: Discussion, changeRequester: Account) {
    if (discussion.creatorId != changeRequester.uid)
        throw PermissionDeniedException("Only discussion owner can perform this operation")

    viewModelScope.launch { repository.deleteDiscussion(context, discussion) }
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
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

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
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)
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
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

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
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)
    if (discussion.creatorId == admin.uid)
        throw PermissionDeniedException("Cannot demote the owner of this discussion")

    viewModelScope.launch { repository.removeAdminFromDiscussion(discussion, admin.uid) }
  }

  /**
   * Upload and set a discussion profile picture (admin only).
   *
   * This method coordinates the profile picture update flow:
   * 1. Verifies the requester is an admin (throws PermissionDeniedException if not)
   * 2. Uploads photo to Firebase Storage at `discussions/{discussionId}/profile.webp`
   * 3. Updates discussion document with the download URL
   *
   * The photo is automatically processed (WebP conversion, 800px max dimension, 40% quality) by
   * [ImageRepository]. The operation is executed asynchronously in viewModelScope.
   *
   * ## Permission Model
   * Only discussion admins can set the profile picture. This method performs the permission check
   * before any operations. Non-admins will receive PermissionDeniedException.
   *
   * ## Typical Usage Flow
   *
   * ```kotlin
   * // After user selects photo
   * val cachedPath = ImageFileUtils.cacheUriToFile(context, photoUri)
   * try {
   *   viewModel.setDiscussionProfilePicture(discussion, account, context, cachedPath)
   *   // Success - UI will update via discussionFlow
   * } catch (e: PermissionDeniedException) {
   *   // Show error: "Only admins can change profile picture"
   * }
   * File(cachedPath).delete() // Clean up
   * ```
   *
   * @param discussion The discussion to update.
   * @param changeRequester The account requesting the change (must be admin).
   * @param context Android context for accessing storage and cache.
   * @param localPath Absolute file path to the local image (typically in app cache directory).
   * @throws PermissionDeniedException if changeRequester is not in discussion.admins list.
   * @see ImageFileUtils.cacheUriToFile for preparing gallery photos
   * @see ImageRepository.saveDiscussionProfilePicture for photo upload
   * @see DiscussionRepository.setDiscussionProfilePictureUrl for URL update
   * @see isAdmin for permission check logic
   */
  fun setDiscussionProfilePicture(
      discussion: Discussion,
      changeRequester: Account,
      context: Context,
      localPath: String
  ) {
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch {
      val downloadUrl =
          imageRepository.saveDiscussionProfilePicture(context, discussion.uid, localPath)
      repository.setDiscussionProfilePictureUrl(discussion.uid, downloadUrl)
    }
  }
}
