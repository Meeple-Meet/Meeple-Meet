package com.github.meeplemeet.model.shops

// Claude Code generated the documentation

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.FirestoreRepository
import com.github.meeplemeet.model.sessions.FirestoreGameRepository
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Firestore collection path for storing and retrieving shop documents. */
const val SHOP_COLLECTION_PATH = "shops"

/**
 * Repository for managing board game shops in Firestore.
 *
 * Handles CRUD operations for shop documents, including fetching owner accounts and game
 * collections from their respective repositories.
 *
 * @property db The Firestore database instance to use for operations.
 */
class ShopRepository(db: FirebaseFirestore = FirebaseProvider.db) {
  private val shops = db.collection(SHOP_COLLECTION_PATH)
  private val accountRepo = FirestoreRepository()
  private val gameRepo = FirestoreGameRepository()

  private fun newUUID() = shops.document().id

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
      gameCollection: List<Pair<Game, Int>> = emptyList()
  ): Shop {
    val shop =
        Shop(newUUID(), owner, name, phone, email, website, address, openingHours, gameCollection)
    shops.document(shop.id).set(toNoUid(shop)).await()
    return shop
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
    val snapshot = shops.document(id).get().await()
    if (!snapshot.exists()) throw IllegalArgumentException("Shop with the given ID does not exist")

    val shopNoUid =
        snapshot.toObject(ShopNoUid::class.java)
            ?: throw IllegalArgumentException("Failed to parse shop data")

    // Fetch the owner account
    val owner = accountRepo.getAccount(shopNoUid.ownerId)

    // Fetch all games in the game collection
    val gameCollection = coroutineScope {
      shopNoUid.gameCollection
          .map { (gameId, count) ->
            async {
              val game = gameRepo.getGameById(gameId)
              game to count
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
          gameCollection.map { (game, count) -> game.uid to count }
    }

    if (updates.isEmpty())
        throw IllegalArgumentException("At least one field must be provided for update")

    shops.document(id).update(updates).await()
  }

  /**
   * Deletes a shop from Firestore.
   *
   * @param id The unique identifier of the shop to delete.
   */
  suspend fun deleteShop(id: String) {
    shops.document(id).delete().await()
  }
}
