// Docs generated with Claude Code.

package com.github.meeplemeet.model.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.AccountViewModel
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

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

  val posts: StateFlow<List<Post>>
  val errorMessage: StateFlow<String>

  init {
    // Delegate all flow logic to OfflineModeManager
    val (postsFlow, errorFlow) =
        OfflineModeManager.createPostsOverviewFlow(viewModelScope, repository)
    posts = postsFlow
    errorMessage = errorFlow
  }
}
