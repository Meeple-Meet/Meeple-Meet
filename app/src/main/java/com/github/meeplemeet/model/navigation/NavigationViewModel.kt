package com.github.meeplemeet.model.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.AccountRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NavigationViewModel(
    private val repo: AccountRepository = RepositoryProvider.accounts,
) : ViewModel() {

  private val _unreadCount = MutableStateFlow(0)
  val unreadCount: StateFlow<Int> = _unreadCount

  private var listeningJob: Job? = null

  fun startListening(accountId: String) {
    // Cancel any existing listener
    listeningJob?.cancel()

    // Don't start listening if accountId is blank
    if (accountId.isBlank()) {
      _unreadCount.value = 0
      return
    }

    listeningJob =
        viewModelScope.launch {
          repo.listenAccount(accountId).collect { account ->
            val count = account.notifications.count { !it.read }
            _unreadCount.value = count
          }
        }
  }
}
