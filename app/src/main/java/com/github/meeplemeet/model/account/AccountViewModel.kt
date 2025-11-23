// Docs generated with Claude Code.
package com.github.meeplemeet.model.account

import com.github.meeplemeet.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Interface defining common account management operations for ViewModels.
 *
 * This interface provides a standard set of methods for ViewModels to interact with the account
 * repository. It uses coroutines to perform asynchronous operations and requires implementing
 * classes to provide a CoroutineScope.
 */
interface AccountViewModel {
  /** The CoroutineScope used for launching coroutines. */
  val scope: CoroutineScope

  /**
   * Retrieves an account by its ID.
   *
   * @param id The account ID to retrieve
   * @throws IllegalArgumentException if the ID is blank
   */
  fun getAccount(id: String) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")
    scope.launch { RepositoryProvider.accounts.getAccount(id) }
  }

  /**
   * Retrieves an account by ID and provides it to a callback.
   *
   * This method is useful when you need to fetch account data without updating the current
   * ViewModel's account state.
   *
   * @param id The account ID to retrieve
   * @param onResult Callback that receives the retrieved account
   * @throws IllegalArgumentException if the ID is blank
   */
  fun getOtherAccount(id: String, onResult: (Account) -> Unit) {
    if (id.isBlank()) throw IllegalArgumentException("Account id cannot be blank")
    scope.launch { onResult(RepositoryProvider.accounts.getAccount(id)) }
  }

  /**
   * Retrieves multiple accounts by their IDs and provides them to a callback.
   *
   * @param uids List of account IDs to retrieve
   * @param onResult Callback that receives the list of retrieved accounts
   */
  fun getAccounts(uids: List<String>, onResult: (List<Account>) -> Unit) {
    scope.launch { onResult(RepositoryProvider.accounts.getAccounts(uids)) }
  }

  /**
   * Updates the display name of an account.
   *
   * @param account The account to update
   * @param newName The new display name (blank names will be replaced with "~")
   */
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

  /**
   * Deletes an account from Firestore.
   *
   * @param account The account to delete
   */
  fun deleteAccount(account: Account) {
    scope.launch { RepositoryProvider.accounts.deleteAccount(account.uid) }
  }
}
