package com.github.meeplemeet.model.rental

import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/** Type of rentable resource in the application */
@Serializable
enum class RentalType {
  /** Renting a play space */
  SPACE,
  /** Renting a board game (WIP) */
  GAME
}

/** Status of a rental */
enum class RentalStatus {
  /** Rental confirmed and paid */
  CONFIRMED,
  /** Rental pending confirmation */
  PENDING,
  /** Rental cancelled */
  CANCELLED,
  /** Rental completed */
  COMPLETED
}

/**
 * Represents a rental of a resource with all its information.
 *
 * This class is designed to be generic so it can represent rentals of different resource types
 * (spaces, games, etc.). It links the renter, the resource, and optionally a session where the
 * resource will be used.
 *
 * @property uid Unique identifier of the rental (Firestore document ID).
 * @property renterId ID of the account renting the resource.
 * @property type Type of rented resource (SPACE, GAME...).
 * @property resourceId Identifier of the resource owner/provider:
 *     - For SPACE: the SpaceRenter's ID (the business offering spaces).
 *     - For GAME: the Shop's ID (the business offering games).
 *
 * @property resourceDetailId Identifier of the specific item being rented:
 *     - For SPACE: the index or ID of the specific Space inside the SpaceRenter.
 *     - For GAME: the Game's UID.
 *
 * @property startDate Start date and time of the rental.
 * @property endDate End date and time of the rental.
 * @property status Current status of the rental (PENDING, CONFIRMED, CANCELLED, COMPLETED).
 * @property totalCost Total cost of the rental, computed based on resource type and duration.
 * @property associatedSessionId ID of the session where the resource will be used (optional).
 * @property notes Free-text notes for additional context (e.g. "Need projector", "English
 *   version").
 * @property createdAt Timestamp when the rental was created.
 */
data class Rental(
    val uid: String,
    val renterId: String,
    val type: RentalType,
    val resourceId: String,
    val resourceDetailId: String,
    val startDate: Timestamp,
    val endDate: Timestamp,
    val status: RentalStatus = RentalStatus.PENDING,
    val totalCost: Double,
    val associatedSessionId: String? = null,
    val notes: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

/** Serializable version of a rental for Firestore */
@Serializable
data class RentalNoUid(
    val renterId: String = "",
    val type: RentalType = RentalType.SPACE,
    val resourceId: String = "",
    val resourceDetailId: String = "",
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp = Timestamp.now(),
    val status: RentalStatus = RentalStatus.PENDING,
    val totalCost: Double = 0.0,
    val associatedSessionId: String? = null,
    val notes: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

/** Simplified information about a rented resource, used for display */
data class RentalResourceInfo(
    val rental: Rental,
    val resourceName: String,
    val resourceAddress: Location,
    val detailInfo: String // Ex: "Space N°2 - 8 seats"
)

/** Converts a RentalNoUid into a Rental */
fun fromNoUid(id: String, rentalNoUid: RentalNoUid): Rental =
    Rental(
        uid = id,
        renterId = rentalNoUid.renterId,
        type = rentalNoUid.type,
        resourceId = rentalNoUid.resourceId,
        resourceDetailId = rentalNoUid.resourceDetailId,
        startDate = rentalNoUid.startDate,
        endDate = rentalNoUid.endDate,
        status = rentalNoUid.status,
        totalCost = rentalNoUid.totalCost,
        associatedSessionId = rentalNoUid.associatedSessionId,
        notes = rentalNoUid.notes,
        createdAt = rentalNoUid.createdAt)

/** Converts a Rental into a RentalNoUid */
fun toNoUid(rental: Rental): RentalNoUid =
    RentalNoUid(
        renterId = rental.renterId,
        type = rental.type,
        resourceId = rental.resourceId,
        resourceDetailId = rental.resourceDetailId,
        startDate = rental.startDate,
        endDate = rental.endDate,
        status = rental.status,
        totalCost = rental.totalCost,
        associatedSessionId = rental.associatedSessionId,
        notes = rental.notes,
        createdAt = rental.createdAt)
