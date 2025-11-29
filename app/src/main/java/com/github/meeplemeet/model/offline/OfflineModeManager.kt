package com.github.meeplemeet.model.offline

import android.content.Context
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val MAX_CACHED_ACCOUNTS = 100

/**
 * Caps the size of a LinkedHashMap by removing the oldest entries until the size is at or below the
 * maximum.
 *
 * This function enforces a maximum size constraint on a LinkedHashMap by removing entries in
 * insertion order (oldest first) until the map size is within the specified limit. LinkedHashMap
 * maintains insertion order, so the first entries are the oldest.
 *
 * @param T The type of values stored in the map
 * @param map The LinkedHashMap to cap
 * @param max The maximum number of entries to retain
 * @return A new Map containing the remaining entries after capping
 */
private fun <T> cap(map: LinkedHashMap<String, T>, max: Int): Map<String, T> {
  while (map.size > max) {
    val oldestKey = map.entries.first().key
    map.remove(oldestKey)
  }

  return map.toMap()
}

/**
 * Singleton manager for offline mode state across the application.
 *
 * This object provides a centralized, reactive way to manage offline data including accounts,
 * discussions, posts, shops, space renters, and map pins. Uses Kotlin StateFlow to enable reactive
 * updates across all observers.
 *
 * Example usage in Composable:
 * ```
 * @Composable
 * fun MyScreen() {
 *     val offlineMode by OfflineModeManager.offlineModeFlow.collectAsState()
 *     Text("Accounts: ${offlineMode.accounts.size}")
 * }
 * ```
 *
 * Example usage in ViewModels:
 * ```
 * viewModelScope.launch {
 *     OfflineModeManager.offlineModeFlow.collect { offlineMode ->
 *         // React to changes
 *     }
 * }
 * ```
 */
object OfflineModeManager {
  private val _offlineModeFlow = MutableStateFlow(OfflineMode())

  /**
   * StateFlow of the current offline mode data. Observers will be notified of any changes.
   *
   * Use [androidx.compose.runtime.collectAsState] in Composable or
   * [kotlinx.coroutines.flow.collect] in coroutines to observe changes.
   */
  val offlineModeFlow: StateFlow<OfflineMode> = _offlineModeFlow.asStateFlow()

  /**
   * Clears all offline mode data by resetting to empty maps.
   *
   * This is useful when exiting offline mode or when the user logs out.
   */
  fun clearOfflineMode() {
    _offlineModeFlow.value = OfflineMode()
  }

  /**
   * Retrieves an account by UID, first checking the offline cache, then fetching from repository if
   * needed.
   *
   * This function checks the cached offline data first. If the account is found in cache, it
   * returns immediately. Otherwise, it attempts to fetch from the repository. If the fetch fails,
   * null is returned.
   *
   * @param uid The unique identifier of the account to retrieve
   * @param onResult Callback invoked with the Account if found, or null if not found or an error
   *   occurs
   */
  suspend fun loadAccount(uid: String, context: Context, onResult: (Account?) -> Unit) {
    val state = _offlineModeFlow.value.accounts
    val cached = state[uid]?.first

    if (cached != null) {
      onResult(cached)
      return
    }

    val fetched = RepositoryProvider.accounts.getAccountSafe(uid, false)
    if (fetched != null) {
      state[uid] = fetched to emptyMap()
      _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = state)
      runCatching { RepositoryProvider.images.loadAccountProfilePicture(uid, context) }
    }

    onResult(fetched)
  }

  /**
   * Retrieves multiple accounts by their UIDs, optimizing by fetching only missing accounts from
   * the repository.
   *
   * This function efficiently retrieves multiple accounts by:
   * 1. Checking the offline cache for each UID
   * 2. Identifying which accounts are missing from cache
   * 3. Fetching only the missing accounts from the repository in a batch
   * 4. Merging cached and fetched results in the original order
   *
   * If any fetch fails, the corresponding position will contain null in the result list. The result
   * list will always have the same size and order as the input UIDs list.
   *
   * @param uids List of unique identifiers of the accounts to retrieve
   * @param onResult Callback invoked with a list of Accounts (or null for missing/failed accounts)
   *   in the same order as the input UIDs
   */
  suspend fun loadAccounts(
      uids: List<String>,
      context: Context,
      scope: CoroutineScope,
      onResult: (List<Account?>) -> Unit
  ) {
    val state = _offlineModeFlow.value.accounts

    val cached = uids.map { uid -> state[uid]?.first }
    val missingIndices = cached.withIndex().filter { it.value == null }.map { it.index }
    val missingUids = missingIndices.map { uids[it] }

    val fetched: List<Account?> =
        if (missingUids.isEmpty()) emptyList()
        else RepositoryProvider.accounts.getAccountsSafe(missingUids, false)

    val merged = MutableList<Account?>(uids.size) { null }
    for ((i, account) in cached.withIndex()) merged[i] = account
    for ((offset, value) in missingIndices.withIndex()) {
      merged[value] = fetched.getOrNull(offset)
    }

    for ((i, uid) in uids.withIndex()) {
      val acc = merged[i]
      if (acc != null) state[uid] = acc to emptyMap()
    }
    _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = state)

    scope.launch(Dispatchers.IO) {
      merged.forEach { account ->
        if (account != null)
            runCatching {
              RepositoryProvider.images.loadAccountProfilePicture(account.uid, context)
            }
      }
    }

    onResult(merged)
  }
}
