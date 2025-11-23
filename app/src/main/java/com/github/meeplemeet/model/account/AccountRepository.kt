// Docs generated with Claude Code.
package com.github.meeplemeet.model.account

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.discussions.DiscussionPreviewNoUid
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing user account data in Firestore.
 *
 * This repository handles CRUD operations for account documents and their associated discussion
 * previews. It extends [FirestoreRepository] to inherit common Firestore functionality.
 *
 * Each account document contains:
 * - Basic profile information (handle, name, email, photo)
 * - Role flags (shop owner, space renter)
 * - A subcollection of discussion previews
 */
class AccountRepository : FirestoreRepository("accounts") {
  /**
   * Creates a new account document in Firestore.
   *
   * @param userHandle The unique handle for the user (also used as the document ID)
   * @param name The user's display name
   * @param email The user's email address
   * @param photoUrl The URL of the user's profile photo, or null if not available
   * @return The created Account object
   */
  suspend fun createAccount(
      userHandle: String,
      name: String,
      email: String,
      photoUrl: String?
  ): Account {
    val account =
        Account(
            userHandle, userHandle, name, email = email, photoUrl = photoUrl, description = null)
    val accountNoUid = AccountNoUid(userHandle, name, email, photoUrl, description = null)
    collection.document(account.uid).set(accountNoUid).await()
    return account
  }

  /**
   * Retrieves an account and its associated discussion previews by ID.
   *
   * @param id The account ID to retrieve
   * @return The Account object with populated discussion previews
   * @throws AccountNotFoundException if the account does not exist
   */
  suspend fun getAccount(id: String): Account {
    val snapshot = collection.document(id).get().await()
    val account = snapshot.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()

    val previewsSnap = collection.document(id).collection("previews").get().await()
    val previews: Map<String, DiscussionPreviewNoUid> =
        previewsSnap.documents.associate { doc ->
          doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java)!!)
        }

    return fromNoUid(id, account, previews)
  }

  /**
   * Retrieves multiple accounts and their discussion previews concurrently.
   *
   * This method fetches all accounts in parallel using coroutines for optimal performance.
   *
   * @param ids List of account IDs to retrieve
   * @return List of Account objects corresponding to the provided IDs
   * @throws AccountNotFoundException if any of the accounts do not exist
   */
  suspend fun getAccounts(ids: List<String>): List<Account> = coroutineScope {
    ids.map { id ->
          async {
            val accountSnap = collection.document(id).get().await()
            val account =
                accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()

            val previewsSnap = collection.document(id).collection("previews").get().await()
            val previews =
                previewsSnap.documents.associate { doc ->
                  doc.id to (doc.toObject(DiscussionPreviewNoUid::class.java)!!)
                }

            fromNoUid(id, account, previews)
          }
        }
        .awaitAll()
  }

  /**
   * Updates the display name of an account.
   *
   * @param id The account ID to update
   * @param name The new display name
   */
  suspend fun setAccountName(id: String, name: String) {
    collection.document(id).update(Account::name.name, name).await()
  }

  /**
   * Update account roles in the repository (space renter & shop owner)
   *
   * @param id Account id's to update it's roles
   * @param isShopOwner Boolean for the role Shop Owner
   * @param isSpaceRenter Boolean for the role Space Renter
   */
  suspend fun setAccountRole(id: String, isShopOwner: Boolean?, isSpaceRenter: Boolean?) {
    val updates = mutableMapOf<String, Any>()
    isShopOwner?.let { updates[AccountNoUid::shopOwner.name] = isShopOwner }
    isSpaceRenter?.let { updates[AccountNoUid::spaceRenter.name] = isSpaceRenter }
    collection.document(id).update(updates).await()
  }

  /**
   * Deletes an account document from Firestore.
   *
   * @param id The account ID to delete
   */
  suspend fun deleteAccount(id: String) {
    collection.document(id).delete().await()
  }

  /**
   * Creates a Flow that listens for real-time changes to an account and its discussion previews.
   *
   * This method sets up Firestore listeners that emit updated account data whenever changes occur.
   * The flow emits a complete Account object including all discussion previews. The listeners are
   * automatically cleaned up when the flow is cancelled.
   *
   * @param accountId The ID of the account to listen to
   * @return A Flow that emits Account objects when changes are detected
   */
  fun listenAccount(accountId: String): Flow<Account> = callbackFlow {
    val accountRef = collection.document(accountId)

    var previewsListener: ListenerRegistration? = null

    val accountListener =
        accountRef.addSnapshotListener { snapshot, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

          val accountNoUid = snapshot.toObject(AccountNoUid::class.java) ?: AccountNoUid()

          // Remove old previews listener before adding a new one
          previewsListener?.remove()
          previewsListener =
              accountRef.collection(Account::previews.name).addSnapshotListener { qs, e2 ->
                if (e2 != null) {
                  close(e2)
                  return@addSnapshotListener
                }
                if (qs != null) {
                  val previews =
                      qs.documents.associate { d ->
                        d.id to
                            (d.toObject(DiscussionPreviewNoUid::class.java)
                                ?: DiscussionPreviewNoUid())
                      }
                  trySend(fromNoUid(accountId, accountNoUid, previews))
                }
              }
        }

    awaitClose {
      accountListener.remove()
      previewsListener?.remove()
    }
  }
}
