package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.github.meeplemeet.model.repositories.FirestoreHandlesRepository
import com.github.meeplemeet.model.structures.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FirestoreHandlesViewModel(
    private val repository: FirestoreHandlesRepository = FirestoreHandlesRepository()
) : ViewModel() {
  private val _errorMsg = MutableStateFlow("")
  val errorMessage: StateFlow<String> = _errorMsg

  private val _account = MutableStateFlow<Account?>(null)
  val account: StateFlow<Account?> = _account

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
        _errorMsg.value = InvalidHandleFormatException.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          val exists = repository.checkHandleAvailable(handle)
          _errorMsg.value =
              if (exists) {
                HandleAlreadyTakenException.DEFAULT_MESSAGE
              } else {
                ""
              }
        }
  }

  fun createAccountHandle(account: Account, handle: String) {
    if (handle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!repository.validHandle(handle))
        _errorMsg.value = InvalidHandleFormatException.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          try {
            _account.value = repository.createAccountHandle(account.uid, handle)
          } catch (_: HandleAlreadyTakenException) {
            _errorMsg.value = HandleAlreadyTakenException.DEFAULT_MESSAGE
          } catch (_: AccountNotFoundException) {
            _errorMsg.value = HandleAlreadyTakenException.DEFAULT_MESSAGE
          }
        }
  }

  fun setAccountHandle(account: Account, newHandle: String) {
    if (newHandle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!repository.validHandle(newHandle))
        _errorMsg.value = InvalidHandleFormatException.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          try {
            _account.value = repository.setAccountHandle(account.uid, account.handle, newHandle)
          } catch (_: HandleAlreadyTakenException) {
            _errorMsg.value = HandleAlreadyTakenException.DEFAULT_MESSAGE
          } catch (_: AccountNotFoundException) {
            _errorMsg.value = HandleAlreadyTakenException.DEFAULT_MESSAGE
          }
        }
  }

  fun deleteAccountHandle(account: Account) {
    viewModelScope.launch { repository.deleteAccountHandle(account.handle) }
  }
}
