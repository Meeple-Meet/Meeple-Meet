// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for viewing shop details.
 *
 * This ViewModel retrieves and exposes a shop through a [StateFlow] for UI observation.
 *
 * @property repository The repository used for shop operations.
 */
class ShopViewModel(private val repository: ShopRepository = RepositoryProvider.shops) :
    ShopSearchViewModel() {
  private val _shop = MutableStateFlow<Shop?>(null)

  /**
   * StateFlow exposing the currently loaded shop.
   *
   * This flow emits null initially and updates with the shop data once [getShop] is called and
   * completes successfully.
   */
  val shop: StateFlow<Shop?> = _shop

  private var currentShopId: String? = null

  init {
    // Observe the offline cache for changes to the current shop
    viewModelScope.launch {
      OfflineModeManager.offlineModeFlow.collect { offlineMode ->
        val shopId = shop.value?.id ?: currentShopId
        if (shopId != null) {
          // Update the StateFlow when the cached shop changes
          val cached = offlineMode.shops[shopId]?.first
          if (cached != null) {
            _shop.value = cached
          }
        }
      }
    }
  }

  /**
   * Retrieves a shop by its ID from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Upon successful retrieval,
   * the shop is emitted through [shop].
   *
   * @param id The unique identifier of the shop to retrieve.
   * @throws IllegalArgumentException if the shop ID is blank.
   */
  fun getShop(id: String) {
    if (id.isBlank()) throw IllegalArgumentException("Shop ID cannot be blank")

    currentShopId = id

    viewModelScope.launch { OfflineModeManager.loadShop(id) { shop -> _shop.value = shop } }
  }

  /**
   * Retrieves a shop by the owner's ID from Firestore.
   *
   * This operation is performed asynchronously and returns the shop or null if not found.
   *
   * @param ownerId The unique identifier of the shop owner.
   * @return The Shop owned by the account, or null if no shop is found.
   */
  suspend fun getShopByOwnerId(ownerId: String): Shop? {
    return repository.getShopByOwnerId(ownerId)
  }
}
