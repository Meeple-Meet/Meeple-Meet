package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for editing and deleting existing space renters.
 *
 * This ViewModel handles space renter updates and deletions with permission validation to ensure
 * only the space renter owner can perform these operations. It also handles offline mode by
 * synchronizing pending changes when connectivity is restored.
 *
 * @property spaceRenterRepository The repository used for space renter operations.
 */
class EditSpaceRenterViewModel(
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
) : SpaceRenterSearchViewModel() {
  private val _currentSpaceRenter = MutableStateFlow<SpaceRenter?>(null)
  val currentSpaceRenter: StateFlow<SpaceRenter?> = _currentSpaceRenter.asStateFlow()

  /**
   * Initializes the ViewModel with a space renter. Also loads it from cache/repository to ensure we
   * have the latest data.
   */
  fun initialize(spaceRenter: SpaceRenter) {
    _currentSpaceRenter.value = spaceRenter

    // Load latest version from cache or repository
    viewModelScope.launch {
      OfflineModeManager.loadSpaceRenter(spaceRenter.id) { loaded ->
        if (loaded != null) {
          _currentSpaceRenter.value = loaded
        }
      }
    }
  }
  /**
   * Updates one or more fields of an existing space renter.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the space renter owner
   * can update the space renter. Validates that:
   * - The requester is the space renter owner
   * - If provided, the space renter name is not blank
   * - If provided, exactly 7 opening hours entries are included (one for each day of the week)
   * - If provided, the address is valid
   *
   * This function automatically handles both online and offline modes through OfflineModeManager.
   *
   * @param spaceRenter The space renter to update.
   * @param requester The account requesting the update.
   * @param owner The new owner account (optional).
   * @param name The new name of the space rental business (optional).
   * @param phone The new contact phone number (optional).
   * @param email The new contact email address (optional).
   * @param website The new website URL (optional).
   * @param address The new physical location (optional).
   * @param openingHours The new opening hours, must include exactly 7 entries if provided
   *   (optional).
   * @param spaces The new collection of rentable spaces (optional).
   * @throws PermissionDeniedException if the requester is not the space renter owner.
   * @throws IllegalArgumentException if the space renter name is blank, if not exactly 7 opening
   *   hours entries are provided, or if the address is not valid.
   */
  fun updateSpaceRenter(
      spaceRenter: SpaceRenter,
      requester: Account,
      owner: Account? = null,
      name: String? = null,
      phone: String? = null,
      email: String? = null,
      website: String? = null,
      address: Location? = null,
      openingHours: List<OpeningHours>? = null,
      spaces: List<Space>? = null,
      photoCollectionUrl: List<String>? = null
  ) {
    val params =
        SpaceRenterUpdateParams(
            owner, name, phone, email, website, address, openingHours, spaces, photoCollectionUrl)
    validateUpdateRequest(spaceRenter, requester, params)

    viewModelScope.launch {
      val isOnline = OfflineModeManager.hasInternetConnection.first()

      if (isOnline) {
        handleOnlineUpdate(spaceRenter, params)
      } else {
        handleOfflineUpdate(spaceRenter, params)
      }
    }
  }

  /**
   * Validates the update request parameters.
   *
   * @throws PermissionDeniedException if the requester is not the owner
   * @throws IllegalArgumentException if validation fails
   */
  private fun validateUpdateRequest(
      spaceRenter: SpaceRenter,
      requester: Account,
      params: SpaceRenterUpdateParams
  ) {
    if (spaceRenter.owner.uid != requester.uid) {
      throw PermissionDeniedException("Only the space renter's owner can edit his own space renter")
    }

    require(!(params.name != null && params.name.isBlank())) { "SpaceRenter name cannot be blank" }

    if (params.openingHours != null) {
      val uniqueByDay = params.openingHours.distinctBy { it.day }
      require(uniqueByDay.size == 7) { "7 opening hours are needed" }
    }

    require(!(params.address != null && params.address == Location())) {
      "An address is required to create a space renter"
    }
  }

  /** Handles online update by persisting to repository and updating cache. */
  private suspend fun handleOnlineUpdate(
      spaceRenter: SpaceRenter,
      params: SpaceRenterUpdateParams
  ) {
    spaceRenterRepository.updateSpaceRenter(
        spaceRenter.id,
        params.owner?.uid,
        params.name,
        params.phone,
        params.email,
        params.website,
        params.address,
        params.openingHours,
        params.spaces,
        params.photoCollectionUrl)

    val refreshed = spaceRenterRepository.getSpaceRenterSafe(spaceRenter.id)

    if (refreshed != null) {
      // Update both cache and UI state
      OfflineModeManager.updateSpaceRenterCache(refreshed)
      _currentSpaceRenter.value = refreshed
    }

    OfflineModeManager.clearSpaceRenterChanges(spaceRenter.id)
  }

  /** Handles offline update by recording changes and updating cache. */
  private suspend fun handleOfflineUpdate(
      spaceRenter: SpaceRenter,
      params: SpaceRenterUpdateParams
  ) {
    // OFFLINE: Record changes and update cached space renter object
    val changes = buildChangeMap(params)

    // Apply changes to create updated space renter object
    val updatedSpaceRenter =
        spaceRenter.copy(
            owner = params.owner ?: spaceRenter.owner,
            name = params.name ?: spaceRenter.name,
            phone = params.phone ?: spaceRenter.phone,
            email = params.email ?: spaceRenter.email,
            website = params.website ?: spaceRenter.website,
            address = params.address ?: spaceRenter.address,
            openingHours = params.openingHours ?: spaceRenter.openingHours,
            spaces = params.spaces ?: spaceRenter.spaces,
            photoCollectionUrl = params.photoCollectionUrl ?: spaceRenter.photoCollectionUrl)

    // Update cache with modified space renter
    OfflineModeManager.updateSpaceRenterCache(updatedSpaceRenter)

    // Update local StateFlow
    _currentSpaceRenter.value = updatedSpaceRenter

    // Record changes for sync
    changes.forEach { (property, value) ->
      OfflineModeManager.setSpaceRenterChange(updatedSpaceRenter, property, value)
    }
  }

  private fun buildChangeMap(params: SpaceRenterUpdateParams): Map<String, Any> {
    val changes = mutableMapOf<String, Any>()
    if (params.owner != null) changes[SpaceRenter::owner.name] = params.owner.uid
    if (params.name != null) changes[SpaceRenter::name.name] = params.name
    if (params.phone != null) changes[SpaceRenter::phone.name] = params.phone
    if (params.email != null) changes[SpaceRenter::email.name] = params.email
    if (params.website != null) changes[SpaceRenter::website.name] = params.website
    if (params.address != null) changes[SpaceRenter::address.name] = params.address
    if (params.openingHours != null) changes[SpaceRenter::openingHours.name] = params.openingHours
    if (params.spaces != null) changes[SpaceRenter::spaces.name] = params.spaces
    if (params.photoCollectionUrl != null)
        changes[SpaceRenter::photoCollectionUrl.name] = params.photoCollectionUrl
    return changes
  }

  /** Deletes a space renter. */
  fun deleteSpaceRenter(spaceRenter: SpaceRenter, requester: Account) {
    if (spaceRenter.owner.uid != requester.uid)
        throw PermissionDeniedException(
            "Only the space renter's owner can delete his own space renter")

    viewModelScope.launch {
      spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
      OfflineModeManager.removeSpaceRenter(spaceRenter.id)
    }
  }
}

/** Data class to encapsulate update parameters and reduce parameter count in methods. */
private data class SpaceRenterUpdateParams(
    val owner: Account? = null,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: Location? = null,
    val openingHours: List<OpeningHours>? = null,
    val spaces: List<Space>? = null,
    val photoCollectionUrl: List<String>? = null
)
