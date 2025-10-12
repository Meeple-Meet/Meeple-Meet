package com.github.meeplemeet.model.systems

import com.github.meeplemeet.model.GameNotFoundException
import com.github.meeplemeet.model.structures.Game

/**
 * Repository interface for accessing and querying [Game] data.
 *
 * Implementations of this interface are responsible for providing access to stored games, whether
 * they come from a local database, a remote API, or a combination of sources. The domain layer and
 * use cases should depend only on this interface, not on the concrete implementation.
 */
interface GameRepository {

  /**
   * Retrieves all games available in the repository.
   *
   * @return a [List] of all [Game] objects.
   */
  suspend fun getAllGames(): List<Game>

  /**
   * Retrieves a [Game] by its unique identifier.
   *
   * @param gameID the unique ID of the game (e.g., Firestore document ID).
   * @return the corresponding [Game] object.
   * @throws GameNotFoundException if no game with the given ID exists.
   */
  suspend fun getGameById(gameID: String): Game

  /**
   * Retrieves a [Game] by its name.
   *
   * @param name the exact name of the game to search for.
   * @return the matching [Game] object, or null if no game with the given name exists.
   */
  suspend fun getGameByName(name: String): Game?

  /**
   * Retrieves all games that are associated with a specific genre.
   *
   * @param genreID the ID of the genre to filter by (e.g., corresponding to an internal enum or
   *   tag).
   * @return a [List] of [Game] objects that include the specified genre.
   */
  suspend fun getGamesByGenre(genreID: Int): List<Game>

  /**
   * Retrieves all games that are associated with **all** of the specified genres.
   *
   * This method performs an **exclusive match**: only games that have every genre ID in [genreIDs]
   * will be returned.
   *
   * For example, if [genreIDs] = `[1, 2, 3]`, only games that contain all three genre IDs 1, 2, and
   * 3 will be included in the result. Games that have only a subset of these genres will not be
   * returned.
   *
   * @param genreIDs a [List] of genre IDs to filter by.
   * @return a [List] of [Game] objects that match all specified genres.
   */
  suspend fun getGamesByGenres(genreIDs: List<Int>): List<Game>
}
