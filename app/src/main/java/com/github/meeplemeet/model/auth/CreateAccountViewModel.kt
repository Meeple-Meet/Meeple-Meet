// Docs generated with Claude Code.
package com.github.meeplemeet.model.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.github.meeplemeet.model.discussions.SUGGESTIONS_LIMIT
import com.github.meeplemeet.model.shared.DEBOUNCE_TIME_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * ViewModel for creating and managing account handles.
 *
 * This ViewModel provides functionality for handle validation, creation, and searching. It extends
 * [ViewModel] and implements [AccountViewModel] to provide both lifecycle awareness and account
 * management capabilities.
 *
 * @property handlesRepository Repository for handle operations
 */
@OptIn(FlowPreview::class)
open class CreateAccountViewModel(
    val handlesRepository: HandlesRepository = RepositoryProvider.handles,
) : ViewModel(), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

  /** StateFlow containing error messages for handle operations. */
  private val _errorMsg = MutableStateFlow("")
  val errorMessage: StateFlow<String> = _errorMsg

  /** StateFlow containing account suggestions from handle searches. */
  private val handleQueryFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
  private val _handleSuggestions = MutableStateFlow<List<Account>>(emptyList())
  val handleSuggestions: StateFlow<List<Account>> = _handleSuggestions

  init {
    viewModelScope.launch {
      handleQueryFlow.debounce(DEBOUNCE_TIME_MS).collectLatest { prefix ->
        if (prefix.isBlank()) {
          _handleSuggestions.value = emptyList()
          return@collectLatest
        }

        try {
          // searchByHandle returns a Flow<List<Handle>>
          handlesRepository.searchByHandle(prefix).collect { list ->
            _handleSuggestions.value = list.take(SUGGESTIONS_LIMIT)
          }
        } catch (_: Exception) {
          _handleSuggestions.value = emptyList()
          // optionally set an error StateFlow here, similar to gameSearchError/locationSearchError
        }
      }
    }
  }

  /**
   * Checks if a handle exists for the given account.
   *
   * Updates [errorMessage] based on whether the handle exists.
   *
   * @param account The account to check
   */
  fun handleForAccountExists(account: Account) {
    viewModelScope.launch {
      val exists = handlesRepository.handleForAccountExists(account.uid, account.handle)
      _errorMsg.value =
          if (exists) {
            ""
          } else {
            "No handle associated to this account"
          }
    }
  }

  /**
   * Checks if a handle is available for use.
   *
   * Validates the handle format and availability, updating [errorMessage] with any issues found.
   *
   * @param handle The handle to check
   */
  fun checkHandleAvailable(handle: String) {
    if (handle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!handlesRepository.validHandle(handle))
        _errorMsg.value = InvalidHandleFormatException.Companion.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          val exists = handlesRepository.checkHandleAvailable(handle)
          _errorMsg.value =
              if (exists) {
                HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
              } else {
                ""
              }
        }
  }

  /**
   * Creates a new handle for an account and updates account information.
   *
   * This method performs validation, creates the handle, and optionally updates the account's name
   * and roles.
   *
   * @param account The account to create a handle for
   * @param handle The handle to create
   * @param username The display name to set for the account
   * @param shopOwner Optional flag to set the shop owner role
   * @param spaceRenter Optional flag to set the space renter role
   */
  fun createAccountHandle(
      account: Account,
      handle: String,
      username: String,
      shopOwner: Boolean? = null,
      spaceRenter: Boolean? = null
  ) {
    if (handle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!handlesRepository.validHandle(handle))
        _errorMsg.value = InvalidHandleFormatException.Companion.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          try {
            handlesRepository.createAccountHandle(account.uid, handle)
            setAccountName(account, username)
            setAccountRole(account, shopOwner, spaceRenter)
          } catch (_: HandleAlreadyTakenException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          } catch (_: AccountNotFoundException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          }
        }
  }

  /**
   * Updates the handle for an existing account.
   *
   * Validates the new handle and updates it in the repository.
   *
   * @param account The account whose handle should be updated
   * @param newHandle The new handle to set
   */
  fun setAccountHandle(account: Account, newHandle: String) {
    if (newHandle.isBlank()) _errorMsg.value = "Handle can not be blank"
    else if (!handlesRepository.validHandle(newHandle))
        _errorMsg.value = InvalidHandleFormatException.Companion.DEFAULT_MESSAGE
    else
        viewModelScope.launch {
          try {
            handlesRepository.setAccountHandle(account.uid, account.handle, newHandle)
          } catch (_: HandleAlreadyTakenException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          } catch (_: AccountNotFoundException) {
            _errorMsg.value = HandleAlreadyTakenException.Companion.DEFAULT_MESSAGE
          }
        }
  }

  /**
   * Deletes the handle for an account.
   *
   * @param account The account whose handle should be deleted
   */
  fun deleteAccountHandle(account: Account) {
    viewModelScope.launch { handlesRepository.deleteAccountHandle(account.handle) }
  }

  /**
   * Searches for accounts by handle prefix.
   *
   * Updates [handleSuggestions] with matching accounts (limited to SUGGESTIONS_LIMIT).
   *
   * @param prefix The handle prefix to search for
   */
  fun searchByHandle(prefix: String) {
    // Keep behaviour consistent with game/location: blank query clears suggestions
    if (prefix.isBlank()) {
      _handleSuggestions.value = emptyList()
      return
    }

    handleQueryFlow.tryEmit(prefix)
  }
}
