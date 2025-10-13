package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.AccountNoUid
import com.github.meeplemeet.model.structures.fromNoUid
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val HANDLES_COLLECTION_PATH = "handles"

class FirestoreHandlesRepository(val db: FirebaseFirestore = FirebaseProvider.db) {
  private val handles = db.collection(HANDLES_COLLECTION_PATH)
  private val accounts = db.collection(ACCOUNT_COLLECTION_PATH)

  suspend fun handleForAccountExists(accountId: String, handle: String): Boolean {
    if (handle.isBlank()) return false
    val snapshot = handles.document(handle).get().await()
    val data = snapshot.data ?: return false
    val id = data["accountId"] as? String
    return id == accountId
  }

  suspend fun checkHandleAvailable(userHandle: String): Boolean {
    if (userHandle.isBlank()) return false
    return handles.document(userHandle).get().await().exists()
  }

  suspend fun createAccountHandle(accountId: String, userHandle: String): Account {
    val accountRef = accounts.document(accountId)
    val handleRef = handles.document(userHandle)

    val result =
        db.runTransaction { tx ->
              val accountSnap = tx.get(accountRef)
              val accountNoUid =
                  accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
              val account = fromNoUid(accountId, accountNoUid.copy(handle = userHandle))

              val existing = tx.get(handleRef)
              if (existing.exists()) throw HandleAlreadyTakenException()

              tx.set(handleRef, mapOf("accountId" to accountId))
              tx.update(accountRef, Account::handle.name, userHandle)
              account
            }
            .await()

    return result
  }

  suspend fun setAccountHandle(accountId: String, oldHandle: String, newHandle: String): Account {
    val accountRef = accounts.document(accountId)
    val oldHandleRef = handles.document(oldHandle)
    val newHandleRef = handles.document(newHandle)

    val result =
        db.runTransaction { tx ->
              val existing = tx.get(newHandleRef)
              if (existing.exists()) throw HandleAlreadyTakenException()

              val accountSnap = tx.get(accountRef)
              val accountNoUid =
                  accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
              val account = fromNoUid(accountId, accountNoUid.copy(handle = newHandle))

              tx.delete(oldHandleRef)
              tx.set(newHandleRef, mapOf("accountId" to accountId))
              tx.update(accountRef, Account::handle.name, newHandle)

              account
            }
            .await()

    return result
  }

  suspend fun deleteAccountHandle(userHandle: String) {
    handles.document(userHandle).delete().await()
  }
}
