// Docs generated with Claude Code.

package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.repositories.FirestorePostRepository
import com.github.meeplemeet.model.structures.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for creating new posts.
 *
 * This ViewModel handles the creation of posts and exposes the resulting post ID through a
 * [StateFlow] for UI observation.
 *
 * @property repository The repository used for post operations.
 */
class CreatePostViewModel(val repository: FirestorePostRepository = FirestorePostRepository()) :
    ViewModel() {

  private val _postId = MutableStateFlow<String?>(null)
  /**
   * StateFlow containing the ID of the most recently created post. Value is null before any post is
   * created.
   */
  val postId: StateFlow<String?> = _postId

  /**
   * Creates a new post in Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Upon successful creation, the
   * post ID is emitted through [postId].
   *
   * @param title The title of the post.
   * @param body The main content/body of the post.
   * @param author The UID of the user creating the post.
   * @param tags Optional list of tags for categorizing the post.
   */
  fun createPost(title: String, body: String, author: Account, tags: List<String> = emptyList()) {
    if (title.isBlank()) throw IllegalArgumentException("Cannot create a post with an empty title")

    if (body.isBlank()) throw IllegalArgumentException("Cannot create a post with an empty body")

    viewModelScope.launch {
      _postId.value = repository.createPost(title, body, author.uid, tags).id
    }
  }
}
