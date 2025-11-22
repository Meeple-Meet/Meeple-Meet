// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.Location
import kotlinx.coroutines.launch

/**
 * ViewModel for creating new shops.
 *
 * This ViewModel handles the creation of board game shops with validation logic to ensure all
 * required fields are properly provided.
 *
 * @property shopRepo The repository used for shop operations.
 * @property imageRepository The repository used for image operations.
 */
class CreateShopViewModel(
    private val shopRepo: ShopRepository = RepositoryProvider.shops,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    gameRepository: GameRepository = RepositoryProvider.games
) : ShopSearchViewModel(gameRepository) {
  /**
   * Creates a new shop in Firestore with photo upload support.
   *
   * This operation is performed asynchronously in the viewModelScope. Validates that:
   * - The shop name is not blank
   * - Exactly 7 opening hours entries are provided (one for each day of the week)
   * - A valid address is provided
   *
   * Photos are uploaded to Firebase Storage after the shop is created, and the shop document is
   * updated with the download URLs.
   *
   * @param context Android context for accessing cache directory.
   * @param owner The account that owns the shop.
   * @param name The name of the shop.
   * @param phone The contact phone number for the shop (optional).
   * @param email The contact email address for the shop (optional).
   * @param website The shop's website URL (optional).
   * @param address The physical location of the shop.
   * @param openingHours The shop's opening hours, must include exactly 7 entries (one per day).
   * @param gameCollection The collection of games available at the shop with their quantities
   *   (optional, defaults to empty).
   * @param photoCollectionUrl List of local file paths for the shop's photos (optional, defaults to
   *   empty). These will be uploaded to Firebase Storage.
   * @throws IllegalArgumentException if the shop name is blank, if not exactly 7 opening hours
   *   entries are provided, or if the address is not valid.
   */
  fun createShop(
      context: android.content.Context,
      owner: Account,
      name: String,
      phone: String = "",
      email: String,
      website: String = "",
      address: Location,
      openingHours: List<OpeningHours>,
      gameCollection: List<Pair<Game, Int>> = emptyList(),
      photoCollectionUrl: List<String> = emptyList()
  ) {
    if (name.isBlank()) throw IllegalArgumentException("Shop name cannot be blank")

    val uniqueByDay = openingHours.distinctBy { it.day }
    if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")

    if (address == Location())
        throw IllegalArgumentException("An address is required to create a shop")

    viewModelScope.launch {
      try {
        // Create the shop WITHOUT photo URLs (they're local paths, not valid URLs)
        val created =
            shopRepo.createShop(
                owner,
                name,
                phone,
                email,
                website,
                address,
                openingHours,
                gameCollection,
                emptyList()) // Don't pass local file paths to Firestore

        val uploadedUrls =
            if (photoCollectionUrl.isNotEmpty()) {
              try {
                // Upload photos to Firebase Storage and get download URLs
                imageRepository.saveShopPhotos(
                    context, created.id, *photoCollectionUrl.toTypedArray())
              } catch (e: Exception) {
                // Log and continue with empty list so the shop exists even if uploads fail.
                Log.e("upload", "Image upload failed for shop ${created.id}: ${e.message}", e)
                emptyList<String>()
              }
            } else {
              emptyList()
            }

        // Update the document with Firebase Storage download URLs
        if (uploadedUrls.isNotEmpty()) {
          try {
            shopRepo.updateShop(id = created.id, photoCollectionUrl = uploadedUrls)
          } catch (e: Exception) {
            // Updating should not crash the app; log and continue.
            Log.e("upload", "Failed to update shop ${created.id} with photo URLs: ${e.message}", e)
          }
        }
      } catch (e: Exception) {
        // Catch any unexpected exception from repository.createShop and fail gracefully.
        Log.e("upload", "Failed to create shop: ${e.message}", e)
        // Re-throw if you want the caller to handle it; otherwise swallow to avoid crash.
      }
    }
  }
}
