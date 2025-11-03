package com.github.meeplemeet.model.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.github.meeplemeet.model.discussions.SUGGESTIONS_LIMIT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HandlesViewModel(val repository: HandlesRepository = RepositoryProvider.handles) :
    ViewModel() {
  private val _errorMsg = MutableStateFlow("")
  val errorMessage: StateFlow<String> = _errorMsg

  private val _account = MutableStateFlow<Account?>(null)
  val account: StateFlow<Account?> = _account

  private val _handleSuggestions = MutableStateFlow<List<Account>>(emptyList())

  val handleSuggestions: StateFlow<List<Account>> = _handleSuggestions

  fun handleForAccountExists(account: Account) {
    viewModelScope.launch {
      val exists = repository.handleForAccountExists(account.uid, account.handle)
      _errorMsg.value =
          if (exists) {
            ""
          } else {
            "No handle associated to this account"
          }
    }
  }

  fun checkHandleAvailable(handle: String) {
    if (handle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!repository.validHandle(handle))
        _errorMsg.value = InvalidHandleFormatException.Companion.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          val exists = repository.checkHandleAvailable(handle)
          _errorMsg.value =
              if (exists) {
                HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
              } else {
                ""
              }
        }
  }

  fun createAccountHandle(account: Account, handle: String) {
    if (handle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!repository.validHandle(handle))
        _errorMsg.value = InvalidHandleFormatException.Companion.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          try {
            _account.value = repository.createAccountHandle(account.uid, handle)
          } catch (_: HandleAlreadyTakenException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          } catch (_: AccountNotFoundException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          }
        }
  }

  fun setAccountHandle(account: Account, newHandle: String) {
    if (newHandle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!repository.validHandle(newHandle))
        _errorMsg.value = InvalidHandleFormatException.Companion.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          try {
            _account.value = repository.setAccountHandle(account.uid, account.handle, newHandle)
          } catch (_: HandleAlreadyTakenException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          } catch (_: AccountNotFoundException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          }
        }
  }

  fun deleteAccountHandle(account: Account) {
    viewModelScope.launch { repository.deleteAccountHandle(account.handle) }
  }

  fun searchByHandle(prefix: String) {
    if (prefix.isBlank()) return
    viewModelScope.launch {
      repository.searchByHandle(prefix).collect { list ->
        _handleSuggestions.value = list.take(SUGGESTIONS_LIMIT)
      }
    }
  }
}
