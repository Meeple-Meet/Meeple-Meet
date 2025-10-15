package com.github.meeplemeet.model.repositories

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
   * Retrieves a subset of games from the repository, without any filtering criteria.
   *
   * This method returns up to [maxResults] games, making it suitable for UI lists, previews, or any
   * situation where fetching all games is unnecessary.
   *
   * @param maxResults the maximum number of games to return (default: 10).
   * @return a [List] of [Game] objects, containing at most [maxResults] items.
   */
  suspend fun getGames(maxResults: Int = 10): List<Game>

  /**
   * Retrieves a [Game] by its unique identifier.
   *
   * @param gameID the unique ID of the game (e.g., Firestore document ID).
   * @return the corresponding [Game] object.
   * @throws GameNotFoundException if no game with the given ID exists.
   */
  suspend fun getGameById(gameID: String): Game

  /**
   * Retrieves games that include a specific genre ID.
   *
   * Returns up to [maxResults] items.
   *
   * @param genreID the ID of the genre to filter by (e.g., corresponding to an internal enum or
   *   tag).
   * @param maxResults the maximum number of results to return (default: 10).
   * @return a [List] of [Game] objects that include the specified genre.
   */
  suspend fun getGamesByGenre(genreID: Int, maxResults: Int = 10): List<Game>

  /**
   * Retrieves games that include **all** of the specified genre IDs.
   *
   * This method performs an **exclusive match**: only games that have every genre ID in [genreIDs]
   * will be returned.
   *
   * For example, if [genreIDs] = `[1, 2, 3]`, only games that contain all three genre IDs 1, 2, and
   * 3 will be included in the result. Games that have only a subset of these genres will not be
   * returned.
   *
   * Returns up to [maxResults] items.
   *
   * @param genreIDs a [List] of genre IDs to filter by.
   * @param maxResults the maximum number of results to return (default: 10).
   * @return a [List] of [Game] objects that match all specified genres.
   */
  suspend fun getGamesByGenres(genreIDs: List<Int>, maxResults: Int = 10): List<Game>

  /**
   * Searches for games whose names contain the specified [query].
   *
   * This method performs a substring search on the game names, allowing the caller to choose
   * whether the comparison should ignore case sensitivity.
   *
   * For example, searching for `"cat"` with [ignoreCase] set to `true` will match `"Catan"`, `"Cat
   * Lady"`, and `"Concatenate"`. When [ignoreCase] is `false`,only exact casing matches are
   * returned.
   *
   * Returns up to [maxResults] results, making it suitable for live search or autocomplete.
   *
   * @param query the substring of the game name to search for.
   * @param maxResults the maximum number of results to return (default: 5).
   * @param ignoreCase whether to ignore case when matching names (default: true).
   * @return a [List] of [Game] objects whose names contain the specified substring.
   */
  suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int = 10,
      ignoreCase: Boolean = true
  ): List<Game>
}
