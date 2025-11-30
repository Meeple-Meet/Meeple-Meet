package com.github.meeplemeet.model.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * @return A new Map containing the remaining entries after capping
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
