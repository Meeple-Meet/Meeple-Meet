// Docs generated with Claude Code.

package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.repositories.FirestorePostRepository
import com.github.meeplemeet.model.structures.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the overview/feed of posts.
 *
 * This ViewModel fetches and exposes a list of posts without their comments for preview purposes.
 * It maintains the posts in a StateFlow that UI components can observe for reactive updates.
 *
 * @property repository The repository for accessing post data from Firestore.
 */
class PostOverviewViewModel(
    private val repository: FirestorePostRepository = FirestorePostRepository()
) : ViewModel() {

  private val _posts = MutableStateFlow<List<Post>>(emptyList())

  /**
   * StateFlow containing the current list of posts.
   *
   * Posts are provided without their comments for efficient preview display. The list is sorted by
   * timestamp with newest posts first. UI components can collect this flow to observe updates.
   */
  val posts: StateFlow<List<Post>> = _posts

  private val _errorMsg = MutableStateFlow("")
  val errorMessage: StateFlow<String> = _errorMsg

  /**
   * Fetches all posts from the repository and updates the [posts] StateFlow.
   *
   * This function launches a coroutine in the viewModelScope to fetch posts asynchronously. Posts
   * are retrieved without their comments for efficient loading in overview/feed screens.
   */
  fun getPosts() {
    viewModelScope.launch {
      try {
        val fetchedPosts = repository.getPosts()
        _posts.value = fetchedPosts
        _errorMsg.value = ""
      } catch (_: Exception) {
        _posts.value = emptyList()
        _errorMsg.value = "An error occurred while getting all posts."
      }
    }
  }
}
