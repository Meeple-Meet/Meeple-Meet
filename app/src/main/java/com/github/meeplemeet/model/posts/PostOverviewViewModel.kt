// Docs generated with Claude Code.

package com.github.meeplemeet.model.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel for managing the overview/feed of posts.
 *
 * This ViewModel listens to real-time updates of posts and exposes them without their comments for
 * preview purposes. It maintains the posts in a StateFlow that UI components can observe for
 * reactive updates.
 *
 * @property repository The repository for accessing post data from Firestore.
 */
class PostOverviewViewModel(private val repository: PostRepository = RepositoryProvider.posts) :
    ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

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

  init {
    startListening()
  }

  /**
   * Starts listening to real-time updates of all posts from the repository.
   *
   * This function launches a coroutine in the viewModelScope to collect posts from the repository's
   * Flow. Posts are retrieved without their comments for efficient loading in overview/feed
   * screens. The listener remains active for the lifetime of the ViewModel, automatically updating
   * the UI when posts are added, modified, or removed in Firestore.
   */
  private fun startListening() {
    viewModelScope.launch {
      repository
          .listenPosts()
          .catch { e ->
            _posts.value = emptyList()
            _errorMsg.value = "An error occurred while listening to posts: ${e.message}"
          }
          .collect { fetchedPosts ->
            _posts.value = fetchedPosts
            _errorMsg.value = ""
          }
    }
  }

  /**
   * Fetches all posts from the repository and updates the [posts] StateFlow.
   *
   * This function launches a coroutine in the viewModelScope to fetch posts asynchronously. Posts
   * are retrieved without their comments for efficient loading in overview/feed screens.
   *
   * @deprecated This method is no longer needed as the ViewModel automatically listens to posts on
   *   initialization. Kept for backward compatibility.
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
