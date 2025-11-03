// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for displaying shops and space renters on a map.
 *
 * This ViewModel retrieves and exposes lists of shops and space renters through [StateFlow]s for UI
 * observation, typically for rendering markers on a map interface.
 *
 * @property spaceRenterRepository The repository used for space renter operations.
 */
class MapViewModel(
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters
) : ViewModel() {
  private val _spaceRenters = MutableStateFlow<List<SpaceRenter>?>(null)

  /**
   * StateFlow exposing the list of space renters.
   *
   * This flow emits null initially and updates with the space renter list once [getSpaceRenters] is
   * called and completes successfully.
   */
  val spaceRenters: StateFlow<List<SpaceRenter>?> = _spaceRenters

  /**
   * Retrieves a list of space renters from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Upon successful retrieval,
   * the space renters are emitted through [spaceRenters].
   *
   * @param count The maximum number of space renters to retrieve.
   */
  fun getSpaceRenters(count: UInt) {
    viewModelScope.launch { _spaceRenters.value = spaceRenterRepository.getSpaceRenters(count) }
  }
}
