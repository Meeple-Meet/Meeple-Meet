// Docs generated with Claude Code.

package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.repositories.FirestorePostRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Comment
import com.github.meeplemeet.model.structures.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for managing post interactions and operations.
 *
 * This ViewModel handles post deletion, comment creation, and comment removal with appropriate
 * permission checks to ensure users can only modify their own content.
 *
 * @property repository The repository used for post operations.
 */
class PostViewModel(val repository: FirestorePostRepository = FirestorePostRepository()) :
    ViewModel() {
  /**
   * Deletes a post from Firestore.
   *
   * Only the post's author can delete it. This operation is performed asynchronously in the
   * viewModelScope.
   *
   * @param author The account attempting to delete the post.
   * @param post The post to delete.
   * @throws PermissionDeniedException if the requester is not the post's author.
   */
  fun deletePost(author: Account, post: Post) {
    if (post.authorId != author.uid)
        throw PermissionDeniedException("Another users post cannot be deleted")

    viewModelScope.launch { repository.deletePost(post.id) }
  }

  /**
   * Adds a comment or reply to a post.
   *
   * To add a top-level comment, set [parentId] to the post's ID. To add a reply to another comment,
   * set [parentId] to that comment's ID. This operation is performed asynchronously in the
   * viewModelScope.
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

    viewModelScope.launch { repository.addComment(post.id, text, author.uid, parentId) }
  }

  /**
   * Removes a comment and all its direct replies from a post.
   *
   * Only the comment's author can remove it. This operation cascades to delete all replies to the
   * specified comment. The operation is performed asynchronously in the viewModelScope.
   *
   * @param author The account attempting to remove the comment.
   * @param post The post containing the comment.
   * @param comment The comment to remove.
   * @throws PermissionDeniedException if the requester is not the comment's author.
   */
  fun removeComment(author: Account, post: Post, comment: Comment) {
    if (comment.authorId != author.uid)
        throw PermissionDeniedException("Another users comment cannot be deleted")

    viewModelScope.launch { repository.removeComment(post.id, comment.id) }
  }

  /** Holds a [StateFlow] of discussion preview maps keyed by post ID. */
  private val postFlows = mutableMapOf<String, StateFlow<Post?>>()

  fun postFlow(postId: String): StateFlow<Post?> {
    if (postId.isBlank()) return MutableStateFlow(null)
    return postFlows.getOrPut(postId) {
      repository
          .listenPost(postId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = null)
    }
  }
}
