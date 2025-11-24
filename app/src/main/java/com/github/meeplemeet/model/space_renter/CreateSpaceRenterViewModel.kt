// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.account.Account
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
 */
class CreateSpaceRenterViewModel(
    private val repository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
) : SpaceRenterSearchViewModel() {
  /**
   * Creates a new space renter in Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Validates that:
   * - The space renter name is not blank
   * - Exactly 7 opening hours entries are provided (one for each day of the week)
   * - A valid address is provided
   *
   * The repository will automatically add the space renter ID to the owner's businesses
   * subcollection.
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
  ) {
    if (name.isBlank()) throw IllegalArgumentException("SpaceRenter name cannot be blank")

    val uniqueByDay = openingHours.distinctBy { it.day }
    if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")

    if (address == Location())
        throw IllegalArgumentException("An address it required to create a space renter")

    viewModelScope.launch {
      try {
        // Create the space renter WITHOUT photo URLs (they're local paths, not valid URLs)
        val created =
            repository.createSpaceRenter(
                owner,
                name,
                phone,
                email,
                website,
                address,
                openingHours,
                spaces,
                emptyList()) // Don't pass local file paths to Firestore

        val uploadedUrls =
            if (photoCollectionUrl.isNotEmpty()) {
              try {
                // Upload photos to Firebase Storage and get download URLs
                withContext(NonCancellable) {
                  imageRepository.saveSpaceRenterPhotos(
                      context, created.id, *photoCollectionUrl.toTypedArray())
                }
              } catch (e: Exception) {
                // Log and continue with empty list so the renter exists even if uploads fail.
                Log.e(
                    "upload", "Image upload failed for space renter ${created.id}: ${e.message}", e)
                emptyList<String>()
              }
            } else {
              emptyList()
            }

        // Update the document with Firebase Storage download URLs
        if (uploadedUrls.isNotEmpty()) {
          try {
            withContext(NonCancellable) {
              repository.updateSpaceRenter(id = created.id, photoCollectionUrl = uploadedUrls)
            }
          } catch (e: Exception) {
            // Updating should not crash the app; log and continue.
            Log.e(
                "upload",
                "Failed to update space renter ${created.id} with photo URLs: ${e.message}",
                e)
          }
        }
      } catch (e: Exception) {
        // Catch any unexpected exception from repository.createSpaceRenter and fail gracefully.
        Log.e("upload", "Failed to create space renter: ${e.message}", e)
        // Re-throw if you want the caller to handle it; otherwise swallow to avoid crash.
      }
    }
  }
}
