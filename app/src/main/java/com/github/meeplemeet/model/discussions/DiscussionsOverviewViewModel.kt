// Docs generated with Claude Code.
package com.github.meeplemeet.model.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for displaying an overview of discussions.
 *
 * This ViewModel provides real-time flows for discussion data and implements [AccountViewModel] to
 * support account operations. It manages cached StateFlows to avoid duplicate Firestore listeners.
 *
 * @property discussionRepository Repository for discussion operations
 */
class DiscussionsOverviewViewModel(
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions
) : ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

  /**
   * Holds cached [StateFlow]s of discussions keyed by discussion ID to avoid duplicate listeners.
   */
  private val discussionFlows = mutableMapOf<String, StateFlow<Discussion?>>()

  /**
   * Returns a real-time flow of discussion data for the specified discussion ID.
   *
   * This method manages a cache of StateFlows to avoid creating duplicate Firestore listeners. The
   * flow emits updated discussion data whenever changes occur in Firestore.
   *
   * @param discussionId The ID of the discussion to observe
   * @return A StateFlow that emits the discussion or null if the discussion doesn't exist or ID is
   *   blank
   */
  fun discussionFlow(discussionId: String): StateFlow<Discussion?> {
    if (discussionId.isBlank()) return MutableStateFlow(null)
    return discussionFlows.getOrPut(discussionId) {
      discussionRepository
          .listenDiscussion(discussionId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = null)
    }
  }
}
