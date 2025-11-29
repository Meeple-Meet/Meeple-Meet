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

/**
 * Maximum number of accounts to cache in offline mode.
 *
 * When this limit is exceeded, the oldest accounts (in insertion order) are evicted from the cache
 * and their profile pictures are deleted from local storage to free up space.
 */
const val MAX_CACHED_ACCOUNTS = 100

/**
 * Caps the size of a LinkedHashMap by removing the oldest entries until the size is at or below the
 * maximum.
 *
 * This function enforces a maximum size constraint on a LinkedHashMap by removing entries in
 * insertion order (oldest first) until the map size is within the specified limit. LinkedHashMap
 * maintains insertion order, so the first entries are the oldest.
 *
 * ## Eviction Strategy
 * Uses LRU-like behavior: oldest inserted entries are evicted first. This is suitable for caching
 * scenarios where recently added items are more likely to be accessed.
 *
 * ## Thread Safety
 * This function modifies the input map in place. Callers must ensure thread-safe access.
 *
 * @param T The type of values stored in the map
 * @param map The LinkedHashMap to cap (modified in place)
 * @param max The maximum number of entries to retain
 * @return Pair of (the modified map, list of removed values for cleanup)
 */
private fun <T> cap(
    map: LinkedHashMap<String, T>,
    max: Int
): Pair<LinkedHashMap<String, T>, List<T>> {
  val removed = mutableListOf<T?>()
  while (map.size > max) {
    val oldestKey = map.entries.first().key
    removed.add(map.remove(oldestKey))
  }

  return Pair(map, removed.filterNotNull().toList())
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
   * This function implements a two-tier loading strategy:
   * 1. **Cache hit**: Returns immediately if account exists in offline cache
   * 2. **Cache miss**: Fetches from repository, caches the result, and downloads profile picture
   *
   * ## Caching Behavior
   * - When fetching from repository, the account is added to the offline cache
   * - Profile picture is downloaded asynchronously and cached locally
   * - If cache exceeds [MAX_CACHED_ACCOUNTS], oldest accounts are evicted
   * - Evicted accounts have their profile pictures deleted from local storage
   *
   * ## Thread Safety
   * This function is suspend and should be called from a coroutine. Updates to the offline mode
   * state flow are thread-safe.
   *
   * ## Error Handling
   * - If repository fetch fails, null is returned via callback
   * - Profile picture download errors are silently caught (non-critical)
   *
   * @param uid The unique identifier of the account to retrieve
   * @param context Android context for accessing local storage
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
      val (capped, removed) = cap(state, MAX_CACHED_ACCOUNTS)
      _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = capped)
      runCatching {
        RepositoryProvider.images.loadAccountProfilePicture(uid, context)
        removed.forEach { (account, _) ->
          RepositoryProvider.images.deleteLocalAccountProfilePicture(account.uid, context)
        }
      }
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
   * 5. Asynchronously downloading profile pictures for all accounts
   *
   * ## Performance Optimization
   * - Batch fetches missing accounts in a single repository call
   * - Profile pictures are downloaded in parallel in the background
   * - Only missing accounts are fetched from repository (cache-first strategy)
   *
   * ## Caching Behavior
   * - Successfully fetched accounts are added to the offline cache
   * - If cache exceeds [MAX_CACHED_ACCOUNTS], oldest accounts are evicted
   * - Evicted accounts have their profile pictures deleted asynchronously
   *
   * ## Order Preservation
   * The result list always has the same size and order as the input UIDs list. If any fetch fails,
   * the corresponding position will contain null.
   *
   * ## Thread Safety
   * - State updates are thread-safe via StateFlow
   * - Profile picture downloads run on Dispatchers.IO in the provided scope
   * - Main callback is invoked on the calling coroutine's context
   *
   * ## Error Handling
   * - Failed fetches result in null at that position
   * - Profile picture download errors are silently caught (non-critical)
   *
   * @param uids List of unique identifiers of the accounts to retrieve
   * @param context Android context for accessing local storage
   * @param scope CoroutineScope for launching background image operations
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

    // Step 1: Check cache and identify missing accounts
    val cached = uids.map { uid -> state[uid]?.first }
    val missingIndices = cached.withIndex().filter { it.value == null }.map { it.index }
    val missingUids = missingIndices.map { uids[it] }

    // Step 2: Batch fetch missing accounts from repository
    val fetched: List<Account?> =
        if (missingUids.isEmpty()) emptyList()
        else RepositoryProvider.accounts.getAccountsSafe(missingUids, false)

    // Step 3: Merge cached and fetched results in original order
    val merged = MutableList<Account?>(uids.size) { null }
    for ((i, account) in cached.withIndex()) merged[i] = account
    for ((offset, value) in missingIndices.withIndex()) {
      merged[value] = fetched.getOrNull(offset)
    }

    // Step 4: Update cache with fetched accounts and apply capping
    for ((i, uid) in uids.withIndex()) {
      val acc = merged[i]
      if (acc != null) state[uid] = acc to emptyMap()
    }
    val (capped, removed) = cap(state, MAX_CACHED_ACCOUNTS)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = capped)

    // Step 5: Asynchronously download profile pictures for all accounts
    scope.launch(Dispatchers.IO) {
      merged.forEach { account ->
        if (account != null)
            runCatching {
              RepositoryProvider.images.loadAccountProfilePicture(account.uid, context)
            }
      }

      // Clean up profile pictures for evicted accounts
      removed.forEach { (account, _) ->
        runCatching {
          RepositoryProvider.images.deleteLocalAccountProfilePicture(account.uid, context)
        }
      }
    }

    onResult(merged)
  }
}
