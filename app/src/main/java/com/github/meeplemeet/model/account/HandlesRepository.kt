package com.github.meeplemeet.model.account

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.InvalidHandleFormatException
import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val FIELD_ACCOUNT_ID = "accountId"

/**
 * Repository that manages user handles (unique @usernames) stored in the `handles` Firestore
 * collection.
 *
 * Each handle document maps a handle to an account ID. The repository provides validation,
 * availability checks, creation/update flows that keep the handle document and account document in
 * sync, and a search API for autocomplete.
 */
class HandlesRepository(accountRepository: AccountRepository = RepositoryProvider.accounts) :
    FirestoreRepository("handles") {
  private val accounts = accountRepository.collection

  /** Returns true if the handle matches the allowed pattern and length (4–32, letters/digits/_). */
  fun validHandle(handle: String): Boolean {
    return handle.length > 3 &&
        handle.length < 33 &&
        handle.all { it.isLetterOrDigit() || it == '_' }
  }

  /**
   * Checks whether a handle document already points to the provided account ID.
   *
   * Useful for idempotent updates where the current handle might already be set on the account.
   */
  suspend fun handleForAccountExists(accountId: String, handle: String): Boolean {
    if (handle.isBlank() || !validHandle(handle)) return false
    val snapshot = collection.document(handle).get().await()
    val data = snapshot.data ?: return false
    val id = data[FIELD_ACCOUNT_ID] as? String
    return id == accountId
  }

  /**
   * Returns true if the handle is free to claim.
   *
   * Non-valid handles are treated as unavailable to avoid leaking validation details.
   */
  suspend fun checkHandleAvailable(handle: String): Boolean {
    if (handle.isBlank() || !validHandle(handle)) return false
    return collection.document(handle).get().await().exists()
  }

  /**
   * Creates a handle for an account in a single transaction, ensuring uniqueness and updating the
   * account's `handle` field atomically.
   *
   * @throws InvalidHandleFormatException if the handle does not match the allowed pattern.
   * @throws HandleAlreadyTakenException if the handle document already exists.
   * @throws AccountNotFoundException if the target account document is missing.
   */
  suspend fun createAccountHandle(accountId: String, handle: String): Account {
    if (!validHandle(handle)) throw InvalidHandleFormatException()

    val accountRef = accounts.document(accountId)
    val handleRef = collection.document(handle)

    val result =
        db.runTransaction { tx ->
              val accountSnap = tx.get(accountRef)
              val accountNoUid =
                  accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
              val account = fromNoUid(accountId, accountNoUid.copy(handle = handle))

              val existing = tx.get(handleRef)
              if (existing.exists()) throw HandleAlreadyTakenException()

              tx.set(handleRef, mapOf<String, Any>(FIELD_ACCOUNT_ID to accountId))
              tx.update(accountRef, Account::handle.name, handle)
              account
            }
            .await()

    return result
  }

  /**
   * Updates an account's handle, replacing the old handle document with the new one in a
   * transaction.
   *
   * @throws InvalidHandleFormatException if the new handle does not match the allowed pattern.
   * @throws HandleAlreadyTakenException if the new handle is already claimed.
   * @throws AccountNotFoundException if the target account document is missing.
   */
  suspend fun setAccountHandle(accountId: String, oldHandle: String, newHandle: String): Account {
    if (!validHandle(newHandle)) throw InvalidHandleFormatException()

    val accountRef = accounts.document(accountId)
    val oldHandleRef = collection.document(oldHandle)
    val newHandleRef = collection.document(newHandle)

    val result =
        db.runTransaction { tx ->
              val existing = tx.get(newHandleRef)
              if (existing.exists()) throw HandleAlreadyTakenException()

              val accountSnap = tx.get(accountRef)
              val accountNoUid =
                  accountSnap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
              val account = fromNoUid(accountId, accountNoUid.copy(handle = newHandle))

              tx.delete(oldHandleRef)
              tx.set(newHandleRef, mapOf<String, Any>(FIELD_ACCOUNT_ID to accountId))
              tx.update(accountRef, Account::handle.name, newHandle)

              account
            }
            .await()

    return result
  }

  /** Deletes a handle document without touching the owning account. */
  suspend fun deleteAccountHandle(userHandle: String) {
    collection.document(userHandle).delete().await()
  }

  /**
   * Prefix-searches handles for autocomplete.
   *
   * Emits up to 10 matching accounts whose handles fall within the prefix range. Invalid search
   * strings return an empty flow immediately.
   */
  fun searchByHandle(handle: String): Flow<List<Account>> = callbackFlow {
    if (!validSearchHandle(handle)) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }

    val reg =
        collection
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), handle)
            .whereLessThanOrEqualTo(FieldPath.documentId(), nextString(handle))
            .addSnapshotListener { qs, e ->
              if (e != null) {
                close(e)
                return@addSnapshotListener
              }
              if (qs != null) {
                // Extract account IDs from handle docs
                val accountIds = qs.documents.mapNotNull { doc -> doc.getString(FIELD_ACCOUNT_ID) }

                if (accountIds.isEmpty()) {
                  trySend(emptyList())
                  return@addSnapshotListener
                }

                accounts
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
