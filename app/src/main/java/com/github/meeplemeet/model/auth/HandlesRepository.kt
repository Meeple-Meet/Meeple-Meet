package com.github.meeplemeet.model.auth

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.github.meeplemeet.model.discussions.ACCOUNT_COLLECTION_PATH
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

const val HANDLES_COLLECTION_PATH = "handles"

class HandlesRepository(val db: FirebaseFirestore = FirebaseProvider.db) {
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

  fun searchByHandle(handle: String): Flow<List<Account>> = callbackFlow {
    if (!validSearchHandle(handle)) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }

    val reg =
        handles
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), handle)
            .whereLessThanOrEqualTo(FieldPath.documentId(), nextString(handle))
            .addSnapshotListener { qs, e ->
              if (e != null) {
                close(e)
                return@addSnapshotListener
              }
              if (qs != null) {
                // Extract account IDs from handle docs
                val accountIds = qs.documents.mapNotNull { doc -> doc.getString("accountId") }

                if (accountIds.isEmpty()) {
                  trySend(emptyList())
                  return@addSnapshotListener
                }

                FirebaseProvider.db
                    .collection(ACCOUNT_COLLECTION_PATH)
                    .whereIn(
                        FieldPath.documentId(), accountIds.take(10)) // Firestore limit workaround
                    .get()
                    .addOnSuccessListener { snap ->
                      val accounts =
                          snap.documents.mapNotNull { d ->
                            d.toObject(AccountNoUid::class.java)?.let {
                              fromNoUid(d.id, it, emptyMap())
                            }
                          }
                      trySend(accounts)
                    }
                    .addOnFailureListener { close(it) }
              }
            }

    awaitClose { reg.remove() }
  }

  private fun nextString(s: String): String {
    if (s.isEmpty()) return s
    val lastChar = s.last()
    val nextChar = lastChar + 1
    return s.dropLast(1) + nextChar
  }

  private fun validSearchHandle(handle: String): Boolean {
    return handle.all { it.isLetterOrDigit() || it == '_' }
  }
}
