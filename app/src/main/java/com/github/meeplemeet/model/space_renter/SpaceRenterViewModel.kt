// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for viewing space renter details.
 *
 * This ViewModel retrieves and exposes a space renter through a [StateFlow] for UI observation.
 *
 * @property repository The repository used for space renter operations.
 */
class SpaceRenterViewModel(
    private val repository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val imageRepository: ImageRepository = RepositoryProvider.images
) : SpaceRenterSearchViewModel() {
  private val _spaceRenter = MutableStateFlow<SpaceRenter?>(null)
  private val _photos = MutableStateFlow<List<ByteArray>>(emptyList())
  val photos: StateFlow<List<ByteArray>> = _photos

  /**
   * StateFlow exposing the currently loaded space renter.
   *
   * This flow emits null initially and updates with the space renter data once [getSpaceRenter] is
   * called and completes successfully.
   */
  val spaceRenter: StateFlow<SpaceRenter?> = _spaceRenter

  /**
   * Retrieves a space renter by its ID from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Upon successful retrieval,
   * the space renter is emitted through [spaceRenter].
   *
   * @param id The unique identifier of the space renter to retrieve.
   * @throws IllegalArgumentException if the space renter ID is blank.
   */
  fun getSpaceRenter(id: String, context: android.content.Context) {
    if (id.isBlank()) throw IllegalArgumentException("SpaceRenter ID cannot be blank")

    viewModelScope.launch {
      val renter = repository.getSpaceRenter(id)
      _spaceRenter.value = renter

      val count = renter.photoCollectionUrl.size
      val images = imageRepository.loadSpaceRenterPhotos(context, id, count)
      _photos.value = images
    }
  }
}
