// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for displaying shops on a map.
 *
 * This ViewModel retrieves and exposes a list of shops through a [StateFlow] for UI observation,
 * typically for rendering shop markers on a map interface.
 *
 * @property repository The repository used for shop operations.
 */
class MapViewModel(private val repository: ShopRepository = RepositoryProvider.shops) :
    ViewModel() {
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
    viewModelScope.launch { _shops.value = repository.getShops(count) }
  }
}
