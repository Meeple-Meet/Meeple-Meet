package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.AccountNoUid
import com.github.meeplemeet.model.structures.fromNoUid
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

const val HANDLES_COLLECTION_PATH = "handles"

class FirestoreHandlesRepository(val db: FirebaseFirestore = FirebaseProvider.db) {
  private val handles = db.collection(HANDLES_COLLECTION_PATH)
  private val accounts = db.collection(ACCOUNT_COLLECTION_PATH)

  fun validHandle(handle: String): Boolean {
    return handle.length > 3 &&
        handle.length < 33 &&
        handle.all { it.isLetterOrDigit() || it == '_' }
  }

  suspend fun handleForAccountExists(accountId: String, handle: String): Boolean {
    if (handle.isBlank() || !validHandle(handle)) return false
    val snapshot = handles.document(handle).get().await()
    val data = snapshot.data ?: return false
    val id = data["accountId"] as? String
    return id == accountId
  }

  suspend fun checkHandleAvailable(handle: String): Boolean {
    if (handle.isBlank() || !validHandle(handle)) return false
    return handles.document(handle).get().await().exists()
  }

  suspend fun createAccountHandle(accountId: String, handle: String): Account {
    if (!validHandle(handle)) throw InvalidHandleFormatException()

    val accountRef = accounts.document(accountId)
    val handleRef = handles.document(handle)

    val result =
        db.runTransaction { tx ->
              val accountSnap = tx.get(accountRef)
              val accountNoUid =
                  accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
              val account = fromNoUid(accountId, accountNoUid.copy(handle = handle))

              val existing = tx.get(handleRef)
              if (existing.exists()) throw HandleAlreadyTakenException()

              tx.set(handleRef, mapOf("accountId" to accountId))
              tx.update(accountRef, Account::handle.name, handle)
              account
            }
            .await()

    return result
  }

  suspend fun setAccountHandle(accountId: String, oldHandle: String, newHandle: String): Account {
    if (!validHandle(newHandle)) throw InvalidHandleFormatException()

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
