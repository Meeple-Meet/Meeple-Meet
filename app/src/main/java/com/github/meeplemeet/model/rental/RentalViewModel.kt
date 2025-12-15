package com.github.meeplemeet.model.rental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing rentals.
 *
 * This ViewModel acts as a bridge between the UI and the RentalRepository. It exposes StateFlows to
 * observe rental data and provides methods to create, load, enrich, and update rentals. Currently
 * focused on SPACE rentals, but designed to be extended for other resource types (GAME...).
 */
class RentalViewModel(
    private val rentalRepository: RentalRepository = RepositoryProvider.rentals,
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters
) : ViewModel() {

  // StateFlow holding all rentals for the current user
  private val _userRentals = MutableStateFlow<List<RentalResourceInfo>>(emptyList())
  val userRentals: StateFlow<List<RentalResourceInfo>> = _userRentals.asStateFlow()

  // StateFlow holding active space rentals for the current user
  private val _activeSpaceRentals = MutableStateFlow<List<RentalResourceInfo>>(emptyList())
  val activeSpaceRentals: StateFlow<List<RentalResourceInfo>> = _activeSpaceRentals.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  /**
   * Creates a new space rental.
   *
   * This method checks availability before creating the rental.
   *
   * @param renterId ID of the account renting the space.
   * @param spaceRenterId ID of the SpaceRenter (business offering spaces).
   * @param spaceIndex Index of the specific space within the SpaceRenter.
   * @param startDate Start date/time of the rental.
   * @param endDate End date/time of the rental.
   * @param totalCost Total cost of the rental.
   * @param notes Optional notes (e.g. "Need projector").
   * @return The unique ID of the created rental.
   * @throws IllegalStateException if the space is not available.
   */
  suspend fun createSpaceRental(
      renterId: String,
      spaceRenterId: String,
      spaceIndex: String,
      startDate: Timestamp,
      endDate: Timestamp,
      totalCost: Double,
      notes: String = ""
  ): String {
    // Check availability
    val isAvailable =
        rentalRepository.isResourceAvailable(
            resourceId = spaceRenterId,
            resourceDetailId = spaceIndex,
            startDate = startDate,
            endDate = endDate)

    if (!isAvailable) {
      throw IllegalStateException("This space is not available for the selected period")
    }

    val rental =
        rentalRepository.createRental(
            renterId = renterId,
            type = RentalType.SPACE,
            resourceId = spaceRenterId,
            resourceDetailId = spaceIndex,
            startDate = startDate,
            endDate = endDate,
            totalCost = totalCost,
            notes = notes)

    return rental.uid
  }

  /**
   * Loads all rentals for a given user.
   *
   * @param userId ID of the user. Updates [userRentals] StateFlow with enriched rental information.
   */
  fun loadUserRentals(userId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val rentals = rentalRepository.getRentalsByUser(userId)
        _userRentals.value = enrichRentals(rentals)
      } catch (e: Exception) {
        e.printStackTrace()
        _userRentals.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Loads active space rentals for a given user.
   *
   * Active rentals are defined as CONFIRMED and not yet expired. Used to populate rental selectors
   * in the UI.
   *
   * @param userId ID of the user. Updates [activeSpaceRentals] StateFlow with enriched rental
   *   information.
   */
  fun loadActiveSpaceRentals(userId: String) {
    viewModelScope.launch {
      _isLoading.value = true
      try {
        val rentals =
            rentalRepository.getActiveRentalsByType(renterId = userId, type = RentalType.SPACE)
        _activeSpaceRentals.value = enrichRentals(rentals)
      } catch (e: Exception) {
        e.printStackTrace()
        _activeSpaceRentals.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Enriches rentals with resource information for display.
   *
   * For SPACE rentals:
   * - Retrieves the SpaceRenter by [resourceId].
   * - Resolves the specific space using [resourceDetailId] (index).
   * - Builds a [RentalResourceInfo] with human-readable details.
   *
   * Future: extend to handle GAME and EQUIPMENT rentals.
   *
   * @param rentals List of rentals to enrich.
   * @return List of enriched rental resource info objects.
   */
  private suspend fun enrichRentals(rentals: List<Rental>): List<RentalResourceInfo> {
    return rentals.mapNotNull { rental ->
      when (rental.type) {
        RentalType.SPACE -> {
          val spaceRenter = spaceRenterRepository.getSpaceRenterSafe(rental.resourceId)
          if (spaceRenter != null) {
            val spaceIndex = rental.resourceDetailId.toIntOrNull() ?: 0
            val space = spaceRenter.spaces.getOrNull(spaceIndex)
            if (space != null) {
              RentalResourceInfo(
                  rental = rental,
                  resourceName = spaceRenter.name,
                  resourceAddress = spaceRenter.address,
                  detailInfo = "Space N°${spaceIndex + 1} - ${space.seats} seats")
            } else null
          } else null
        }
        // WIP: Other types to handle must be added here
        else -> null
      }
    }
  }

  /**
   * Cancels a rental by updating its status to CANCELLED.
   *
   * @param rentalId ID of the rental to cancel.
   */
  fun cancelRental(rentalId: String) {
    viewModelScope.launch {
      try {
        rentalRepository.cancelRental(rentalId)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * Associates a rental with a session.
   *
   * @param rentalId ID of the rental.
   * @param sessionId ID of the session to associate.
   */
  suspend fun associateRentalWithSession(rentalId: String, sessionId: String) {
    rentalRepository.associateWithSession(rentalId, sessionId)
  }

  /**
   * Removes the association between a rental and a session.
   *
   * @param rentalId ID of the rental.
   */
  suspend fun dissociateRentalFromSession(rentalId: String) {
    rentalRepository.dissociateFromSession(rentalId)
  }
}
