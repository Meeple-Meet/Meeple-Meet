// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
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

  private var currentSpaceRenterId: String? = null

  init {
    // Observe the offline cache for changes to the current space renter
    viewModelScope.launch {
      OfflineModeManager.offlineModeFlow.collect { offlineMode ->
        val renterId = spaceRenter.value?.id ?: currentSpaceRenterId
        if (renterId != null) {
          // Update the StateFlow when the cached space renter changes
          val cached = offlineMode.spaceRenters[renterId]?.first
          if (cached != null) {
            _spaceRenter.value = cached
          }
        }
      }
    }
  }

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

    currentSpaceRenterId = id
      

    viewModelScope.launch {
        OfflineModeManager.loadSpaceRenter(id) { spaceRenter -> _spaceRenter.value = spaceRenter }
        val count = spaceRenter.value?.photoCollectionUrl?.size
        val images = count?.let { imageRepository.loadSpaceRenterPhotos(context, id, it) }
        if (images != null) {
            _photos.value = images
        }
        }
  }
  }
