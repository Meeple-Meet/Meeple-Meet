// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.Location

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
   * @param owner The account that owns the shop.
   * @param name The name of the shop.
   * @param phone The contact phone number for the shop (optional).
   * @param email The contact email address for the shop (optional).
   * @param website The shop's website URL (optional).
   * @param address The physical location of the shop.
   * @param openingHours The shop's opening hours, must include exactly 7 entries (one per day).
   * @param gameCollection The collection of games available at the shop with their quantities
   *   (optional, defaults to empty).
   * @return The created Shop object with its generated ID.
   * @throws IllegalArgumentException if the shop name is blank, if not exactly 7 opening hours
   *   entries are provided, or if the address is not valid.
   */
  suspend fun createShop(
      owner: Account,
      name: String,
      phone: String = "",
      email: String,
      website: String = "",
      address: Location,
      openingHours: List<OpeningHours>,
      gameCollection: List<Pair<Game, Int>> = emptyList()
  ): Shop {
    if (name.isBlank()) throw IllegalArgumentException("Shop name cannot be blank")

    val uniqueByDay = openingHours.distinctBy { it.day }
    if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")

    if (address == Location())
        throw IllegalArgumentException("An address is required to create a shop")

    return shopRepo.createShop(
        owner, name, phone, email, website, address, openingHours, gameCollection)
  }
}
