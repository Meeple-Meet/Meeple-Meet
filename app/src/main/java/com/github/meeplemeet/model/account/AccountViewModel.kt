// Docs generated with Claude Code.
package com.github.meeplemeet.model.account

import android.content.Context
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

const val BLANK_ACCOUNT_ID_ERROR = "Account id cannot be blank"
const val MAX_NUMBER_ATTEMPT = 3
const val DELETE_ACCOUNT_COOLDOWN = 1000L

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
   * Retrieves an account by ID and provides it to a callback.
   *
   * This method is useful when you need to fetch account data without updating the current
   * ViewModel's account state.
   *
   * @param id The account ID to retrieve
   * @param onResult Callback that receives the retrieved account
   * @throws IllegalArgumentException if the ID is blank
   */
  fun getAccount(id: String, context: Context, onResult: (Account?) -> Unit) {
    require(id.isNotBlank()) { BLANK_ACCOUNT_ID_ERROR }
    scope.launch { OfflineModeManager.loadAccount(id, context) { onResult(it) } }
  }

  /**
   * Retrieves multiple accounts by their IDs and provides them to a callback.
   *
   * @param uids List of account IDs to retrieve
   * @param onResult Callback that receives the list of retrieved accounts
   */
  fun getAccounts(uids: List<String>, context: Context, onResult: (List<Account?>) -> Unit) {
    scope.launch { OfflineModeManager.loadAccounts(uids, context, scope) { onResult(it) } }
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
   * @param account Account to update its roles
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
   * Updates the notification privacy settings of an account.
   *
   * This method allows users to control who can send them notifications such as discussion
   * invitations or session invitations. The setting can be configured to accept notifications from
   * everyone, only friends, or no one.
   *
   * @param account The account to update
   * @param notificationSettings The new notification privacy setting
   */
  fun setAccountNotificationSettings(account: Account, notificationSettings: NotificationSettings) {
    scope.launch {
      RepositoryProvider.accounts.setAccountNotificationSettings(account.uid, notificationSettings)
    }
  }

  /**
   * Update account roles.
   *
   * @param account Account to update its roles
   * @param newEmail The new email address
   */
  fun setAccountEmail(account: Account, newEmail: String) {
    scope.launch { RepositoryProvider.accounts.setAccountEmail(account.uid, newEmail) }
  }

  /**
   * Update account description.
   *
   * @param account Account to update its description
   * @param newDescription The new description
   */
  fun setAccountDescription(account: Account, newDescription: String) {
    scope.launch { RepositoryProvider.accounts.setAccountDescription(account.uid, newDescription) }
  }

  /**
   * Update account photo URL.
   *
   * @param account Account to update its photo URL
   * @param newPhotoUrl The new photo URL
   */
  fun setAccountPhotoUrl(account: Account, newPhotoUrl: String) {
    scope.launch { RepositoryProvider.accounts.setAccountPhotoUrl(account.uid, newPhotoUrl) }
  }

  /**
   * Deletes an account and all associated data from Firestore.
   *
   * This method performs a comprehensive cleanup:
   * - Removes the account's unique handle
   * - Removes the account from all discussions and sessions they participated in
   * - Deletes all shops owned by the account
   * - Deletes all space rentals owned by the account
   * - Deletes the account document itself
   *
   * Note: This does NOT delete the Firebase Authentication account or profile picture from storage.
   * Those deletions are handled in ProfileScreenViewModel.deleteAccountWithReauth().
   *
   * IMPORTANT: This must be called with `await()` or within a suspend context to ensure completion
   * before Firebase Auth deletion, otherwise security rules will reject operations.
   *
   * @param account The account to delete
   * @return Deferred that completes when all Firestore deletions finish
   */
  fun deleteAccount(account: Account) =
      scope.async {
        RepositoryProvider.handles.deleteAccountHandle(account.handle)

        account.previews.forEach { (id, _) ->
          val disc = RepositoryProvider.discussions.getDiscussion(id)
          if (disc.session != null) {
            RepositoryProvider.sessions.updateSession(
                id, newParticipantList = disc.session.participants - account.uid)
          }
          RepositoryProvider.discussions.removeUserFromDiscussion(
              disc, account.uid, disc.creatorId == account.uid)
        }

        val (shops, spaces) = RepositoryProvider.accounts.getBusinessIds(account.uid)
        RepositoryProvider.shops.deleteShops(shops)
        RepositoryProvider.spaceRenters.deleteSpaceRenters(spaces)

        RepositoryProvider.accounts.deleteAccount(account.uid)
      }

  /**
   * Deletes the user's Firebase Authentication account with retry logic. This should be called
   * AFTER deleting Firestore data, as auth is needed for Firestore deletion. Attempts to delete the
   * account up to 3 times in case of transient network failures.
   *
   * @param onSuccess Callback invoked if account deletion succeeds
   * @param onFailure Callback invoked with error message if all retry attempts fail
   */
  fun deleteFirebaseAuthAccount(onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
    scope.launch {
      val maxRetries = MAX_NUMBER_ATTEMPT
      var attempt = 0
      var lastError: String? = null

      while (attempt < maxRetries) {
        attempt++
        val result = RepositoryProvider.authentication.deleteAuthAccount()

        result
            .onSuccess {
              onSuccess()
              return@launch
            }
            .onFailure { exception ->
              lastError = exception.message ?: "Unknown error occurred"
              if (attempt < maxRetries) {
                // Wait before retrying (exponential backoff: 1s, 2s, 3s)
                kotlinx.coroutines.delay(DELETE_ACCOUNT_COOLDOWN * attempt)
              }
            }
      }

      // If we get here, all retries failed
      onFailure(lastError ?: "Failed to delete account after $maxRetries attempts")
    }
  }
}
