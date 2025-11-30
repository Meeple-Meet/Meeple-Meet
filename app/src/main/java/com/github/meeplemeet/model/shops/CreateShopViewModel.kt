// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.Location
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
   * The repository will automatically add the shop ID to the owner's businesses subcollection.
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
   * @param photoCollectionUrl List of URLs for the shop's photos (optional, defaults to empty).
   * @return The created Shop object with its generated ID.
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
      gameCollection: List<GameItem> = emptyList(),
      photoCollectionUrl: List<String> = emptyList()
  ): Shop {
    if (name.isBlank()) throw IllegalArgumentException("Shop name cannot be blank")

        val uniqueByDay = openingHours.distinctBy { it.day }
        if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")

        if (address == Location())
            throw IllegalArgumentException("An address is required to create a shop")

        viewModelScope.launch {
            // Check internet connection status
            val isOnline = OfflineModeManager.hasInternetConnection.value

            if (isOnline) {
                // ONLINE: Create immediately in Firestore
                val created = shopRepo.createShop(
                    owner,
                    name,
                    phone,
                    email,
                    website,
                    address,
                    openingHours,
                    gameCollection,
                    emptyList()
                )
                val uploadedUrls =
                    if (photoCollectionUrl.isNotEmpty()) {
                        try {
                            // Upload photos to Firebase Storage and get download URLs
                            val urls =
                            withContext(NonCancellable) {
                                imageRepository.saveShopPhotos(
                                    context, created.id, *photoCollectionUrl.toTypedArray()
                                )
                            }
                            urls
                        } catch (e: Exception) {
                            throw Exception("Photo upload failed: ${e.message}", e)
                        }
                    } else {
                        emptyList()
                    }

                // Update the document with Firebase Storage download URLs
                if (uploadedUrls.isNotEmpty()) {
                    try {
                        withContext(NonCancellable) {
                            shopRepo.updateShop(id = created.id, photoCollectionUrl = uploadedUrls)
                        }
                    } catch (e: Exception) {
                        // Updating should not crash the app; log and continue.
                        throw Exception("Failed to save photo URLs: ${e.message}", e)
                    }
                }
                // Return the created shop with updated photo URLs
            } else {
                // OFFLINE: Queue for later creation

                // Generate a temporary ID
                val tempId = "temp_${System.currentTimeMillis()}_${owner.uid}"

                // Create the Shop object with temporary ID
                val pendingShop =
                    Shop(
                        id = tempId,
                        owner = owner,
                        name = name,
                        phone = phone,
                        email = email,
                        website = website,
                        address = address,
                        openingHours = openingHours,
                        gameCollection = gameCollection,
                        photoCollectionUrl = photoCollectionUrl
                    )

                // Add to offline cache with pending creation marker
                OfflineModeManager.addPendingShop(pendingShop)
      }
    }

    // Return a placeholder shop since we can't return the real one immediately
    return Shop(
        id = "pending",
        email = email,
        phone = phone,
        website = website,
        owner = owner,
        name = name,
        address = address,
        openingHours = openingHours,
        gameCollection = gameCollection)
      }
    }
