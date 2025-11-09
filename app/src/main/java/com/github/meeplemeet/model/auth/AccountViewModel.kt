package com.github.meeplemeet.model.auth

import com.github.meeplemeet.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AccountViewModel {
  val scope: CoroutineScope

  /** Retrieve an account by ID. */
  fun getAccount(id: String) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")
    scope.launch { RepositoryProvider.accounts.getAccount(id) }
  }

  /** Retrieve an account by ID without changing the current account state. */
  fun getOtherAccount(id: String, onResult: (Account) -> Unit) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")
    scope.launch { onResult(RepositoryProvider.accounts.getAccount(id)) }
  }

  fun getAccounts(uids: List<String>, onResult: (List<Account>) -> Unit) {
    scope.launch { onResult(RepositoryProvider.accounts.getAccounts(uids)) }
  }

  /** Update account name. */
  fun setAccountName(account: Account, newName: String) {
    scope.launch {
      RepositoryProvider.accounts.setAccountName(account.uid, newName.ifBlank { "~" })
    }
  }

  /**
   * Update account roles.
   *
   * @param account Account to update it's roles
   * @param isShopOwner Boolean for the role Shop Owner
   * @param isSpaceRenter Boolean for the role Space Renter
   */
  fun setAccountRole(
      account: Account,
      isShopOwner: Boolean? = null,
      isSpaceRenter: Boolean? = null
  ) {
    scope.launch {
      RepositoryProvider.accounts.setAccountRole(
          account.uid, isShopOwner = isShopOwner, isSpaceRenter = isSpaceRenter)
    }
  }

  /** Delete an account. */
  fun deleteAccount(account: Account) {
    scope.launch { RepositoryProvider.accounts.deleteAccount(account.uid) }
  }
}
