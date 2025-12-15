package com.github.meeplemeet.model.rental

import com.github.meeplemeet.model.FirestoreRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing rentals in Firestore.
 *
 * This repository provides CRUD operations and utility methods to handle rentals of different
 * resource types.
 */
class RentalRepository : FirestoreRepository("rentals") {

  /**
   * Creates a new rental and stores it in Firestore.
   *
   * @param renterId ID of the account renting the resource.
   * @param type Type of resource being rented (SPACE, GAME).
   * @param resourceId ID of the resource provider:
   *     - For SPACE: the SpaceRenter's ID (business offering spaces).
   *     - For GAME: the Shop's ID (business offering games).
   *
   * @param resourceDetailId ID of the specific item being rented:
   *     - For SPACE: the index or ID of the specific Space inside the SpaceRenter.
   *     - For GAME: the Game's UID.
   *
   * @param startDate Desired start date/time of the rental.
   * @param endDate Desired end date/time of the rental.
   * @param totalCost Total cost of the rental.
   * @param notes Optional free-text notes (e.g. "Need projector", "English version").
   * @return The created Rental object.
   */
  suspend fun createRental(
      renterId: String,
      type: RentalType,
      resourceId: String,
      resourceDetailId: String,
      startDate: Timestamp,
      endDate: Timestamp,
      totalCost: Double,
      notes: String = ""
  ): Rental {
    val uid = newUUID()
    val rental =
        Rental(
            uid = uid,
            renterId = renterId,
            type = type,
            resourceId = resourceId,
            resourceDetailId = resourceDetailId,
            startDate = startDate,
            endDate = endDate,
            status = RentalStatus.CONFIRMED, // Status is directly confirmed for now
            totalCost = totalCost,
            notes = notes)

    collection.document(uid).set(toNoUid(rental)).await()
    return rental
  }

  /**
   * Retrieves a rental by its unique ID.
   *
   * @param rentalId Firestore document ID of the rental.
   * @return The Rental object if found, or null if not found.
   */
  suspend fun getRental(rentalId: String): Rental? {
    val snapshot = collection.document(rentalId).get().await()
    if (!snapshot.exists()) return null

    val rentalNoUid = snapshot.toObject(RentalNoUid::class.java) ?: return null
    return fromNoUid(snapshot.id, rentalNoUid)
  }

  /**
   * Retrieves all rentals for a given user.
   *
   * @param renterId ID of the user.
   * @param includeCompleted Whether to include completed rentals in the result.
   * @return List of rentals belonging to the user.
   */
  suspend fun getRentalsByUser(renterId: String, includeCompleted: Boolean = false): List<Rental> {
    var query: Query =
        collection
            .whereEqualTo(RentalNoUid::renterId.name, renterId)
            .orderBy(RentalNoUid::startDate.name, Query.Direction.DESCENDING)

    if (!includeCompleted) {
      query = query.whereNotEqualTo(RentalNoUid::status.name, RentalStatus.COMPLETED)
    }

    val snapshot = query.get().await()
    return snapshot.documents.mapNotNull { doc ->
      doc.toObject(RentalNoUid::class.java)?.let { fromNoUid(doc.id, it) }
    }
  }

  /**
   * Retrieves active rentals for a given user and resource type.
   *
   * Active rentals are defined as:
   * - Status = CONFIRMED
   * - End date > current time
   *
   * @param renterId ID of the user.
   * @param type Type of rental (SPACE, GAME, EQUIPMENT).
   * @return List of active rentals.
   */
  suspend fun getActiveRentalsByType(renterId: String, type: RentalType): List<Rental> {
    val now = Timestamp.now()
    val snapshot =
        collection
            .whereEqualTo(RentalNoUid::renterId.name, renterId)
            .whereEqualTo(RentalNoUid::type.name, type)
            .whereEqualTo(RentalNoUid::status.name, RentalStatus.CONFIRMED)
            .whereGreaterThan(RentalNoUid::endDate.name, now)
            .get()
            .await()

    return snapshot.documents.mapNotNull { doc ->
      doc.toObject(RentalNoUid::class.java)?.let { fromNoUid(doc.id, it) }
    }
  }

  /**
   * Updates the status of a rental.
   *
   * @param rentalId ID of the rental to update.
   * @param status New status (PENDING, CONFIRMED, CANCELLED, COMPLETED).
   */
  suspend fun updateRentalStatus(rentalId: String, status: RentalStatus) {
    collection.document(rentalId).update(RentalNoUid::status.name, status).await()
  }

  /**
   * Associates a rental with a session.
   *
   * @param rentalId ID of the rental.
   * @param sessionId ID of the session to associate.
   */
  suspend fun associateWithSession(rentalId: String, sessionId: String) {
    collection.document(rentalId).update(RentalNoUid::associatedSessionId.name, sessionId).await()
  }

  /**
   * Removes the association between a rental and a session.
   *
   * @param rentalId ID of the rental.
   */
  suspend fun dissociateFromSession(rentalId: String) {
    collection.document(rentalId).update(RentalNoUid::associatedSessionId.name, null).await()
  }

  /**
   * Cancels a rental by updating its status to CANCELLED.
   *
   * @param rentalId ID of the rental.
   */
  suspend fun cancelRental(rentalId: String) {
    updateRentalStatus(rentalId, RentalStatus.CANCELLED)
  }

  /**
   * Marks a rental as completed by updating its status to COMPLETED.
   *
   * @param rentalId ID of the rental.
   */
  suspend fun completeRental(rentalId: String) {
    updateRentalStatus(rentalId, RentalStatus.COMPLETED)
  }

  /**
   * Deletes a rental from Firestore.
   *
   * @param rentalId ID of the rental to delete.
   */
  suspend fun deleteRental(rentalId: String) {
    collection.document(rentalId).delete().await()
  }

  /**
   * Checks if a resource is available for a given time period.
   *
   * Availability is determined by verifying that no CONFIRMED rentals overlap with the requested
   * time window.
   *
   * @param resourceId ID of the resource provider (e.g. SpaceRenter ID).
   * @param resourceDetailId ID of the specific resource (e.g. space index, game ID).
   * @param startDate Desired start date/time.
   * @param endDate Desired end date/time.
   * @return true if available, false if there is a conflicting rental.
   */
  suspend fun isResourceAvailable(
      resourceId: String,
      resourceDetailId: String,
      startDate: Timestamp,
      endDate: Timestamp
  ): Boolean {
    val conflictingRentals =
        collection
            .whereEqualTo(RentalNoUid::resourceId.name, resourceId)
            .whereEqualTo(RentalNoUid::resourceDetailId.name, resourceDetailId)
            .whereEqualTo(RentalNoUid::status.name, RentalStatus.CONFIRMED)
            .whereGreaterThan(RentalNoUid::endDate.name, startDate)
            .get()
            .await()

    return conflictingRentals.documents.none { doc ->
      val rental = doc.toObject(RentalNoUid::class.java)
      rental != null && rental.startDate.toDate().time < endDate.toDate().time
    }
  }
}
