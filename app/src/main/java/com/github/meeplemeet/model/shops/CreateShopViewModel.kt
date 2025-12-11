// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
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
 */
class CreateShopViewModel(
    private val shopRepo: ShopRepository = RepositoryProvider.shops,
    gameRepository: GameRepository = RepositoryProvider.games
) : ShopSearchViewModel(gameRepository) {
  /**
   * Creates a new shop in Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Validates that:
   * - The shop name is not blank
   * - Exactly 7 opening hours entries are provided (one for each day of the week)
   * - A valid address is provided
   *
   * The repository will automatically add the shop ID to the owner's businesses subcollection.
   *
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
        shopRepo.createShop(
            owner,
            name,
            phone,
            email,
            website,
            address,
            openingHours,
            gameCollection,
            photoCollectionUrl)
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
                photoCollectionUrl = photoCollectionUrl)

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
