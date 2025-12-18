package com.github.meeplemeet.model.space_renter

// Claude Code generated the documentation

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.serialization.Serializable

/**
 * Represents a rentable space for board game sessions.
 *
 * @property seats The number of seats available in the space.
 * @property costPerHour The rental cost per hour for the space.
 */
@Serializable data class Space(val seats: Int = 0, val costPerHour: Double = 0.0)

/**
 * Represents a space rental business that provides areas for board game sessions.
 *
 * @property id The unique identifier for the space renter.
 * @property owner The account that owns this space rental business.
 * @property name The name of the space rental business.
 * @property phone The contact phone number for the space renter.
 * @property email The contact email address for the space renter.
 * @property website The space renter's website URL.
 * @property address The physical location of the space rental business.
 * @property openingHours The space renter's opening hours as a list of time pairs (start time, end
 *   time).
 * @property spaces The collection of rentable spaces available with their details.
 */
data class SpaceRenter(
    val id: String,
    val owner: Account,
    val name: String,
    val phone: String,
    val email: String,
    val website: String,
    val address: Location,
    val openingHours: List<OpeningHours>,
    val spaces: List<Space>,
    val photoCollectionUrl: List<String>
)

/**
 * Represents a space renter without a unique identifier, used for creating or updating space renter
 * data in Firebase. This version is serializable and uses primitive types for Firebase
 * compatibility.
 *
 * @property ownerId The unique identifier of the account that owns this space rental business.
 * @property name The name of the space rental business.
 * @property phone The contact phone number for the space renter.
 * @property email The contact email address for the space renter.
 * @property website The space renter's website URL.
 * @property address The physical location of the space rental business.
 * @property openingHours The space renter's opening hours as a list of time pairs (start time, end
 *   time).
 * @property spaces The collection of rentable spaces available with their details.
 */
@Serializable
data class SpaceRenterNoUid(
    val ownerId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val address: Location = Location(),
    val openingHours: List<OpeningHours> = emptyList(),
    val spaces: List<Space> = emptyList(),
    val photoCollectionUrl: List<String> = emptyList()
)

/**
 * Converts a SpaceRenter to a SpaceRenterNoUid for Firebase storage.
 *
 * @param spaceRenter The space renter to convert.
 * @return A SpaceRenterNoUid with the same data, using the owner's UID instead of the Account
 *   object.
 */
fun toNoUid(spaceRenter: SpaceRenter): SpaceRenterNoUid =
    SpaceRenterNoUid(
        spaceRenter.owner.uid,
        spaceRenter.name,
        spaceRenter.phone,
        spaceRenter.email,
        spaceRenter.website,
        spaceRenter.address,
        spaceRenter.openingHours,
        spaceRenter.spaces,
        spaceRenter.photoCollectionUrl)

/**
 * Converts a SpaceRenterNoUid to a SpaceRenter. Reconstructs the owner Account from the provided
 * parameter.
 *
 * @param id The unique identifier for the space renter.
 * @param spaceRenterNoUid The space renter data without ID.
 * @param owner The account that owns this space rental business.
 * @return A SpaceRenter with the provided ID and data.
 */
fun fromNoUid(id: String, spaceRenterNoUid: SpaceRenterNoUid, owner: Account): SpaceRenter =
    SpaceRenter(
        id,
        owner,
        spaceRenterNoUid.name,
        spaceRenterNoUid.phone,
        spaceRenterNoUid.email,
        spaceRenterNoUid.website,
        spaceRenterNoUid.address,
        spaceRenterNoUid.openingHours,
        spaceRenterNoUid.spaces,
        spaceRenterNoUid.photoCollectionUrl)
