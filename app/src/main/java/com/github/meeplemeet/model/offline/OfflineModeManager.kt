package com.github.meeplemeet.model.offline
// AI was used on this fille

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.offline.OfflineModeManager.hasInternetConnection
import com.github.meeplemeet.model.posts.Comment
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.posts.PostRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableMap

const val PENDING_STRING = "_pending_create"
const val UNCHECKED_CAST = "UNCHECKED_CAST"

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

  /** Tracks whether network monitoring has been started at least once. */
  private val _networkMonitorStarted = MutableStateFlow(false)

  /** StateFlow exposing whether network monitoring has been initialized. */
  val networkMonitorStarted: StateFlow<Boolean> = _networkMonitorStarted.asStateFlow()

  var dispatcher = Dispatchers.IO

  /**
   * Sets the internet connection state. For testing purposes only. In production, connection state
   * is managed by the network callback in start().
   *
   * @param connected true if connected, false if offline
   */
  fun setInternetConnection(connected: Boolean) {
    _hasInternetConnection.value = connected
  }

  /**
   * Starts monitoring network connectivity changes.
   *
   * This function registers a network callback that monitors the device's internet connectivity
   * status and updates the [hasInternetConnection] StateFlow accordingly. The callback:
   * - Checks for internet capability when a network becomes available
   * - Verifies that the network connection is validated (has actual internet access)
   * - Updates the online state to false when the network is lost
   * - Triggers auto-sync of offline posts when connectivity is restored
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

            val wasOffline = !_hasInternetConnection.value
            _hasInternetConnection.value = valid

            // Auto-sync offline posts when coming back online
            if (valid && wasOffline) {
              CoroutineScope(dispatcher).launch {
                syncQueuedPosts()
                syncOfflineComments()
              }
            }
          }

          override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val valid = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            val wasOffline = !_hasInternetConnection.value
            _hasInternetConnection.value = valid

            // Auto-sync when capabilities change to validated
            if (valid && wasOffline) {
              CoroutineScope(dispatcher).launch {
                syncQueuedPosts()
                syncOfflineComments()
              }
            }
          }

          override fun onLost(network: Network) {
            _hasInternetConnection.value = false
          }
        })
    _networkMonitorStarted.value = true
  }

  /**
   * Forces the network connection status for testing purposes.
   *
   * @param isConnected The desired connection status.
   */
  fun setNetworkStatusForTesting(isConnected: Boolean) {
    _hasInternetConnection.value = isConnected
  }

  /**
   * Clears all offline mode data by resetting to empty maps.
   *
   * This is useful when exiting offline mode or when the user logs out.
   */
  fun clearOfflineMode() {
    _offlineModeFlow.value = OfflineMode()
  }

  fun forceInternet() {
    _hasInternetConnection.value = true
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
  suspend fun loadAccount(
      uid: String,
      context: Context,
      loadAllData: Boolean = false,
      onResult: (Account?) -> Unit
  ) {
    val state = _offlineModeFlow.value.accounts
    val cached = state[uid]?.first

    if (cached != null) {
      onResult(cached)
      return
    }

    val fetched = RepositoryProvider.accounts.getAccountSafe(uid, loadAllData)
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
      runCatching {
        removed.forEach { (account, _) ->
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

  // ==================== Space Renter Functions ====================

  /**
   * Retrieves a space renter by ID, first checking the offline cache, then fetching from repository
   * if needed.
   *
   * Images are NOT cached or loaded - image handling should be disabled in UI when offline.
   *
   * @param renterId The unique identifier of the space renter to retrieve
   * @param onResult Callback invoked with the SpaceRenter if found, or null otherwise
   */
  suspend fun loadSpaceRenter(renterId: String, onResult: (SpaceRenter?) -> Unit) {
    val state = _offlineModeFlow.value.spaceRenters
    val cached = state[renterId]?.first

    if (cached != null) {
      onResult(cached)
      return
    }

    val fetched = RepositoryProvider.spaceRenters.getSpaceRenterSafe(renterId)
    if (fetched != null) {
      val newState = LinkedHashMap(state).apply { this[renterId] = fetched to emptyMap() }
      val (capped, _) = cap(newState, MAX_CACHED_SPACE_RENTERS)
      _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRenters = capped)
    }

    onResult(fetched)
  }

  // ==================== SPACE RENTER OFFLINE METHODS ====================

  /** Helper to safely filter a list by type, returning the filtered list or the fallback. */
  private inline fun <reified T> safeFilterList(value: Any, fallback: List<T>): List<T> {
    val list = value as? List<*>
    return if (list != null && list.all { it is T }) {
      list.filterIsInstance<T>()
    } else fallback
  }

  /**
   * Helper to apply standard changes (name, phone, email, website, address, openingHours, photos)
   * to any object.
   */
  /**
   * Strategy pattern object to hold copier functions for standard changes. Reduces parameter count
   * for applyStandardChanges.
   */
  private class StandardUpdateStrategy<T>(
      val copyName: (T, String) -> T,
      val copyPhone: (T, String) -> T,
      val copyEmail: (T, String) -> T,
      val copyWebsite: (T, String) -> T,
      val copyAddress: (T, Location) -> T,
      val copyOpeningHours: (T, List<OpeningHours>) -> T,
      val copyPhotoCollection: (T, List<String>) -> T,
      val getOpeningHours: (T) -> List<OpeningHours>,
      val getPhotoCollection: (T) -> List<String>,
      val onOther: (T, String, Any) -> T
  )

  /**
   * Helper to apply standard changes (name, phone, email, website, address, openingHours, photos)
   * to any object using a strategy.
   */
  private fun <T> applyStandardChanges(
      target: T,
      changes: Map<String, Any>,
      strategy: StandardUpdateStrategy<T>
  ): T {
    var updated = target
    changes.forEach { (key, value) ->
      updated =
          when (key) {
            "name" -> strategy.copyName(updated, value as? String ?: return@forEach)
            "phone" -> strategy.copyPhone(updated, value as? String ?: return@forEach)
            "email" -> strategy.copyEmail(updated, value as? String ?: return@forEach)
            "website" -> strategy.copyWebsite(updated, value as? String ?: return@forEach)
            "address" ->
                strategy.copyAddress(
                    updated,
                    value as? com.github.meeplemeet.model.shared.location.Location
                        ?: return@forEach)
            "openingHours" ->
                strategy.copyOpeningHours(
                    updated, safeFilterList(value, strategy.getOpeningHours(updated)))
            "photoCollectionUrl" ->
                strategy.copyPhotoCollection(
                    updated, safeFilterList(value, strategy.getPhotoCollection(updated)))
            else -> strategy.onOther(updated, key, value)
          }
    }
    return updated
  }

  /**
   * Applies pending changes to a SpaceRenter object.
   *
   * @param renter The base SpaceRenter object.
   * @param changes The map of pending changes.
   * @return The SpaceRenter with changes applied.
   */
  private fun applySpaceRenterChanges(renter: SpaceRenter, changes: Map<String, Any>): SpaceRenter {
    if (changes.isEmpty()) return renter

    val strategy =
        StandardUpdateStrategy<SpaceRenter>(
            copyName = { t, v -> t.copy(name = v) },
            copyPhone = { t, v -> t.copy(phone = v) },
            copyEmail = { t, v -> t.copy(email = v) },
            copyWebsite = { t, v -> t.copy(website = v) },
            copyAddress = { t, v -> t.copy(address = v) },
            copyOpeningHours = { t, v -> t.copy(openingHours = v) },
            copyPhotoCollection = { t, v -> t.copy(photoCollectionUrl = v) },
            getOpeningHours = { it.openingHours },
            getPhotoCollection = { it.photoCollectionUrl },
            onOther = { t, k, v ->
              if (k == "spaces") t.copy(spaces = safeFilterList(v, t.spaces)) else t
            })

    return applyStandardChanges(renter, changes, strategy)
  }

  /**
   * Records a change to a space renter property in the offline cache for later synchronization.
   *
   * This tracks edits made while offline. Changes will be synced when connection is restored.
   *
   * @param renter The space renter being modified
   * @param property The name of the property being changed
   * @param newValue The new value for the property
   */
  fun setSpaceRenterChange(renter: SpaceRenter, property: String, newValue: Any) {
    val state = _offlineModeFlow.value.spaceRenters
    val (existingRenter, existingChanges) = state[renter.id] ?: (renter to emptyMap())
    val updatedChanges =
        existingChanges.toMutableMap().apply { put(property, newValue) }.toImmutableMap()

    // Update the base object with the new change
    val updatedRenter = applySpaceRenterChanges(existingRenter, mapOf(property to newValue))

    val newState = LinkedHashMap(state)
    newState[renter.id] = updatedRenter to updatedChanges
    _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRenters = newState)
  }

  /**
   * Adds a new space renter to the offline cache for later synchronization. Used when creating a
   * space renter while offline.
   *
   * Note: Space renter creation should ideally be blocked in UI when offline since images cannot be
   * uploaded. This is provided for edge cases where text-only data needs to be queued.
   *
   * @param renter The space renter to add
   */
  fun addPendingSpaceRenter(renter: SpaceRenter) {
    val state = _offlineModeFlow.value.spaceRenters
    val newState = LinkedHashMap(state)
    newState[renter.id] = renter to mapOf(PENDING_STRING to true)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRenters = newState)
  }

  suspend fun updateSpaceRenterCache(renter: SpaceRenter) {
    val state = _offlineModeFlow.value.spaceRenters
    val newState = LinkedHashMap(state)
    newState[renter.id] = renter to state[renter.id]?.second.orEmpty()
    _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRenters = newState)
    // Wait until the StateFlow has actually updated with this exact object
    _offlineModeFlow.first { offlineMode -> offlineMode.spaceRenters[renter.id]?.first == renter }
  }

  /**
   * Removes a space renter from the offline cache. Used when deleting a space renter or after
   * successful synchronization.
   *
   * @param renterId The ID of the space renter to remove
   */
  fun removeSpaceRenter(renterId: String) {
    val state = _offlineModeFlow.value.spaceRenters
    val newState = LinkedHashMap(state)
    newState.remove(renterId)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRenters = newState)
  }

  // ==================== SHOP OFFLINE METHODS ====================

  /**
   * Loads a shop from the offline cache or repository.
   *
   * @param shopId The ID of the shop to load
   * @param onLoaded Callback invoked with the loaded shop (or null if not found)
   */
  fun loadShop(shopId: String, onLoaded: (Shop?) -> Unit) {
    val cached = _offlineModeFlow.value.shops[shopId]?.first
    if (cached != null) {
      onLoaded(cached)
    } else {
      // Load from repository if not in cache
      CoroutineScope(dispatcher).launch {
        val shop = RepositoryProvider.shops.getShopSafe(shopId)
        onLoaded(shop)
      }
    }
  }

  /**
   * Adds a new shop to the offline cache for later synchronization. Used when creating a shop while
   * offline.
   *
   * @param shop The shop to add
   */
  fun addPendingShop(shop: Shop) {
    val state = _offlineModeFlow.value.shops
    val newState = LinkedHashMap(state)
    newState[shop.id] = shop to mapOf(PENDING_STRING to true)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(shops = newState)
  }

  /**
   * Records a change to a shop property in the offline cache for later synchronization.
   *
   * @param shop The shop being modified
   * @param property The name of the property changing
   * @param newValue The new value for the property
   */
  fun setShopChange(shop: Shop, property: String, newValue: Any) {
    val state = _offlineModeFlow.value.shops
    val existingEntry = state[shop.id]

    // If not in cache, add it first
    val (existingShop, existingChanges) =
        if (existingEntry == null) {
          addPendingShop(shop)
          shop to emptyMap()
        } else {
          existingEntry
        }

    val updatedChanges = existingChanges.toMutableMap()
    updatedChanges[property] = newValue

    // Update the base object with the new change
    val updatedShop = applyShopChanges(existingShop, mapOf(property to newValue))

    val newState = LinkedHashMap(state)
    newState[shop.id] = updatedShop to updatedChanges
    _offlineModeFlow.value = _offlineModeFlow.value.copy(shops = newState)
  }

  private fun applyShopChanges(shop: Shop, changes: Map<String, Any>): Shop {
    if (changes.isEmpty()) return shop

    val strategy =
        StandardUpdateStrategy<Shop>(
            copyName = { t, v -> t.copy(name = v) },
            copyPhone = { t, v -> t.copy(phone = v) },
            copyEmail = { t, v -> t.copy(email = v) },
            copyWebsite = { t, v -> t.copy(website = v) },
            copyAddress = { t, v -> t.copy(address = v) },
            copyOpeningHours = { t, v -> t.copy(openingHours = v) },
            copyPhotoCollection = { t, v -> t.copy(photoCollectionUrl = v) },
            getOpeningHours = { it.openingHours },
            getPhotoCollection = { it.photoCollectionUrl },
            onOther = { t, k, v ->
              if (k == "gameCollection") {
                val list = v as? List<*>
                val castValue =
                    if (list != null) {
                      list.mapNotNull { item ->
                        if (item is Pair<*, *> &&
                            item.first is com.github.meeplemeet.model.shared.game.Game &&
                            item.second is Int) {
                          @Suppress("UNCHECKED_CAST")
                          (item.first as com.github.meeplemeet.model.shared.game.Game) to
                              (item.second as Int)
                        } else {
                          null
                        }
                      }
                    } else t.gameCollection
                // Only update if we successfully mapped something or if the list was empty but not
                // null
                if (list != null && castValue.size == list.size) {
                  t.copy(gameCollection = castValue)
                } else {
                  t
                }
              } else t
            })

    return applyStandardChanges(shop, changes, strategy)
  }

  suspend fun updateShopCache(shop: Shop) {
    val state = _offlineModeFlow.value.shops
    val newState = LinkedHashMap(state)
    newState[shop.id] = shop to state[shop.id]?.second.orEmpty()
    _offlineModeFlow.value = _offlineModeFlow.value.copy(shops = newState)
    // Wait until the StateFlow has actually updated with this exact object
    _offlineModeFlow.first { offlineMode -> offlineMode.shops[shop.id]?.first == shop }
  }

  /**
   * Clears pending changes for a shop after successful synchronization.
   *
   * @param shopId The ID of the shop whose changes were synchronized
   */
  fun clearShopChanges(shopId: String) {
    val state = _offlineModeFlow.value.shops
    val shop = state[shopId]?.first ?: return
    val newState = LinkedHashMap(state)
    newState[shopId] = shop to emptyMap()
    _offlineModeFlow.value = _offlineModeFlow.value.copy(shops = newState)
  }

  /**
   * Removes a shop from the offline cache. Used when deleting a shop or after successful
   * synchronization.
   *
   * @param shopId The ID of the shop to remove
   */
  fun removeShop(shopId: String) {
    val state = _offlineModeFlow.value.shops
    val newState = LinkedHashMap(state)
    newState.remove(shopId)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(shops = newState)
  }

  // ==================== Synchronization Helpers ====================

  /**
   * Retrieves all pending changes for shops that need to be synchronized.
   *
   * @return List of pairs containing the shop and its pending changes map
   */
  fun getPendingShopChanges(): List<Pair<Shop, Map<String, Any>>> {
    return _offlineModeFlow.value.shops.values.filter { it.second.isNotEmpty() }.toList()
  }

  /**
   * Retrieves all pending changes for space renters that need to be synchronized.
   *
   * @return List of pairs containing the space renter and its pending changes map
   */
  fun getPendingSpaceRenterChanges(): List<Pair<SpaceRenter, Map<String, Any>>> {
    return _offlineModeFlow.value.spaceRenters.values.filter { it.second.isNotEmpty() }.toList()
  }

  /**
   * Clears pending changes for a space renter after successful synchronization.
   *
   * @param renterId The ID of the space renter whose changes were synchronized
   */
  fun clearSpaceRenterChanges(renterId: String) {
    val state = _offlineModeFlow.value.spaceRenters
    val renter = state[renterId]?.first ?: return
    val newState = LinkedHashMap(state)
    newState[renterId] = renter to emptyMap()
    _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRenters = newState)
  }

  // ==================== POST CACHING METHODS ====================
  /** Clears all cached posts from offline mode. */
  fun clearCachedPosts() {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(posts = LinkedHashMap())
  }

  /** Syncs offline-created posts to Firestore when connection is restored. */
  suspend fun syncQueuedPosts(onComplete: (() -> Unit)? = null) {
    if (!hasInternetConnection.value) return

    for (queuedPost in getQueuedPosts()) {
      try {
        RepositoryProvider.posts.createPost(
            title = queuedPost.title,
            content = queuedPost.body,
            authorId = queuedPost.authorId,
            tags = queuedPost.tags)
        removeQueuedPost(queuedPost.id)
      } catch (_: Exception) {
        // Leave in queue for next sync attempt
      }
    }

    onComplete?.invoke()
  }

  /** Syncs offline comments to Firestore and clears cache. Handles nested comment ID mapping. */
  suspend fun syncOfflineComments() {
    if (!hasInternetConnection.value) return

    // Extract all offline comments from cached posts
    val allOfflineComments = mutableListOf<Triple<String, Comment, String>>()
    for ((postId, post) in _offlineModeFlow.value.posts) {
      allOfflineComments.addAll(extractOfflineComments(post.comments, postId))
    }

    if (allOfflineComments.isEmpty()) return

    // Map temp comment IDs to real Firestore IDs
    val tempToRealIdMap = mutableMapOf<String, String>()
    val uploaded = mutableSetOf<String>()

    // Upload comments: iterate until all are uploaded (BFS approach)
    while (uploaded.size < allOfflineComments.size) {
      val beforeSize = uploaded.size

      for ((postId, comment, parentId) in allOfflineComments) {
        if (uploaded.contains(comment.id)) continue

        // Check if parent is either not a temp comment, or already uploaded
        val parentIsReady = !parentId.startsWith("temp_comment_") || uploaded.contains(parentId)

        if (parentIsReady) {
          runCatching {
            // Translate parent ID if it was a temp ID that's already been uploaded
            val realParentId = tempToRealIdMap[parentId] ?: parentId

            // Upload comment with correct parent ID
            val realCommentId =
                RepositoryProvider.posts.addComment(
                    postId, comment.text, comment.authorId, realParentId)

            // Map temp ID to real ID for future children
            tempToRealIdMap[comment.id] = realCommentId
            uploaded.add(comment.id)
          }
        }
      }

      // Safety check: if no progress made, break to avoid infinite loop
      if (uploaded.size == beforeSize) {
        break
      }
    }

    clearCachedPosts()
  }

  /** Extracts comments with temp IDs from comment tree. */
  private fun extractOfflineComments(
      comments: List<Comment>,
      postId: String,
      parentId: String = postId
  ): List<Triple<String, Comment, String>> {
    val result = mutableListOf<Triple<String, Comment, String>>()

    for (comment in comments) {
      if (comment.id.startsWith("temp_comment_")) {
        result.add(Triple(postId, comment, parentId))
      }
      // Recursively check children
      result.addAll(extractOfflineComments(comment.children, postId, comment.id))
    }

    return result
  }

  /** Recursively adds a reply to a comment in the comment tree. */
  private fun addReplyToComment(
      comments: List<Comment>,
      parentId: String,
      reply: Comment
  ): List<Comment> {
    return comments.map { comment ->
      when {
        comment.id == parentId ->
            comment.copy(
                children =
                    (comment.children + reply)
                        .sortedWith(
                            compareBy({ it.timestamp.seconds }, { it.timestamp.nanoseconds }))
                        .toMutableList())
        comment.children.isNotEmpty() ->
            comment.copy(
                children = addReplyToComment(comment.children, parentId, reply).toMutableList())
        else -> comment
      }
    }
  }

  /** Recursively sorts comments by timestamp (oldest first). */
  private fun sortCommentsByTime(comments: List<Comment>): List<Comment> {
    return comments
        .map { it.copy(children = sortCommentsByTime(it.children).toMutableList()) }
        .sortedWith(compareBy({ it.timestamp.seconds }, { it.timestamp.nanoseconds }))
  }

  /**
   * Adds a comment or reply to a post, handling offline queuing if needed.
   *
   * When online, the comment is added directly via the repository. When offline, a temporary
   * comment with a temp ID is created and added to the offline cache for immediate UI feedback.
   * Comments cannot be added to posts that are still queued for upload.
   *
   * @param postId The ID of the post to comment on.
   * @param text The text content of the comment.
   * @param authorId The UID of the user creating the comment.
   * @param parentId The ID of the parent (post ID for top-level comments, or comment ID for
   *   replies).
   * @throws IllegalStateException if trying to comment on a post that is queued for upload.
   */
  suspend fun addComment(postId: String, text: String, authorId: String, parentId: String) {
    if (hasInternetConnection.value && !postId.startsWith("temp_")) {
      RepositoryProvider.posts.addComment(postId, text, authorId, parentId)
    } else {
      val queuedPosts = _offlineModeFlow.value.postsToAdd
      if (queuedPosts.containsKey(postId)) {
        throw IllegalStateException("Cannot add comments to posts that are waiting to be uploaded.")
      }

      val tempId = "temp_comment_${System.currentTimeMillis()}_${(0..999).random()}"
      val comment =
          Comment(
              id = tempId,
              text = text,
              timestamp = Timestamp.now(),
              authorId = authorId,
              children = mutableListOf())

      val cachedPost = _offlineModeFlow.value.posts[postId]
      if (cachedPost != null) {
        val updatedPost =
            if (parentId == postId) {
              cachedPost.copy(
                  comments = sortCommentsByTime(cachedPost.comments + comment),
                  commentCount = cachedPost.commentCount + 1)
            } else {
              cachedPost.copy(
                  comments =
                      sortCommentsByTime(addReplyToComment(cachedPost.comments, parentId, comment)),
                  commentCount = cachedPost.commentCount + 1)
            }

        val newState = LinkedHashMap(_offlineModeFlow.value.posts)
        newState[postId] = updatedPost
        _offlineModeFlow.value = _offlineModeFlow.value.copy(posts = newState)
      }
    }
  }

  /** Removes a queued offline-created post by its temporary ID. (temp_...) */
  fun removeQueuedPost(postId: String) {
    val newState = LinkedHashMap(_offlineModeFlow.value.postsToAdd)
    newState.remove(postId)
    _offlineModeFlow.value = _offlineModeFlow.value.copy(postsToAdd = newState)
  }

  /** Deletes a post online or removes it from the offline queue. */
  suspend fun deletePost(post: Post) {
    if (post.id.startsWith("temp_")) {
      removeQueuedPost(post.id)
    } else {
      RepositoryProvider.posts.deletePost(post.id)
      // Remove from cache immediately so UI updates right away
      val newState = LinkedHashMap(_offlineModeFlow.value.posts)
      newState.remove(post.id)
      _offlineModeFlow.value = _offlineModeFlow.value.copy(posts = newState)
    }
  }

  /** Creates a StateFlow for observing a specific post with online/offline handling. */
  fun postFlow(
      postId: String,
      scope: CoroutineScope,
      repository: PostRepository
  ): StateFlow<Post?> {
    if (postId.isBlank()) return MutableStateFlow(null)

    val flow = MutableStateFlow<Post?>(null)

    if (postId.startsWith("temp_")) {
      scope.launch { offlineModeFlow.collect { flow.value = it.postsToAdd[postId] } }
    } else {
      scope.launch {
        offlineModeFlow.collect { offlineMode ->
          if (!hasInternetConnection.value) {
            flow.value = offlineMode.posts[postId]
          }
        }
      }

      scope.launch {
        repository.listenPost(postId).collect { updated ->
          if (hasInternetConnection.value && updated != null) {
            flow.value = updated
          }
        }
      }
    }

    return flow
  }

  /** Creates a post online or queues it offline with temp ID. */
  suspend fun createPost(
      title: String,
      body: String,
      authorId: String,
      tags: List<String> = emptyList()
  ) {
    if (hasInternetConnection.value) {
      RepositoryProvider.posts.createPost(title, body, authorId, tags)
    } else {
      val tempId = "temp_${System.currentTimeMillis()}_${(0..999).random()}"
      val offlinePost =
          Post(
              id = tempId,
              title = title,
              body = body,
              timestamp = Timestamp.now(),
              authorId = authorId,
              tags = tags,
              comments = emptyList(),
              commentCount = 0)

      val newState = LinkedHashMap(_offlineModeFlow.value.postsToAdd)

      if (newState.size >= MAX_OFFLINE_CREATED_POSTS) {
        newState.remove(newState.entries.first().key)
      }

      newState[offlinePost.id] = offlinePost
      _offlineModeFlow.value = _offlineModeFlow.value.copy(postsToAdd = newState)
    }
  }

  /**
   * Caches a list of posts in offline mode, fetching full posts with comments if possible.
   *
   * This function updates the offline mode cache with the provided list of posts. For each post, it
   * attempts to fetch the full post including comments from the repository. If fetching fails, it
   * falls back to caching the provided post as-is. The cache is capped at [MAX_CACHED_POSTS] using
   * an LRU eviction strategy.
   *
   * @param posts The list of posts to cache
   */
  fun cachePosts(posts: List<Post>) {
    CoroutineScope(dispatcher).launch {
      val newState = LinkedHashMap<String, Post>(MAX_CACHED_POSTS, LOAD_FACTOR, true)

      for (post in posts) {
        try {
          // Fetch full posts with comments
          val fullPost = RepositoryProvider.posts.getPost(post.id)
          newState[fullPost.id] = fullPost.copy(comments = fullPost.comments)
        } catch (_: Exception) {
          newState[post.id] = post.copy(comments = post.comments)
        }
      }

      _offlineModeFlow.value = _offlineModeFlow.value.copy(posts = newState)
    }
  }

  /** Returns all cached posts sorted by timestamp (newest first). */
  fun getCachedPosts(): List<Post> {
    return _offlineModeFlow.value.posts.values.sortedByDescending { it.timestamp }
  }

  /** Returns all queued offline-created posts. */
  fun getQueuedPosts(): List<Post> {
    return _offlineModeFlow.value.postsToAdd.values.toList()
  }

  /** Gets all posts for display (cached + queued), sorted by timestamp. */
  fun getAllPostsForDisplay(): List<Post> {
    return (getCachedPosts() + getQueuedPosts()).sortedByDescending { it.timestamp }
  }

  /**
   * Creates a pair of StateFlows for posts overview and error messages with online/offline
   * handling.
   *
   * This function sets up two StateFlows:
   * 1. A StateFlow emitting the list of posts for display, combining cached posts and queued posts.
   * 2. A StateFlow emitting error messages related to post fetching.
   *
   * The function listens to both the offline cache and the Firestore repository:
   * - From the offline cache, it provides immediate updates when cached posts change.
   * - From the Firestore repository, it listens for real-time updates to posts. When online, it
   *   updates the posts flow with the latest fetched data and caches it. When offline, it shows
   *   cached posts if available.
   *
   * @param scope CoroutineScope for launching background operations
   * @param repository The PostRepository for accessing post data
   * @return A Pair of StateFlows: (postsFlow, errorFlow)
   */
  fun createPostsOverviewFlow(
      scope: CoroutineScope,
      repository: PostRepository
  ): Pair<StateFlow<List<Post>>, StateFlow<String>> {
    val postsFlow = MutableStateFlow(getAllPostsForDisplay())
    val errorFlow = MutableStateFlow("")

    // Cache listener: Provides immediate UI updates when cache changes
    scope.launch {
      offlineModeFlow.collect { offlineMode ->
        val cachedPosts =
            (offlineMode.posts.values + offlineMode.postsToAdd.values).sortedByDescending {
              it.timestamp
            }
        if (!hasInternetConnection.value && cachedPosts.isNotEmpty()) {
          postsFlow.value = cachedPosts
        }
      }
    }

    // Firestore listener: Real-time database updates
    scope.launch {
      repository
          .listenPosts()
          .catch { e -> errorFlow.value = "Error: ${e.message}" }
          .collect { fetchedPosts ->
            if (hasInternetConnection.value) {
              // Online: cache latest posts and show ALL fetched data (overrides cache view)
              cachePosts(fetchedPosts.take(MAX_CACHED_POSTS))
              postsFlow.value = fetchedPosts
              errorFlow.value = ""
            } else {
              errorFlow.value = "Offline mode"
            }
          }
    }

    return postsFlow to errorFlow
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

  // Add to OfflineModeManager object, after the existing helper functions

  /**
   * Syncs all pending space renter changes with Firestore. Should be called when internet
   * connection is restored.
   */
  private suspend fun syncPendingSpaceRenters() {
    val pendingChanges = getPendingSpaceRenterChanges()

    if (pendingChanges.isEmpty()) return

    pendingChanges.forEach { (renter, changes) ->
      try {
        if (changes.containsKey(PENDING_STRING)) {
          RepositoryProvider.spaceRenters.createSpaceRenter(
              owner = renter.owner,
              name = renter.name,
              phone = renter.phone,
              email = renter.email,
              website = renter.website,
              address = renter.address,
              openingHours = renter.openingHours,
              spaces = renter.spaces,
              photoCollectionUrl = renter.photoCollectionUrl)

          removeSpaceRenter(renter.id)
        } else {
          // Update Firestore
          RepositoryProvider.spaceRenters.updateSpaceRenterOffline(renter.id, changes)

          // Fetch the updated data from Firestore
          val refreshed = RepositoryProvider.spaceRenters.getSpaceRenterSafe(renter.id)
          if (refreshed != null) {
            // Update the cache with fresh data
            updateSpaceRenterCache(refreshed)
          }

          clearSpaceRenterChanges(renter.id)
        }
      } catch (e: Exception) {
        // Log or handle error - silent failure for now
      }
    }
  }
  /**
   * Syncs all pending offline data (shops and space renters) with Firestore. Call this when
   * internet connection is restored.
   */
  suspend fun syncAllPendingData() {
    syncPendingSpaceRenters()
  }
}
