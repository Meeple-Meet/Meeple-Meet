package com.github.meeplemeet.model.shared.game

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
   * @throws com.github.meeplemeet.model.GameNotFoundException if no game with the given ID exists.
   */
  suspend fun getGameById(gameID: String): Game

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
