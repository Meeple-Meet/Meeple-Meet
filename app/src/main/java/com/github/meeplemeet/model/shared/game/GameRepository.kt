package com.github.meeplemeet.model.shared.game

import com.github.meeplemeet.model.GameFetchException
import com.github.meeplemeet.model.GameSearchException

/**
 * Repository interface for accessing and querying [Game] data.
 *
 * Implementations of this interface are responsible for providing access to stored games, whether
 * they come from a local database, a remote API, or a combination of sources. The domain layer and
 * use cases should depend only on this interface, not on the concrete implementation.
 */
interface GameRepository {

  /**
   * Retrieves a [Game] by its unique identifier.
   *
   * @param gameID the unique ID of the game (e.g., Firestore document ID).
   * @return the corresponding [Game] object.
   * @throws GameFetchException if fetching the game with the given ID fails.
   */
  suspend fun getGameById(gameID: String): Game

  /**
   * Retrieves multiple [Game] objects by their unique identifiers.
   *
   * This method allows batch fetching of games by their IDs. The number of IDs provided must not
   * exceed 20. If more than 20 IDs are passed, implementations should throw an
   * [IllegalArgumentException]. If some of the provided IDs do not correspond to existing games,
   * they are simply omitted from the result list.
   *
   * @param gameIDs the unique IDs of the games to retrieve (max 20).
   * @return a [List] of [Game] objects corresponding to the provided IDs. The list may contain
   *   fewer items than requested if some IDs are invalid or not found.
   * @throws IllegalArgumentException if more than 20 IDs are provided.
   * @throws GameFetchException if fetching games with the given IDs fails.
   */
  suspend fun getGamesById(vararg gameIDs: String): List<Game>

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
   * @throws GameSearchException If the search operation fails.
   * @throws GameFetchException If loading the full game objects fails.
   */
  @Deprecated("Use searchGameByName for lightweight results instead")
  suspend fun searchGamesByNameContains(
      query: String,
      maxResults: Int = 10,
      ignoreCase: Boolean = true
  ): List<Game>

  /**
   * Searches for games by name and returns lightweight search results.
   *
   * Search is case-insensitive by default.
   *
   * @param query the substring of the game name to search for.
   * @param maxResults the maximum number of results to return (default: 10).
   * @return a [List] of [GameSearchResult] objects matching the query.
   * @throws GameSearchException If the search operation fails.
   */
  suspend fun searchGamesByName(query: String, maxResults: Int = 10): List<GameSearchResult>
}
