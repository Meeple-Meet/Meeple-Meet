package com.github.meeplemeet.model.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
  private val job = SupervisorJob()
  /** Coroutine scope to run async jobs */
  val scope = CoroutineScope(Dispatchers.Default + job)

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
}
