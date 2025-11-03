package com.github.meeplemeet.model.shops

// Claude Code generated the documentation

import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import kotlinx.serialization.Serializable

@Serializable data class OpeningHours(val day: Int = 0, val hours: List<Pair<String?, String?>>)

/**
 * Represents a board game shop or game cafe.
 *
 * @property id The unique identifier for the shop.
 * @property name The name of the shop.
 * @property phone The contact phone number for the shop.
 * @property email The contact email address for the shop.
 * @property website The shop's website URL.
 * @property address The physical location of the shop.
 * @property openingHours The shop's opening hours as a list of time pairs (start time, end time).
 * @property gameCollection The collection of games available at the shop with their quantities.
 *   Each pair contains a Game and its count.
 */
data class Shop(
    val id: String,
    val owner: Account,
    val name: String,
    val phone: String,
    val email: String,
    val website: String,
    val address: Location,
    val openingHours: List<OpeningHours>,
    val gameCollection: List<Pair<Game, Int>>
)

/**
 * Represents a shop without a unique identifier, used for creating or updating shop data in
 * Firebase. This version is serializable and uses primitive types for Firebase compatibility.
 *
 * @property name The name of the shop.
 * @property phone The contact phone number for the shop.
 * @property email The contact email address for the shop.
 * @property website The shop's website URL.
 * @property address The physical location of the shop.
 * @property openingHours The shop's opening hours as a list of time pairs (start time, end time).
 * @property gameCollection The collection of games available at the shop with their quantities.
 *   Each pair contains a game UID and its count.
 */
@Serializable
data class ShopNoUid(
    val ownerId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val address: Location = Location(),
    val openingHours: List<OpeningHours> = emptyList(),
    val gameCollection: List<Pair<String, Int>> = emptyList()
)

/**
 * Converts a Shop to a ShopNoUid for Firebase storage. Transforms the game collection from Game
 * objects to game UIDs.
 *
 * @param shop The shop to convert.
 * @return A ShopNoUid with the same data, using game UIDs instead of Game objects.
 */
fun toNoUid(shop: Shop): ShopNoUid =
    ShopNoUid(
        shop.owner.uid,
        shop.name,
        shop.phone,
        shop.email,
        shop.website,
        shop.address,
        shop.openingHours,
        shop.gameCollection.map { (game, count) -> game.uid to count })

/**
 * Converts a ShopNoUid and game collection to a Shop. Reconstructs Game objects from the provided
 * collection.
 *
 * @param id The unique identifier for the shop.
 * @param shopNoUid The shop data without ID.
 * @param collection The collection of games with their quantities as Game objects.
 * @return A Shop with the provided ID and data.
 */
fun fromNoUid(
    id: String,
    shopNoUid: ShopNoUid,
    owner: Account,
    collection: List<Pair<Game, Int>>
): Shop =
    Shop(
        id,
        owner,
        shopNoUid.name,
        shopNoUid.phone,
        shopNoUid.email,
        shopNoUid.website,
        shopNoUid.address,
        shopNoUid.openingHours,
        collection)
