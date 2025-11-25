package com.github.meeplemeet.model.space_renter

// Claude Code generated the documentation

import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.FirestoreRepository
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing space rental businesses in Firestore.
 *
 * Handles CRUD operations for space renter documents, including fetching owner accounts from their
 * respective repositories.
 *
 * @property db The Firestore database instance to use for operations.
 */
class SpaceRenterRepository(
    val accountRepository: AccountRepository = RepositoryProvider.accounts,
    val geoPinRepository: StorableGeoPinRepository = RepositoryProvider.geoPins
) : FirestoreRepository("space_renters") {
  /**
   * Creates a new space renter and stores it in Firestore.
   *
   * @param owner The account that owns the space rental business.
   * @param name The name of the space rental business.
   * @param phone The contact phone number for the space renter (optional).
   * @param email The contact email address for the space renter (optional).
   * @param website The space renter's website URL (optional).
   * @param address The physical location of the space rental business.
   * @param openingHours The space renter's opening hours as a list of time pairs (start time, end
   *   time).
   * @param spaces The collection of rentable spaces with their details (optional, defaults to
   *   empty).
   * @param photoCollectionUrl The collection of photo URLs for the space renter (optional, defaults
   *   to empty).
   * @return The created SpaceRenter with a generated unique ID.
   */
  suspend fun createSpaceRenter(
      owner: Account,
      name: String,
      phone: String = "",
      email: String = "",
      website: String = "",
      address: Location,
      openingHours: List<OpeningHours>,
      spaces: List<Space> = emptyList(),
      photoCollectionUrl: List<String> = emptyList()
  ): SpaceRenter {
    val spaceRenter =
        SpaceRenter(
            newUUID(),
            owner,
            name,
            phone,
            email,
            website,
            address,
            openingHours,
            spaces,
            photoCollectionUrl)
    collection.document(spaceRenter.id).set(toNoUid(spaceRenter)).await()

    geoPinRepository.upsertGeoPin(ref = spaceRenter.id, type = PinType.SPACE, location = address)

    accountRepository.addSpaceRenterId(owner.uid, spaceRenter.id)

    return spaceRenter
  }

  /**
   * Retrieves a list of space renters from Firestore.
   *
   * Fetches up to N space renters, then retrieves owner accounts for each space renter in parallel
   * for optimal performance.
   *
   * @param maxResults The maximum number of space renters to retrieve.
   * @return A list of SpaceRenter objects, up to maxResults in size.
   */
  suspend fun getSpaceRenters(maxResults: UInt): List<SpaceRenter> {
    val snapshot = collection.limit(maxResults.toLong()).get().await()

    return coroutineScope {
      snapshot.documents
          .map { doc ->
            async {
              val spaceRenterNoUid = doc.toObject(SpaceRenterNoUid::class.java) ?: return@async null

              // Fetch the owner account
              val owner = accountRepository.getAccount(spaceRenterNoUid.ownerId)

              fromNoUid(doc.id, spaceRenterNoUid, owner)
            }
          }
          .awaitAll()
          .filterNotNull()
    }
  }

  /**
   * Retrieves a space renter by its ID from Firestore.
   *
   * Fetches the space renter document, then retrieves the owner account.
   *
   * @param id The unique identifier of the space renter to retrieve.
   * @return The SpaceRenter with the specified ID.
   * @throws IllegalArgumentException if the space renter does not exist or if the data cannot be
   *   parsed.
   */
  suspend fun getSpaceRenter(id: String): SpaceRenter {
    val snapshot = collection.document(id).get().await()
    if (!snapshot.exists())
        throw IllegalArgumentException("SpaceRenter with the given ID does not exist")

    val spaceRenterNoUid =
        snapshot.toObject(SpaceRenterNoUid::class.java)
            ?: throw IllegalArgumentException("Failed to parse space renter data")

    // Fetch the owner account
    val owner = accountRepository.getAccount(spaceRenterNoUid.ownerId)

    return fromNoUid(id, spaceRenterNoUid, owner)
  }

  /**
   * Updates one or more fields of a space renter in Firestore.
   *
   * Only the provided (non-null) fields are updated. At least one field must be provided.
   *
   * @param id The unique identifier of the space renter to update.
   * @param ownerId The new owner ID (optional).
   * @param name The new name of the space rental business (optional).
   * @param phone The new contact phone number (optional).
   * @param email The new contact email address (optional).
   * @param website The new website URL (optional).
   * @param address The new physical location (optional).
   * @param openingHours The new opening hours (optional).
   * @param spaces The new collection of rentable spaces (optional).
   * @throws IllegalArgumentException if no fields are provided for update.
   */
  suspend fun updateSpaceRenter(
      id: String,
      ownerId: String? = null,
      name: String? = null,
      phone: String? = null,
      email: String? = null,
      website: String? = null,
      address: Location? = null,
      openingHours: List<OpeningHours>? = null,
      spaces: List<Space>? = null,
      photoCollectionUrl: List<String>? = null
  ) {
    val updates = mutableMapOf<String, Any>()

    ownerId?.let { updates[SpaceRenterNoUid::ownerId.name] = ownerId }
    name?.let { updates[SpaceRenterNoUid::name.name] = name }
    phone?.let { updates[SpaceRenterNoUid::phone.name] = phone }
    email?.let { updates[SpaceRenterNoUid::email.name] = email }
    website?.let { updates[SpaceRenterNoUid::website.name] = website }
    address?.let { updates[SpaceRenterNoUid::address.name] = address }
    openingHours?.let { updates[SpaceRenterNoUid::openingHours.name] = openingHours }
    spaces?.let { updates[SpaceRenterNoUid::spaces.name] = spaces }
    photoCollectionUrl?.let {
      updates[SpaceRenterNoUid::photoCollectionUrl.name] = photoCollectionUrl
    }

    if (updates.isEmpty())
        throw IllegalArgumentException("At least one field must be provided for update")

    collection.document(id).update(updates).await()

    if (address != null) geoPinRepository.upsertGeoPin(id, PinType.SPACE, address)
  }

  /**
   * Deletes a space renter from Firestore.
   *
   * Also removes the space renter ID from the owner's businesses subcollection.
   *
   * @param id The unique identifier of the space renter to delete.
   */
  suspend fun deleteSpaceRenter(id: String) {
    // Get the space renter to retrieve the owner ID before deletion
    val spaceRenter = getSpaceRenter(id)

    geoPinRepository.deleteGeoPin(id)
    collection.document(id).delete().await()

    // Remove the space renter ID from the owner's businesses
    accountRepository.removeSpaceRenterId(spaceRenter.owner.uid, id)
  }

  /**
   * Deletes multiple space renters from Firestore efficiently in parallel.
   *
   * Also removes the space renter IDs from the owners' businesses subcollections.
   *
   * @param ids The list of unique identifiers of the space renters to delete.
   */
  suspend fun deleteSpaceRenters(ids: List<String>) {
    coroutineScope {
      ids.map { id ->
            async {
              // Get the space renter to retrieve the owner ID before deletion
              val spaceRenter = getSpaceRenter(id)

              geoPinRepository.deleteGeoPin(id)
              collection.document(id).delete().await()

              // Remove the space renter ID from the owner's businesses
              accountRepository.removeSpaceRenterId(spaceRenter.owner.uid, id)
            }
          }
          .awaitAll()
    }
  }
}
