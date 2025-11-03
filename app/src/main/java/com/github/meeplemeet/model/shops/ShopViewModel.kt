// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class ShopViewModel(private val repository: ShopRepository = ShopRepository()) : ViewModel() {
  private val _shop = MutableStateFlow<Shop?>(null)

  /**
   * StateFlow exposing the currently loaded shop.
   *
   * This flow emits null initially and updates with the shop data once [getShop] is called and
   * completes successfully.
   */
  val shop: StateFlow<Shop?> = _shop

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

    viewModelScope.launch { _shop.value = repository.getShop(id) }
  }
}
