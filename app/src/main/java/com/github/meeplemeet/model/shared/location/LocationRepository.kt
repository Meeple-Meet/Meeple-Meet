package com.github.meeplemeet.model.shared.location

/**
 * Interface for location-based search functionality.
 *
 * This repository abstracts the logic for searching geographic locations based on user input.
 *
 * @see Location
 */
interface LocationRepository {

  /**
   * Searches for geographic locations matching the given query string.
   *
   * This method should return a list of [Location] objects that represent possible matches for the
   * query. The results may include city names, addresses, landmarks, or other identifiable places.
   *
   * @param query The user-provided search string (e.g., "Lausanne", "EPFL").
   * @return A list of [Location] results matching the query, ordered by relevance.
   */
  suspend fun search(query: String): List<Location>
}
