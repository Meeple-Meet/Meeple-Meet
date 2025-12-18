// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for creating new space renters.
 *
 * This ViewModel handles the creation of space rental businesses with validation logic to ensure
 * all required fields are properly provided.
 *
 * @property repository The repository used for space renter operations.
 * @property imageRepository The repository used for image operations.
 */
class CreateSpaceRenterViewModel(
    private val repository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
) : SpaceRenterSearchViewModel() {
  /**
   * Creates a new space renter, either immediately (online) or queued for sync (offline).
   *
   * This operation is performed asynchronously in the viewModelScope. Validates that:
   * - The space renter name is not blank
   * - Exactly 7 opening hours entries are provided (one for each day of the week)
   * - A valid address is provided
   *
   * **Online behavior**: Creates the space renter in Firestore immediately. The repository will
   * automatically add the space renter ID to the owner's businesses subcollection.
   *
   * **Offline behavior**: Queues the space renter for creation by storing it in the offline cache
   * with a "_pending_create" marker. When connection is restored, a sync mechanism should detect
   * this pending creation and push it to the server.
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
   * @param photoCollectionUrl List of photo URLs (optional, defaults to empty).
   * @throws IllegalArgumentException if validation fails.
   */
  fun createSpaceRenter(
      context: android.content.Context,
      owner: Account,
      name: String,
      phone: String = "",
      email: String = "",
      website: String = "",
      address: Location,
      openingHours: List<OpeningHours>,
      spaces: List<Space> = emptyList(),
      photoCollectionUrl: List<String> = emptyList(),
      onSuccess: (SpaceRenter) -> Unit = {},
      onFailure: (Exception) -> Unit = {}
  ) {
    // Validation
    if (name.isBlank()) {
      onFailure(IllegalArgumentException("SpaceRenter name cannot be blank"))
      return
    }

    val uniqueByDay = openingHours.distinctBy { it.day }
    if (uniqueByDay.size != 7) {
      onFailure(IllegalArgumentException("7 opening hours are needed"))
      return
    }

    if (address == Location()) {
      onFailure(IllegalArgumentException("An address is required to create a space renter"))
      return
    }

    viewModelScope.launch {
      val isOnline = OfflineModeManager.hasInternetConnection.value

      if (isOnline) {
        try {
          val created =
              repository.createSpaceRenter(
                  owner, name, phone, email, website, address, openingHours, spaces, emptyList())

          val uploadedUrls =
              if (photoCollectionUrl.isNotEmpty()) {
                try {
                  // Upload photos to Firebase Storage and get download URLs
                  val urls =
                      withContext(NonCancellable) {
                        imageRepository.saveSpaceRenterPhotos(
                            context, created.id, *photoCollectionUrl.toTypedArray())
                      }
                  urls
                } catch (e: Exception) {
                  throw Exception("Photo upload failed: ${e.message}", e)
                }
              } else {
                emptyList<String>()
              }
          // Update the document with Firebase Storage download URLs
          if (uploadedUrls.isNotEmpty()) {
            try {
              withContext(NonCancellable) {
                repository.updateSpaceRenter(id = created.id, photoCollectionUrl = uploadedUrls)
              }
            } catch (e: Exception) {
              throw Exception("Failed to save photo URLs: ${e.message}", e)
            }
          }
          onSuccess(created)
        } catch (e: Exception) {
          onFailure(Exception("Failed to create space renter: ${e.message}", e))
        }
      } else {
        // OFFLINE: Queue for later creation

        // Generate a temporary ID (you might need to adjust this based on your ID generation
        // strategy)
        val tempId = "temp_${System.currentTimeMillis()}_${owner.uid}"

        // Create the SpaceRenter object with temporary ID
        val pendingRenter =
            SpaceRenter(
                id = tempId,
                owner = owner,
                name = name,
                phone = phone,
                email = email,
                website = website,
                address = address,
                openingHours = openingHours,
                spaces = spaces,
                photoCollectionUrl = photoCollectionUrl)

        // Add to offline cache with pending creation marker
        OfflineModeManager.addPendingSpaceRenter(pendingRenter)

        // Optional: Show a message to user that creation will happen when online
        // You might want to expose a callback or LiveData for this
        onSuccess(pendingRenter)
      }
    }
  }
}
