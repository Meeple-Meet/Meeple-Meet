package com.github.meeplemeet.model.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableMap

/**
 * Caps the size of a LinkedHashMap by removing the least recently used entries until the size is at
 * or below the maximum.
 *
 * This function enforces a maximum size constraint on a LinkedHashMap by removing entries in access
 * order (least recently used first) until the map size is within the specified limit. LinkedHashMap
 * with accessOrder=true maintains LRU ordering, so the first entries are the least recently
 * accessed.
 *
 * ## Eviction Strategy
 * Uses true LRU (Least Recently Used) behavior: entries that haven't been accessed recently are
 * evicted first. This is optimal for caching scenarios where recently accessed items are more
 * likely to be accessed again.
 *
 * ## Thread Safety
 * This function modifies the input map in place. Callers must ensure thread-safe access.
 *
 * @param T The type of values stored in the map
 * @param map The LinkedHashMap to cap (modified in place, must have accessOrder=true)
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
   * Internal mutable state flow tracking the device's online connectivity status. Defaults to false
   * (offline) until network connectivity is verified.
   */
  private val _hasInternetConnection = MutableStateFlow(false)

  /** StateFlow indicating whether the device currently has a valid internet connection. */
  val hasInternetConnection: StateFlow<Boolean> = _hasInternetConnection

  var dispatcher = Dispatchers.IO

  /**
   * Starts monitoring network connectivity changes.
   *
   * This function registers a network callback that monitors the device's internet connectivity
   * status and updates the [hasInternetConnection] StateFlow accordingly. The callback:
   * - Checks for internet capability when a network becomes available
   * - Verifies that the network connection is validated (has actual internet access)
   * - Updates the online state to false when the network is lost
   *
   * This should typically be called once during application initialization (e.g., in MainActivity
   * onCreate or Application.onCreate).
   *
   * @param context Android context used to access the ConnectivityManager system service
   */
  fun start(context: Context) {
    val cm = context.getSystemService(ConnectivityManager::class.java)

    val request =
        NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()

    cm.registerNetworkCallback(
        request,
        object : ConnectivityManager.NetworkCallback() {

          override fun onAvailable(network: Network) {
            val caps = cm.getNetworkCapabilities(network)
            val valid = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            _hasInternetConnection.value = valid
          }

          override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val valid = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _hasInternetConnection.value = valid
          }

          override fun onLost(network: Network) {
            _hasInternetConnection.value = false
          }
        })
  }

  /**
   * Clears all offline mode data by resetting to empty maps.
   *
   * This is useful when exiting offline mode or when the user logs out.
   */
  fun clearOfflineMode() {
    _offlineModeFlow.value = OfflineMode()
  }

  // ---------------------- Accounts Methods ---------------------- //

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
      // Create new map to avoid mutating StateFlow's internal state
      val newState = LinkedHashMap(state).apply { this[uid] = fetched to emptyMap() }
      val (capped, removed) = cap(newState, MAX_CACHED_ACCOUNTS)
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

    // Step 4: Update cache with ONLY newly fetched accounts and apply capping
    // Create new map to avoid mutating StateFlow's internal state
    val newState = LinkedHashMap(state)
    for ((offset, idx) in missingIndices.withIndex()) {
      val acc = fetched.getOrNull(offset)
      if (acc != null) newState[uids[idx]] = acc to emptyMap()
    }
    val (capped, removed) = cap(newState, MAX_CACHED_ACCOUNTS)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = capped)

    // Step 5: Asynchronously download profile pictures for all accounts
    scope.launch(dispatcher) {
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

  /**
   * Records a change to an account property in the offline cache.
   *
   * This function tracks pending changes to account properties that will be synchronized later. If
   * the account is not already in the cache, it is added. Changes are accumulated in a map keyed by
   * property name.
   *
   * ## Use Cases
   * - Recording user profile edits while offline
   * - Tracking pending updates before synchronization
   * - Maintaining change history for conflict resolution
   *
   * ## Thread Safety
   * Updates to the offline mode state flow are thread-safe.
   *
   * @param account The account being modified
   * @param property The name of the property being changed (e.g., "username", "bio")
   * @param newValue The new value for the property
   */
  fun setAccountChange(account: Account, property: String, newValue: Any) {
    val state = _offlineModeFlow.value.accounts
    val (existingAccount, existingChanges) = state[account.uid] ?: return
    val updatedChanges =
        existingChanges.toMutableMap().apply { put(property, newValue) }.toImmutableMap()
    // Create new map to avoid mutating StateFlow's internal state
    val newState = LinkedHashMap(state)
    newState[account.uid] = existingAccount to updatedChanges
    _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = newState)
  }

  // ---------------------- Discussions Methods ---------------------- //

  /**
   * Updates the discussion cache with new data and manages cache size limits.
   *
   * This internal function updates the cached discussion data and enforces the maximum cache size
   * by removing the oldest discussions when the limit is exceeded. It also handles cleanup of
   * associated profile pictures for removed discussions.
   *
   * @param state The current cache state (LinkedHashMap maintaining access order)
   * @param discussion The discussion to cache
   * @param messages The list of messages for this discussion
   * @param pendingMessages The list of pending (unsent) messages for this discussion
   */
  private fun updateDiscussionCache(
      state: LinkedHashMap<String, Triple<Discussion, List<Message>, List<Message>>>,
      discussion: Discussion,
      messages: List<Message>,
      pendingMessages: List<Message>
  ) {
    // Create new map to avoid mutating StateFlow's internal state
    val newState =
        LinkedHashMap(state).apply {
          this[discussion.uid] = Triple(discussion, messages, pendingMessages)
        }
    _offlineModeFlow.value = _offlineModeFlow.value.copy(discussions = newState)
  }

  /**
   * Loads a discussion from cache or fetches it from the repository if not cached.
   *
   * This function first checks if the discussion is already in the offline cache. If found, it
   * immediately returns the cached version via the callback. Otherwise, it fetches the discussion
   * from the repository, adds it to the cache, and returns the fetched result.
   *
   * @param uid The unique identifier of the discussion to load
   * @param context Android context for image operations during caching
   * @param onResult Callback function invoked with the loaded Discussion (or null if not found)
   */
  suspend fun loadDiscussion(uid: String, context: Context, onResult: (Discussion?) -> Unit) {
    if (uid.isBlank()) return

    val state = _offlineModeFlow.value.discussions
    val cached = state[uid]?.first

    if (cached != null) {
      onResult(cached)
      return
    }

    val fetched = RepositoryProvider.discussions.getDiscussionSafe(uid)
    addDiscussionToCache(fetched, context)

    onResult(fetched)
  }

  /**
   * Adds a discussion to the offline cache if not already present.
   *
   * This function checks if the discussion is already cached and, if not, adds it to the cache with
   * empty message lists. If the discussion is null or already cached, the function returns without
   * making changes.
   *
   * @param discussion The discussion to add to the cache (null values are ignored)
   * @param context Android context for image operations during caching
   */
  suspend fun addDiscussionToCache(discussion: Discussion?, context: Context) {
    if (discussion == null) return

    val state = _offlineModeFlow.value.discussions
    val cached = state[discussion.uid]?.first

    if (cached != null) return

    // Create new map to avoid mutating StateFlow's internal state
    val newState =
        LinkedHashMap(state).apply {
          this[discussion.uid] = Triple(discussion, emptyList(), emptyList())
        }
    val (capped, removed) = cap(newState, MAX_CACHED_DISCUSSIONS)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(discussions = capped)
    runCatching {
      RepositoryProvider.images.loadAccountProfilePicture(discussion.uid, context)
      removed.forEach { (disc, _) ->
        RepositoryProvider.images.deleteLocalDiscussionProfilePicture(context, disc.uid)
      }
    }
  }

  /**
   * Updates the cached messages for a discussion.
   *
   * This function updates the message list for an already cached discussion. If the discussion is
   * not in the cache, this function returns without making changes. Pending messages are preserved
   * during the update.
   *
   * @param uid The unique identifier of the discussion
   * @param messages The updated list of messages to cache for this discussion
   */
  fun cacheDiscussionMessages(uid: String, messages: List<Message>) {
    val state = _offlineModeFlow.value.discussions
    val cached = state[uid]

    if (cached == null) return

    updateDiscussionCache(state, cached.first, messages, cached.third)
  }

  /**
   * Adds a pending message to the cache for later synchronization.
   *
   * This function adds a message to the pending messages list for a cached discussion. Pending
   * messages represent messages that have been created locally but not yet sent to the server. If
   * the discussion is not in the cache, this function returns without making changes.
   *
   * @param uid The unique identifier of the discussion
   * @param message The message to add to the pending messages list
   */
  fun sendPendingMessage(uid: String, message: Message) {
    val state = _offlineModeFlow.value.discussions
    val cached = state[uid]

    if (cached == null) return

    updateDiscussionCache(state, cached.first, cached.second, cached.third + message)
  }
}
