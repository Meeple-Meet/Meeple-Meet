// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.launch

/**
 * ViewModel for editing and deleting existing space renters.
 *
 * This ViewModel handles space renter updates and deletions with permission validation to ensure
 * only the space renter owner can perform these operations.
 *
 * @property spaceRenterRepository The repository used for space renter operations.
 */
class EditSpaceRenterViewModel(
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
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
    }
  }

  /**
   * Deletes a space renter from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the space renter owner
   * can delete the space renter.
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
      accountRepository.removeSpaceRenterId(requester.uid, spaceRenter.id)
    }
  }
}
