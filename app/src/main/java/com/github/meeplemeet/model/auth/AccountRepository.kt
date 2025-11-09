package com.github.meeplemeet.model.auth

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

class AccountRepository : FirestoreRepository("accounts") {
  /** Create a new account document. */
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

  /** Retrieve an account and its discussion previews. */
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

  /** Retrieve an account and its discussion previews. */
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

  /** Update account display name. */
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

  /** Delete an account document. */
  suspend fun deleteAccount(id: String) {
    collection.document(id).delete().await()
  }

  /**
   * Listen for changes to all discussion previews for a given account.
   *
   * Emits a map keyed by discussion ID whenever any preview changes.
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
