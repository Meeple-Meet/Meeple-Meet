package com.github.meeplemeet.model.shops

// Claude Code generated the documentation

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.shared.location.Location
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing board game shops in Firestore.
 *
 * Handles CRUD operations for shop documents, including fetching owner accounts. Game collections
 * are now stored as GameItem (id + name + quantity) and not resolved to full Game objects.
 *
 * @property db The Firestore database instance to use for operations.
 */
class ShopRepository(
    val accountRepository: AccountRepository = RepositoryProvider.accounts,
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
      gameCollection: List<GameItem> = emptyList(),
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

    geoPinRepository.upsertBusinessGeoPin(
        ref = shop.id, type = PinType.SHOP, location = address, ownerId = owner.uid)

    accountRepository.addShopId(owner.uid, shop.id)

    return shop
  }

  /**
   * Retrieves a list of shops from Firestore.
   *
   * Fetches up to N shops, then retrieves owner accounts for each shop in parallel. Game
   * collections are returned as GameItem and not resolved to full Game objects.
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

              fromNoUid(doc.id, shopNoUid, owner)
            }
          }
          .awaitAll()
          .filterNotNull()
    }
  }

  /**
   * Retrieves a shop by its ID from Firestore.
   *
   * Fetches the shop document and the owner account. Game collections are returned as GameItem and
   * not resolved to full Game objects.
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

    return fromNoUid(id, shopNoUid, owner)
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
      gameCollection: List<GameItem>? = null,
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
    gameCollection?.let { updates[ShopNoUid::gameCollection.name] = gameCollection }
    photoCollectionUrl?.let { updates[ShopNoUid::photoCollectionUrl.name] = photoCollectionUrl }

    if (updates.isEmpty())
        throw IllegalArgumentException("At least one field must be provided for update")

    collection.document(id).update(updates).await()

    if (address != null) geoPinRepository.updateGeoPinLocation(id, address)
  }

  /**
   * Deletes a shop from Firestore.
   *
   * Also removes the shop ID from the owner's businesses subcollection.
   *
   * @param id The unique identifier of the shop to delete.
   */
  suspend fun deleteShop(id: String) {
    // Get the shop to retrieve the owner ID before deletion
    val shop = getShop(id)

    geoPinRepository.deleteGeoPin(id)
    collection.document(id).delete().await()

    // Remove the shop ID from the owner's businesses
    accountRepository.removeShopId(shop.owner.uid, id)
  }

  /**
   * Deletes multiple shops from Firestore efficiently in parallel.
   *
   * Also removes the shop IDs from the owners' businesses subcollections.
   *
   * @param ids The list of unique identifiers of the shops to delete.
   */
  suspend fun deleteShops(ids: List<String>, ownerId: String? = null) {
    coroutineScope {
      ids.map { id ->
            async {
              // Try to get the shop to retrieve the owner ID before deletion
              // If it fails (e.g. document doesn't exist), we fallback to the provided ownerId
              val shop =
                  try {
                    getShop(id)
                  } catch (e: Exception) {
                    null
                  }

              // Best effort deletion of geopins
              try {
                geoPinRepository.deleteGeoPin(id)
              } catch (_: Exception) {}

              // Best effort deletion of the document
              try {
                collection.document(id).delete().await()
              } catch (_: Exception) {}

              // Remove the shop ID from the owner's businesses
              // Use the owner from the document if available, otherwise use the provided fallback
              val uidToRemoveFrom = shop?.owner?.uid ?: ownerId
              if (uidToRemoveFrom != null) {
                try {
                  accountRepository.removeShopId(uidToRemoveFrom, id)
                } catch (_: Exception) {}
              }
            }
          }
          .awaitAll()
    }
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

    return fromNoUid(doc.id, shopNoUid, owner)
  }
  /**
   * Attempts to retrieve a shop by its ID from Firestore.
   *
   * Returns null if the shop does not exist or if an error occurs during retrieval.
   *
   * @param shopId The unique identifier of the shop to retrieve.
   * @return The Shop with the specified ID, or null if not found or on error.
   */
  suspend fun getShopSafe(shopId: String): Shop? {
    return try {
      getShop(shopId)
    } catch (_: Exception) {
      null
    }
  }
  /**
   * Updates a shop document from its offline changes.
   *
   * @param id The shop ID to update
   * @param changes The changes made offline
   */
  suspend fun updateShopOffline(id: String, changes: Map<String, Any>) {
    collection.document(id).update(changes).await()
  }
}
