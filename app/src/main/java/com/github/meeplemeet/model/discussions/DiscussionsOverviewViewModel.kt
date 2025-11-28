// Docs generated with Claude Code.
package com.github.meeplemeet.model.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.account.AccountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for displaying an overview of discussions.
 *
 * This ViewModel provides real-time flows for discussion data and implements [AccountViewModel] to
 * support account operations. It manages cached StateFlows to avoid duplicate Firestore listeners.
 *
 * @property discussionRepository Repository for discussion operations
 */
class DiscussionsOverviewViewModel(
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
    private val accountRepository: AccountRepository = RepositoryProvider.accounts
) : ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

  /**
   * Holds cached [StateFlow]s of discussions keyed by discussion ID to avoid duplicate listeners.
   */
  private val discussionFlows = mutableMapOf<String, StateFlow<Discussion?>>()

  /**
   * Cache of discussion IDs that have been validated to exist.
   *
   * Prevents redundant validation checks for the same discussions within a session.
   */
  private val checkedPreviews = mutableSetOf<String>()

  /**
   * Validates discussion previews for an account and removes orphaned previews.
   *
   * This method checks if each preview references a valid discussion document. If a discussion no
   * longer exists (e.g., it was deleted), the corresponding preview is removed from the account's
   * previews subcollection.
   *
   * The validation is performed in the background using a batched write for efficiency. Each
   * preview is only checked once per session to minimize Firestore reads.
   *
   * ## Use Case
   * Called when loading the discussions overview screen to ensure the preview list is synchronized
   * with actual discussions in Firestore.
   *
   * @param account The account whose previews should be validated
   */
  fun validatePreviews(account: Account) {
    val targets = account.previews.keys.filter { it !in checkedPreviews }

    if (targets.isEmpty()) return

    viewModelScope.launch {
      val batch = FirebaseProvider.db.batch()
      val toMarkValid = mutableListOf<String>()

      for (id in targets) {
        val exists = discussionRepository.previewIsValid(id)
        if (exists) toMarkValid.add(id)
        else batch.delete(accountRepository.previewToDeleteRef(account.uid, id))
      }

      batch.commit().await()
      checkedPreviews.addAll(toMarkValid)
    }
  }

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
