package com.github.meeplemeet.model.shared.game

import kotlinx.serialization.Serializable

/**
 * Represents a game stored in Firestore.
 *
 * @property uid Globally unique identifier of the game (Firestore document ID).
 * @property name Name of the game.
 * @property description Description of the game.
 * @property imageURL URL of the game's image.
 * @property minPlayers Minimum number of players required to play the game.
 * @property maxPlayers Maximum number of players that can play the game.
 * @property recommendedPlayers Recommended number of players for optimal gameplay (optional).
 * @property averagePlayTime Average playtime of the game in minutes (optional).
 * @property genres List of genre IDs associated with the game (optional).
 */
data class Game(
    val uid: String,
    val name: String,
    val description: String,
    val imageURL: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val recommendedPlayers: Int?,
    val averagePlayTime: Int?,
    val genres: List<Int> = emptyList()
)

/**
 * Minimal serializable form of [Game] without the UID, used for Firestore storage.
 *
 * Firestore stores the UID as the document ID, so it is omitted from the stored object.
 */
@Serializable
data class GameNoUid(
    val name: String = "",
    val description: String = "",
    val imageURL: String = "",
    val minPlayers: Int = 0,
    val maxPlayers: Int = 0,
    val recommendedPlayers: Int? = null,
    val averagePlayTime: Int? = null,
    val genres: List<Int> = emptyList()
)

/**
 * Reconstructs a full [Game] object from its Firestore representation.
 *
 * @param id The Firestore document ID (used as game UID).
 * @param gameNoUid The deserialized [GameNoUid] data from Firestore.
 * @return A fully constructed [Game] instance.
 */
fun fromNoUid(id: String, gameNoUid: GameNoUid): Game =
    Game(
        id,
        gameNoUid.name,
        gameNoUid.description,
        gameNoUid.imageURL,
        gameNoUid.minPlayers,
        gameNoUid.maxPlayers,
        gameNoUid.recommendedPlayers,
        gameNoUid.averagePlayTime,
        gameNoUid.genres)
