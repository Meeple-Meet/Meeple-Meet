// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.SearchViewModel
import com.github.meeplemeet.model.shared.location.Location

private const val PERMISSION_DENIED_MESSAGE =
    "Only the space renter's owner can edit their own space renter"

/**
 * Base ViewModel for space renter-related screens that need location selection functionality.
 *
 * Extends [SearchViewModel] to provide location search and selection with space renter owner
 * permission validation. This ViewModel ensures that only the space renter owner can modify
 * location-related data.
 */
open class SpaceRenterSearchViewModel : SearchViewModel() {

  /**
   * Sets the selected location for a space renter with permission validation.
   *
   * Only the space renter owner can select location for their space renter. Updates the location UI
   * state with the selected location.
   *
   * @param spaceRenter The space renter to modify.
   * @param requester The account requesting the change.
   * @param location The location to select.
   * @throws PermissionDeniedException if the requester is not the space renter owner.
   */
  fun setLocation(spaceRenter: SpaceRenter, requester: Account, location: Location) {
    if (spaceRenter.owner.uid != requester.uid)
        throw PermissionDeniedException(PERMISSION_DENIED_MESSAGE)

    setLocation(location)
  }

  /**
   * Updates the location search query for a space renter with permission validation.
   *
   * Only the space renter owner can search for location to add to their space renter. This method
   * updates the visible location query in the UI state and triggers a background search for
   * matching location.
   *
   * @param spaceRenter The space renter context.
   * @param requester The account requesting the search.
   * @param query The search query string.
   * @throws PermissionDeniedException if the requester is not the space renter owner.
   */
  fun setLocationQuery(spaceRenter: SpaceRenter, requester: Account, query: String) {
    if (spaceRenter.owner.uid != requester.uid)
        throw PermissionDeniedException(PERMISSION_DENIED_MESSAGE)

    setLocationQuery(query)
  }
}
