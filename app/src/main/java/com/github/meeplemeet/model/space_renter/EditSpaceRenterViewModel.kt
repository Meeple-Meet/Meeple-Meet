// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
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
    if (spaceRenter.owner.uid != requester.uid)
        throw PermissionDeniedException(
            "Only the space renter's owner can edit his own space renter")

    if (name != null && name.isBlank())
        throw IllegalArgumentException("SpaceRenter name cannot be blank")

    if (openingHours != null) {
      val uniqueByDay = openingHours.distinctBy { it.day }
      if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")
    }

    if (address != null && address == Location())
        throw IllegalArgumentException("An address it required to create a space renter")

    viewModelScope.launch {
      val isOnline = OfflineModeManager.hasInternetConnection.value

      if (isOnline) {
        // Online: update repository directly
        spaceRenterRepository.updateSpaceRenter(
            spaceRenter.id,
            owner?.uid,
            name,
            phone,
            email,
            website,
            address,
            openingHours,
            spaces,
            photoCollectionUrl)

        // Clear offline changes after successful update
        OfflineModeManager.clearSpaceRenterChanges(spaceRenter.id)
      } else {
        // Offline: store changes in OfflineModeManager
        val changes = mutableMapOf<String, Any>()
        if (owner != null) changes[SpaceRenter::owner.name] = owner.uid
        if (name != null) changes[SpaceRenter::name.name] = name
        if (phone != null) changes[SpaceRenter::phone.name] = phone
        if (email != null) changes[SpaceRenter::email.name] = email
        if (website != null) changes[SpaceRenter::website.name] = website
        if (address != null) changes[SpaceRenter::address.name] = address
        if (openingHours != null) changes[SpaceRenter::openingHours.name] = openingHours
        if (spaces != null) changes[SpaceRenter::spaces.name] = spaces
        // remove this line when images are supported offline
        // if (photoCollectionUrl != null) changes[SpaceRenter::photoCollectionUrl.name] =
        // photoCollectionUrl

        // Store each change in offline cache
        changes.forEach { (property, value) ->
          OfflineModeManager.setSpaceRenterChange(spaceRenter, property, value)
        }
      }
    }
  }

  /**
   * Deletes a space renter from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the space renter owner
   * can delete the space renter. The repository will automatically remove the space renter ID from
   * the owner's businesses subcollection.
   *
   * @param spaceRenter The space renter to delete.
   * @param requester The account requesting the deletion.
   * @throws PermissionDeniedException if the requester is not the space renter owner.
   */
  fun deleteSpaceRenter(spaceRenter: SpaceRenter, requester: Account) {
    if (spaceRenter.owner.uid != requester.uid)
        throw PermissionDeniedException(
            "Only the space renter's owner can delete his own space renter")

    viewModelScope.launch {
      spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
      // Remove from offline cache after deletion
      OfflineModeManager.removeSpaceRenter(spaceRenter.id)
    }
  }
}
