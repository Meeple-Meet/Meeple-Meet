package com.github.meeplemeet.model.shops

// Claude Code generated the documentation

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AccountRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing board game shops in Firestore.
 *
 * Handles CRUD operations for shop documents, including fetching owner accounts and game
 * collections from their respective repositories.
 *
 * @property db The Firestore database instance to use for operations.
 */
class ShopRepository(
    val accountRepository: AccountRepository = RepositoryProvider.accounts,
    val gameRepository: FirestoreGameRepository = RepositoryProvider.games,
    val geoPinRepository: StorableGeoPinRepository = RepositoryProvider.geoPins
) : FirestoreRepository("shops") {
  /**
   * Creates a new shop and stores it in Firestore.
   *
   * @param owner The account that owns the shop.
   * @param name The name of the shop.
   * @param phone The contact phone number for the shop (optional).
   * @param email The contact email address for the shop (optional).
   * @param website The shop's website URL (optional).
   * @param address The physical location of the shop.
   * @param openingHours The shop's opening hours as a list of time pairs (start time, end time).
   * @param gameCollection The collection of games available at the shop with their quantities
   *   (optional, defaults to empty).
   * @return The created Shop with a generated unique ID.
   */
  suspend fun createShop(
      owner: Account,
      name: String,
      phone: String = "",
      email: String = "",
      website: String = "",
      address: Location,
      openingHours: List<OpeningHours>,
      gameCollection: List<Pair<Game, Int>> = emptyList(),
      photoCollectionUrl: List<String> = emptyList()
  ): Shop {
    val shop =
        Shop(
            newUUID(),
            owner,
            name,
            phone,
            email,
            website,
            address,
            openingHours,
            gameCollection,
            photoCollectionUrl)
    collection.document(shop.id).set(toNoUid(shop)).await()

    geoPinRepository.upsertGeoPin(ref = shop.id, type = PinType.SHOP, location = address)

    return shop
  }

  /**
   * Retrieves a list of shops from Firestore.
   *
   * Fetches up to N shops, then retrieves owner accounts and game collections for each shop in
   * parallel for optimal performance.
   *
   * @param maxResults The maximum number of shops to retrieve.
   * @return A list of Shop objects, up to maxResults in size.
   */
  suspend fun getShops(maxResults: UInt): List<Shop> {
    val snapshot = collection.limit(maxResults.toLong()).get().await()

    return coroutineScope {
      snapshot.documents
          .map { doc ->
            async {
              val shopNoUid = doc.toObject(ShopNoUid::class.java) ?: return@async null

              // Fetch the owner account
              val owner = accountRepository.getAccount(shopNoUid.ownerId)

              // Fetch all games in the game collection
              val gameCollection =
                  shopNoUid.gameCollection
                      .map { gameItem ->
                        async {
                          val game = gameRepository.getGameById(gameItem.gameId)
                          game to gameItem.quantity
                        }
                      }
                      .awaitAll()

              fromNoUid(doc.id, shopNoUid, owner, gameCollection)
            }
          }
          .awaitAll()
          .filterNotNull()
    }
  }

  /**
   * Retrieves a shop by its ID from Firestore.
   *
   * Fetches the shop document, then retrieves the owner account and all games in the game
   * collection in parallel for optimal performance.
   *
   * @param id The unique identifier of the shop to retrieve.
   * @return The Shop with the specified ID.
   * @throws IllegalArgumentException if the shop does not exist or if the data cannot be parsed.
   */
  suspend fun getShop(id: String): Shop {
    val snapshot = collection.document(id).get().await()
    if (!snapshot.exists()) throw IllegalArgumentException("Shop with the given ID does not exist")

    val shopNoUid =
        snapshot.toObject(ShopNoUid::class.java)
            ?: throw IllegalArgumentException("Failed to parse shop data")

    // Fetch the owner account
    val owner = accountRepository.getAccount(shopNoUid.ownerId)

    // Fetch all games in the game collection
    val gameCollection = coroutineScope {
      shopNoUid.gameCollection
          .map { gameItem ->
            async {
              val game = gameRepository.getGameById(gameItem.gameId)
              game to gameItem.quantity
            }
          }
          .awaitAll()
    }

    return fromNoUid(id, shopNoUid, owner, gameCollection)
  }

  /**
   * Updates one or more fields of a shop in Firestore.
   *
   * Only the provided (non-null) fields are updated. At least one field must be provided.
   *
   * @param id The unique identifier of the shop to update.
   * @param ownerId The new owner ID (optional).
   * @param phone The new contact phone number (optional).
   * @param email The new contact email address (optional).
   * @param website The new website URL (optional).
   * @param address The new physical location (optional).
   * @param openingHours The new opening hours (optional).
   * @param gameCollection The new game collection (optional).
   * @throws IllegalArgumentException if no fields are provided for update.
   */
  suspend fun updateShop(
      id: String,
      ownerId: String? = null,
      name: String? = null,
      phone: String? = null,
      email: String? = null,
      website: String? = null,
      address: Location? = null,
      openingHours: List<OpeningHours>? = null,
      gameCollection: List<Pair<Game, Int>>? = null,
      photoCollectionUrl: List<String>? = null
  ) {
    val updates = mutableMapOf<String, Any>()

    ownerId?.let { updates[ShopNoUid::ownerId.name] = ownerId }
    name?.let { updates[ShopNoUid::name.name] = name }
    phone?.let { updates[ShopNoUid::phone.name] = phone }
    email?.let { updates[ShopNoUid::email.name] = email }
    website?.let { updates[ShopNoUid::website.name] = website }
    address?.let { updates[ShopNoUid::address.name] = address }
    openingHours?.let { updates[ShopNoUid::openingHours.name] = openingHours }
    gameCollection?.let {
      updates[ShopNoUid::gameCollection.name] =
          gameCollection.map { (game, count) -> GameItem(game.uid, count) }
    }
    photoCollectionUrl?.let { updates[ShopNoUid::photoCollectionUrl.name] = photoCollectionUrl }

    if (updates.isEmpty())
        throw IllegalArgumentException("At least one field must be provided for update")

    collection.document(id).update(updates).await()

    if (address != null) geoPinRepository.upsertGeoPin(id, PinType.SHOP, address)
  }

  /**
   * Deletes a shop from Firestore.
   *
   * @param id The unique identifier of the shop to delete.
   */
  suspend fun deleteShop(id: String) {
    geoPinRepository.deleteGeoPin(id)
    collection.document(id).delete().await()
  }

  /**
   * Retrieves the first shop owned by the specified account from Firestore.
   *
   * @param ownerId The unique identifier of the shop owner.
   * @return The Shop owned by the account, or null if no shop is found.
   */
  suspend fun getShopByOwnerId(ownerId: String): Shop? {
    val snapshot = collection.whereEqualTo(ShopNoUid::ownerId.name, ownerId).limit(1).get().await()

    if (snapshot.isEmpty) return null

    val doc = snapshot.documents.first()
    val shopNoUid = doc.toObject(ShopNoUid::class.java) ?: return null

    // Fetch the owner account
    val owner = accountRepository.getAccount(shopNoUid.ownerId)

    // Fetch all games in the game collection
    val gameCollection = coroutineScope {
      shopNoUid.gameCollection
          .map { gameItem ->
            async {
              val game = gameRepository.getGameById(gameItem.gameId)
              game to gameItem.quantity
            }
          }
          .awaitAll()
    }

    return fromNoUid(doc.id, shopNoUid, owner, gameCollection)
  }
}
