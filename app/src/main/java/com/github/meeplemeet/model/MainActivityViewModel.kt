package com.github.meeplemeet.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AccountRepository
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainActivityViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
    private val discussionRepository: DiscussionRepository = RepositoryProvider.discussions,
) : ViewModel() {

  fun signOut() {
    discussionFlows.clear()
    accountFlows.clear()
  }

  /** Holds a [StateFlow] of discussion preview maps keyed by account ID. */
  private val accountFlows = mutableMapOf<String, StateFlow<Account?>>()

  /**
   * Real-time flow of all discussion previews for an account.
   *
   * Emits a new map whenever any preview changes in Firestore.
   */
  fun accountFlow(accountId: String): StateFlow<Account?> {
    if (accountId.isBlank()) return MutableStateFlow(null)
    return accountFlows.getOrPut(accountId) {
      accountRepository
          .listenAccount(accountId)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
              initialValue = null)
    }
  }

  /** Holds a [StateFlow] of discussion documents keyed by discussion ID. */
  private val discussionFlows = mutableMapOf<String, StateFlow<Discussion?>>()

  /**
   * Real-time flow of a discussion document.
   *
   * Emits a new [Discussion] on every snapshot change, or `null` if the discussion does not exist
   * yet.
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
