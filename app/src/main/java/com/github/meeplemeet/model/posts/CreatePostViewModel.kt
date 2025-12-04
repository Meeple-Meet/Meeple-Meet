package com.github.meeplemeet.model.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.launch

/**
 * ViewModel for creating new posts.
 *
 * This ViewModel handles the creation of posts both online and offline. When offline, posts are
 * queued for later upload.
 */
class CreatePostViewModel(private val repository: PostRepository = RepositoryProvider.posts) :
    ViewModel() {

  /**
   * Creates a new post in Firestore or queues it for offline upload.
   *
   * This operation checks connectivity:
   * - Online: Posts immediately to Firestore
   * - Offline: Queues post for later upload
   *
   * @param title The title of the post.
   * @param body The main content/body of the post.
   * @param author The account creating the post.
   * @param tags Optional list of tags for categorizing the post.
   * @throws IllegalArgumentException if title or body is blank
   */
  fun createPost(title: String, body: String, author: Account, tags: List<String> = emptyList()) {
    require(title.isNotBlank()) { "Cannot create a post with an empty title" }
    require(body.isNotBlank()) { "Cannot create a post with an empty body" }

    viewModelScope.launch { OfflineModeManager.createPost(title, body, author.uid, tags) }
  }
}
