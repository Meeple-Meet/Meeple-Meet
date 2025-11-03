// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for displaying shops and space renters on a map.
 *
 * This ViewModel retrieves and exposes lists of shops and space renters through [StateFlow]s for UI
 * observation, typically for rendering markers on a map interface.
 *
 * @property shopRepository The repository used for shop operations.
 */
class MapViewModel(
    private val shopRepository: ShopRepository = RepositoryProvider.shops,
) : ViewModel() {
  private val _shops = MutableStateFlow<List<Shop>?>(null)

  /**
   * StateFlow exposing the list of shops.
   *
   * This flow emits null initially and updates with the shop list once [getShops] is called and
   * completes successfully.
   */
  val shops: StateFlow<List<Shop>?> = _shops

  /**
   * Retrieves a list of shops from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Upon successful retrieval,
   * the shops are emitted through [shops].
   *
   * @param count The maximum number of shops to retrieve.
   */
  fun getShops(count: UInt) {
    viewModelScope.launch { _shops.value = shopRepository.getShops(count) }
  }
}
