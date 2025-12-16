// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.shared.SearchViewModel
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.game.GameSearchResult
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shared.location.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PERMISSION_DENIED_MESSAGE = "Only the shop's owner can edit his own shop"

/**
 * Base ViewModel for shop-related screens that need game selection functionality.
 *
 * Extends [SearchViewModel] to provide game/location search and selection with shop owner
 * permission validation. This ViewModel ensures that only the shop owner can modify
 * game/location-related data.
 */
open class ShopSearchViewModel(
    private val gameRepository: GameRepository = RepositoryProvider.games,
    locationRepository: LocationRepository = RepositoryProvider.locations
) : SearchViewModel(gameRepository, locationRepository) {

  // Map of game IDs to fetched Game objects
  private val _fetchedGames = MutableStateFlow<Map<String, Game>>(emptyMap())
  val fetchedGames: StateFlow<Map<String, Game>> = _fetchedGames.asStateFlow()

  /**
   * Fetches games by their IDs in the background and updates the fetchedGames state.
   *
   * @param gameIds List of game IDs to fetch (max 20 per call due to Firestore limitations).
   * @throws IllegalArgumentException If more than 20 IDs are requested.
   */
  fun fetchGames(gameIds: List<String>) {
    require(gameIds.size < 20)
    if (gameIds.isEmpty()) return

    // Filter out already fetched games
    val toFetch = gameIds.filterNot { _fetchedGames.value.containsKey(it) }
    if (toFetch.isEmpty()) return

    viewModelScope.launch {
      try {
        val games = gameRepository.getGamesById(*toFetch.toTypedArray())
        val newMap = _fetchedGames.value.toMutableMap()
        games.forEach { game -> newMap[game.uid] = game }
        _fetchedGames.value = newMap
      } catch (_: Exception) {
        // Silent failure - games will show placeholder/name only
      }
    }
  }

  /** Clears all fetched games from cache. */
  fun clearFetchedGames() {
    _fetchedGames.value = emptyMap()
  }

  /**
   * Sets the selected game for a shop with permission validation.
   *
   * Only the shop owner can select games for their shop. Updates the game UI state with the
   * selected game's UID and name.
   *
   * @param shop The shop to add the game to.
   * @param requester The account requesting the game selection.
   * @param searchResult The game to select.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun setGame(shop: Shop, requester: Account, searchResult: GameSearchResult) {
    if (shop.owner.uid != requester.uid) throw PermissionDeniedException(PERMISSION_DENIED_MESSAGE)

    setGame(searchResult)
  }

  /**
   * Updates the game search query for a shop with permission validation.
   *
   * Only the shop owner can search for games to add to their shop. This method updates the visible
   * game query in the UI state and triggers a background search for matching games.
   *
   * @param shop The shop context for the game search.
   * @param requester The account requesting the game search.
   * @param query The search query string to find games by name.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun setGameQuery(shop: Shop, requester: Account, query: String) {
    if (shop.owner.uid != requester.uid) throw PermissionDeniedException(PERMISSION_DENIED_MESSAGE)

    setGameQuery(query)
  }

  /**
   * Sets the selected location for a shop with permission validation.
   *
   * Only the shop owner can select location for their shop. Updates the location UI state with the
   * selected location.
   *
   * @param shop The shop to modify.
   * @param requester The account requesting the change.
   * @param location The location to select.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun setLocation(shop: Shop, requester: Account, location: Location) {
    if (shop.owner.uid != requester.uid) throw PermissionDeniedException(PERMISSION_DENIED_MESSAGE)

    setLocation(location)
  }

  /**
   * Updates the location search query for a shop with permission validation.
   *
   * Only the shop owner can search for location to add to their shop. This method updates the
   * visible location query in the UI state and triggers a background search for matching location.
   *
   * @param shop The shop context.
   * @param requester The account requesting the search.
   * @param query The search query string.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun setLocationQuery(shop: Shop, requester: Account, query: String) {
    if (shop.owner.uid != requester.uid) throw PermissionDeniedException(PERMISSION_DENIED_MESSAGE)

    setLocationQuery(query)
  }
}
