// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.launch

/**
 * ViewModel for creating new space renters.
 *
 * This ViewModel handles the creation of space rental businesses with validation logic to ensure
 * all required fields are properly provided.
 *
 * @property repository The repository used for space renter operations.
 */
class CreateSpaceRenterViewModel(
    private val repository: SpaceRenterRepository = RepositoryProvider.spaceRenters
) : SpaceRenterSearchViewModel() {
  /**
   * Creates a new space renter in Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Validates that:
   * - The space renter name is not blank
   * - Exactly 7 opening hours entries are provided (one for each day of the week)
   * - A valid address is provided
   *
   * @param owner The account that owns the space rental business.
   * @param name The name of the space rental business.
   * @param phone The contact phone number for the space renter (optional).
   * @param email The contact email address for the space renter (optional).
   * @param website The space renter's website URL (optional).
   * @param address The physical location of the space rental business.
   * @param openingHours The space renter's opening hours, must include exactly 7 entries (one per
   *   day).
   * @param spaces The collection of rentable spaces with their details (optional, defaults to
   *   empty).
   * @throws IllegalArgumentException if the space renter name is blank, if not exactly 7 opening
   *   hours entries are provided, or if the address is not valid.
   */
  fun createSpaceRenter(
      owner: Account,
      name: String,
      phone: String = "",
      email: String = "",
      website: String = "",
      address: Location,
      openingHours: List<OpeningHours>,
      spaces: List<Space> = emptyList(),
      photoCollectionUrl: List<String> = emptyList(),
  ) {
    if (name.isBlank()) throw IllegalArgumentException("SpaceRenter name cannot be blank")

    val uniqueByDay = openingHours.distinctBy { it.day }
    if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")

    if (address == Location())
        throw IllegalArgumentException("An address it required to create a space renter")

    viewModelScope.launch {
      repository.createSpaceRenter(
          owner, name, phone, email, website, address, openingHours, spaces, photoCollectionUrl)
    }
  }
}
