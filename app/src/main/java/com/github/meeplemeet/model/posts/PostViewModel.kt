// Docs generated with Claude Code.

package com.github.meeplemeet.model.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountViewModel
import com.github.meeplemeet.model.discussions.EDIT_MAX_THRESHOLD
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
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
class PostViewModel(private val repository: PostRepository = RepositoryProvider.posts) :
    ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

  /**
   * Edits an existing post in Firestore.
   *
   * Only the post's author can edit it, and edits must be made within the time threshold defined by
   * [EDIT_MAX_THRESHOLD]. This operation is performed asynchronously in the viewModelScope.
   *
   * @param author The account attempting to edit the post.
   * @param post The post to edit.
   * @param newTitle Optional new title for the post.
   * @param newBody Optional new content/body for the post.
   * @param newTags Optional new list of tags for the post.
   * @throws PermissionDeniedException if the requester is not the post's author.
   * @throws IllegalArgumentException if the edit is attempted after the time threshold, or if the
   *   new title or body is blank.
   */
  fun editPost(
      author: Account,
      post: Post,
      newTitle: String? = null,
      newBody: String? = null,
      newTags: List<String>? = null
  ) {
    if (post.authorId != author.uid)
        throw PermissionDeniedException("Another users post cannot be edited")

    require(Timestamp.now().toDate().time <= post.timestamp.toDate().time + EDIT_MAX_THRESHOLD) {
      "Can not edit a post after ${EDIT_MAX_THRESHOLD}ms it has been created"
    }

    require(!(newTitle != null && newTitle.isBlank())) {
      "Cannot create a post with an empty title"
    }

    require(!(newBody != null && newBody.isBlank())) { "Cannot create a post with an empty body" }

    viewModelScope.launch { repository.editPost(post.id, newTitle, newBody, newTags) }
  }

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
   * Edits an existing comment in Firestore.
   *
   * Only the comment's author can edit it, and edits must be made within the time threshold defined
   * by [EDIT_MAX_THRESHOLD]. This operation is performed asynchronously in the viewModelScope.
   *
   * @param author The account attempting to edit the comment.
   * @param post The post containing the comment.
   * @param comment The comment to edit.
   * @param newText The new text content for the comment.
   * @throws PermissionDeniedException if the requester is not the comment's author.
   * @throws IllegalArgumentException if the edit is attempted after the time threshold, or if the
   *   new text is blank.
   */
  fun editComment(author: Account, post: Post, comment: Comment, newText: String) {
    if (comment.authorId != author.uid)
        throw PermissionDeniedException("Another users post cannot be edited")

    require(Timestamp.now().toDate().time <= post.timestamp.toDate().time + EDIT_MAX_THRESHOLD) {
      "Can not edit a post after ${EDIT_MAX_THRESHOLD}ms it has been created"
    }

    require(!(newText.isBlank())) { "Cannot send a blank comment" }

    viewModelScope.launch { repository.editComment(post.id, comment.id, newText) }
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
