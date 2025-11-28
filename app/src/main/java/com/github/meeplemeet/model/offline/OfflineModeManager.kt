package com.github.meeplemeet.model.offline

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.map.GeoPinWithLocation
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.space_renter.SpaceRenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
   * Replaces the entire offline mode state with a new instance.
   *
   * @param newOfflineMode The new offline mode data to set
   */
  fun updateOfflineMode(newOfflineMode: OfflineMode) {
    _offlineModeFlow.value = newOfflineMode
  }

  /**
   * Updates only the accounts map in the offline mode state.
   *
   * @param accounts The new accounts map to set
   */
  fun updateAccounts(accounts: Map<String, Pair<Account, Map<String, Any>>>) {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(accounts = accounts)
  }

  /**
   * Updates only the discussions map in the offline mode state.
   *
   * @param discussions The new discussions map to set
   */
  fun updateDiscussions(discussions: Map<String, Pair<Discussion, List<Message>>>) {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(discussions = discussions)
  }

  /**
   * Updates only the posts map in the offline mode state.
   *
   * @param posts The new posts map to set (Post paired with Boolean flag)
   */
  fun updatePosts(posts: Map<String, Pair<Post, Boolean>>) {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(posts = posts)
  }

  /**
   * Updates only the shops to add map in the offline mode state.
   *
   * @param shops The new shops map to set
   */
  fun updateShopsToAdd(shops: Map<String, Pair<Shop, Map<String, Any>>>) {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(shopsToAdd = shops)
  }

  /**
   * Updates only the space renters to add map in the offline mode state.
   *
   * @param spaceRenters The new space renters map to set
   */
  fun updateSpaceRentersToAdd(spaceRenters: Map<String, Pair<SpaceRenter, Map<String, Any>>>) {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(spaceRentersToAdd = spaceRenters)
  }

  /**
   * Updates only the loaded pins map in the offline mode state.
   *
   * @param pins The new pins map to set
   */
  fun updateLoadedPins(pins: Map<String, GeoPinWithLocation>) {
    _offlineModeFlow.value = _offlineModeFlow.value.copy(loadedPins = pins)
  }

  /**
   * Clears all offline mode data by resetting to empty maps.
   *
   * This is useful when exiting offline mode or when the user logs out.
   */
  fun clearOfflineMode() {
    _offlineModeFlow.value =
        OfflineMode(
            accounts = emptyMap(),
            discussions = emptyMap(),
            posts = emptyMap(),
            shopsToAdd = emptyMap(),
            spaceRentersToAdd = emptyMap(),
            loadedPins = emptyMap())
  }
}
