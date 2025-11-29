package com.github.meeplemeet.model.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
private fun <T> cap(map: LinkedHashMap<String, T>, max: Int): LinkedHashMap<String, T> {
  while (map.size > max) {
    val oldestKey = map.entries.first().key
    map.remove(oldestKey)
  }

  return map
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
  fun getAccount(uid: String, onResult: (Account?) -> Unit) {
    val cached = _offlineModeFlow.value.accounts[uid]

    scope.launch(Dispatchers.Main) {
      if (cached != null) {
        onResult(cached.first)
        return@launch
      }

      onResult(RepositoryProvider.accounts.getAccountSafe(uid, false))
    }
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
  fun getAccounts(uids: List<String>, onResult: (List<Account?>) -> Unit) {
    scope.launch(Dispatchers.Main) {
      val state = _offlineModeFlow.value.accounts

      val cached = uids.map { uid -> state[uid]?.first }
      val missingIndices = cached.withIndex().filter { it.value == null }.map { it.index }
      val missingUids = missingIndices.map { uids[it] }

      val fetched: List<Account?> =
          if (missingUids.isEmpty()) emptyList()
          else RepositoryProvider.accounts.getAccountsSafe(missingUids, false)

      val merged = MutableList<Account?>(uids.size) { null }

      for ((i, account) in cached.withIndex()) merged[i] = account
      for ((offset, value) in missingIndices.withIndex()) merged[value] = fetched.getOrNull(offset)

      onResult(merged)
    }
  }
}
