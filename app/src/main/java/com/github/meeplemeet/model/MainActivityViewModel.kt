// Docs generated with Claude Code.
package com.github.meeplemeet.model

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.auth.AuthenticationViewModel
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Main view model for the application that provides real-time data flows for accounts and
 * discussions.
 *
 * This ViewModel extends [AuthenticationViewModel] to provide authentication functionality while
 * adding support for listening to real-time updates from Firestore. It manages cached StateFlows
 * for accounts and discussions to avoid duplicate listeners.
 *
 * @property accountRepository Repository for account operations
 * @property discussionRepository Repository for discussion operations
 */
class MainActivityViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
) : AuthenticationViewModel() {

  /**
   * Signs out the current user and clears all cached flows.
   *
   * Overrides the parent signOut method to ensure that all Firestore listeners are removed when the
   * user signs out.
   */
  override fun signOut() {
    super.signOut()
  }

  // Holds cached [StateFlow]s of accounts keyed by account ID to avoid duplicate listeners.
  private val accountFlows = mutableMapOf<String, StateFlow<Account?>>()

  /**
   * Returns a real-time flow of account data for the specified account ID.
   *
   * This method integrates both online and offline modes:
   * - When online: Creates a Firestore listener that emits real-time updates and caches data
   * - When offline: Loads account from the offline cache using OfflineModeManager
   *
   * The flow combines the internet connection state with the appropriate data source, automatically
   * switching between online (Firestore) and offline (cached) data as connectivity changes.
   *
   * ## Caching Behavior
   * - When online, accounts are loaded via OfflineModeManager which caches them
   * - When offline, accounts are retrieved from OfflineModeManager cache
   * - When transitioning from offline to online, the flow switches to live Firestore data
   *
   * @param accountId The ID of the account to observe
   * @param context Android context for accessing offline storage (required for offline mode)
   * @return A [StateFlow] that emits the account or null if the account doesn't exist or ID is
   *   blank
   */
  fun accountFlow(accountId: String, context: Context): StateFlow<Account?> {
    if (accountId.isBlank()) return MutableStateFlow(null)

    // Create a flow that combines online/offline state with account data
    val accountDataFlow = MutableStateFlow<Account?>(null)

    // Listen to connectivity changes and load account accordingly
    viewModelScope.launch {
      OfflineModeManager.hasInternetConnection.collect {
        // Always use OfflineModeManager.loadAccount - it handles both cache and fetch
        OfflineModeManager.loadAccount(accountId, context, true) { account ->
          accountDataFlow.value = account
        }
      }
    }

    // Combine connectivity state with account data and listen to Firestore when online
    return combine(
            OfflineModeManager.hasInternetConnection,
            accountRepository.listenAccount(accountId),
            accountDataFlow) { isOnline, liveAccount, loadedAccount ->
              // When online, prefer live Firestore data
              // When offline, use the loaded account from cache
              if (isOnline) liveAccount else loadedAccount
            }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue = null)
  }

  /**
   * Returns a real-time flow of discussion data for the specified discussion ID with offline
   * support.
   *
   * The flow automatically switches between online and offline modes:
   * - **Online:** Emits real-time updates from Firestore
   * - **Offline:** Emits data from the offline cache loaded via [OfflineModeManager]
   *
   * The discussion is automatically loaded into the offline cache when first accessed, enabling
   * offline viewing later.
   *
   * @param discussionId The ID of the discussion to observe
   * @return A [StateFlow] that emits the discussion or null if the discussion doesn't exist or ID
   *   is blank
   */
  fun discussionFlow(discussionId: String): StateFlow<Discussion?> {
    if (discussionId.isBlank()) return MutableStateFlow(null)

    // Combine online/offline state with live data and cached data
    return combine(
            OfflineModeManager.hasInternetConnection,
            discussionRepository.listenDiscussion(discussionId),
            OfflineModeManager.offlineModeFlow) { isOnline, liveDiscussion, offlineMode ->
              if (isOnline) {
                liveDiscussion
              } else {
                offlineMode.discussions[discussionId]?.first
              }
            }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue =
                OfflineModeManager.offlineModeFlow.value.discussions[discussionId]?.first)
  }
}
