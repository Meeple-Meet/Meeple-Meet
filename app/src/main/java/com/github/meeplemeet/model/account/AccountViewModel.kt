// Docs generated with Claude Code.
package com.github.meeplemeet.model.account

import android.content.Context
import com.github.meeplemeet.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val BLANK_ACCOUNT_ID_ERROR = "Account id cannot be blank"

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
    require(id.isNotBlank()) { BLANK_ACCOUNT_ID_ERROR }
    scope.launch { RepositoryProvider.accounts.getAccount(id, false) }
  }

  /**
   * Retrieves multiple accounts by their IDs and provides them to a callback.
   *
   * Additionally, this method preloads each account's profile picture in the provided context.
   *
   * @param uids List of account IDs to retrieve
   * @param context Context used for loading profile pictures
   * @param onResult Callback that receives the list of retrieved accounts
   */
  fun getAccounts(uids: List<String>, context: Context, onResult: (List<Account>) -> Unit) {
    scope.launch {
      val accounts = RepositoryProvider.accounts.getAccounts(uids)
      onResult(accounts)
      accounts.forEach { account ->
        launch {
          runCatching { RepositoryProvider.images.loadAccountProfilePicture(account.uid, context) }
        }
      }
    }
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
    require(id.isNotBlank()) { BLANK_ACCOUNT_ID_ERROR }
    scope.launch { onResult(RepositoryProvider.accounts.getAccount(id, false)) }
  }

  /**
   * Retrieves an account by ID and provides it to a callback, preloading its profile picture.
   *
   * @param id The account ID to retrieve
   * @param context Context used for loading the profile picture
   * @param onResult Callback that receives the retrieved account
   */
  fun getOtherAccount(id: String, context: Context, onResult: (Account) -> Unit) {
    require(id.isNotBlank()) { BLANK_ACCOUNT_ID_ERROR }
    scope.launch {
      val account = RepositoryProvider.accounts.getAccount(id, false)
      onResult(account)
      runCatching { RepositoryProvider.images.loadAccountProfilePicture(account.uid, context) }
    }
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
   * Deletes an account from Firestore.
   *
   * @param account The account to delete
   */
  fun deleteAccount(account: Account) {
    scope.launch {
      // Remove the user's handle
      RepositoryProvider.handles.deleteAccountHandle(account.handle)

      // Leave all the discussions and sessions the account is a part of
      account.previews.forEach { (id, _) ->
        val disc = RepositoryProvider.discussions.getDiscussion(id)
        if (disc.session != null)
            RepositoryProvider.sessions.updateSession(
                id, newParticipantList = disc.session.participants - account.uid)
        RepositoryProvider.discussions.removeUserFromDiscussion(
            disc, account.uid, disc.creatorId == account.uid)
      }

      // Delete all shops and spaces owned by the account
      val (shops, spaces) = RepositoryProvider.accounts.getBusinessIds(account.uid)
      RepositoryProvider.shops.deleteShops(shops)
      RepositoryProvider.spaceRenters.deleteSpaceRenters(spaces)

      // Delete the account
      RepositoryProvider.accounts.deleteAccount(account.uid)
    }
  }
}
