// Docs generated with Claude Code.

package com.github.meeplemeet.model.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountViewModel
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing post interactions and operations.
 *
 * This ViewModel handles post deletion, comment creation, and comment removal with appropriate
 * permission checks to ensure users can only modify their own content.
 *
 * @property repository The repository used for post operations.
 */
class PostViewModel(private val repository: PostRepository = RepositoryProvider.posts) :
    ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

  /**
   * Deletes a post from Firestore or removes it from offline queue.
   *
   * Only the post's author can delete it. This operation is performed asynchronously in the
   * viewModelScope.
   *
   * For offline-created posts (temp IDs), removes them from the queue. For normal posts, deletes
   * from Firestore.
   *
   * @param author The account attempting to delete the post.
   * @param post The post to delete.
   * @throws PermissionDeniedException if the requester is not the post's author.
   */
  fun deletePost(author: Account, post: Post) {
    if (post.authorId != author.uid)
        throw PermissionDeniedException("Another users post cannot be deleted")

    viewModelScope.launch { OfflineModeManager.deletePost(post) }
  }

  /**
   * Adds a comment or reply to a post.
   *
   * When offline, comments are queued and synced when back online. Comments appear immediately in
   * the UI. For offline-created posts, comments are not allowed until the post is uploaded.
   *
   * @param author The account creating the comment.
   * @param post The post being commented on.
   * @param parentId The ID of the parent (post ID for top-level comments, or comment ID for
   *   replies).
   * @param text The text content of the comment.
   * @throws IllegalArgumentException if the text is blank.
   */
  fun addComment(author: Account, post: Post, parentId: String, text: String) {
    if (text.isBlank()) throw IllegalArgumentException("Cannot send a blank comment")

    // Check if post is in the queue - don't allow comments on queued posts
    if (OfflineModeManager.offlineModeFlow.value.postsToAdd.containsKey(post.id)) {
      throw IllegalStateException("Cannot comment on a post that is not yet uploaded")
    }

    viewModelScope.launch { OfflineModeManager.addComment(post.id, text, author.uid, parentId) }
  }

  /**
   * Removes a comment and all its direct replies from a post.
   *
   * Only the comment's author can remove it. This operation requires an internet connection as
   * deletion is a destructive operation that should be immediate. The operation is performed
   * asynchronously in the viewModelScope.
   *
   * @param author The account attempting to remove the comment.
   * @param post The post containing the comment.
   * @param comment The comment to remove.
   * @throws PermissionDeniedException if the requester is not the comment's author.
   * @throws IllegalStateException if there is no internet connection.
   */
  fun removeComment(author: Account, post: Post, comment: Comment) {
    if (comment.authorId != author.uid)
        throw PermissionDeniedException("Another users comment cannot be deleted")

    if (!OfflineModeManager.hasInternetConnection.value) {
      throw IllegalStateException("Cannot delete comments while offline")
    }

    viewModelScope.launch { repository.removeComment(post.id, comment.id) }
  }

  /** Holds a [StateFlow] of discussion preview maps keyed by post ID. */
  private val postFlows = mutableMapOf<String, StateFlow<Post?>>()

  /**
   * Returns a StateFlow for a specific post, providing cache-first loading and real-time updates.
   *
   * Observes offline mode changes to show comments added offline immediately.
   */
  fun postFlow(postId: String): StateFlow<Post?> {
    return postFlows.getOrPut(postId) {
      OfflineModeManager.postFlow(postId, viewModelScope, repository)
    }
  }
}
