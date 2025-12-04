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
import kotlinx.coroutines.flow.StateFlow
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
   * Each key is a combination of discussion ID and context hash to support different contexts.
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
}
