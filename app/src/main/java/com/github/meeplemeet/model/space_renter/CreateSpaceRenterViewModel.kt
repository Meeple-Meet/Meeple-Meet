package com.github.meeplemeet.model.space_renter

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.launch

class CreateSpaceRenterViewModel(
    private val repository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
) : SpaceRenterSearchViewModel() {

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
                imageRepository.saveSpaceRenterPhotos(
                    context, created.id, *photoCollectionUrl.toTypedArray())
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
            repository.updateSpaceRenter(id = created.id, photoCollectionUrl = uploadedUrls)
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
