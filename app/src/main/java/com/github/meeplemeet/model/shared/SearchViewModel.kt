package com.github.meeplemeet.model.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.map.LocationRepository
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.sessions.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the session game picker.
 *
 * @property gameQuery Current text in the game search text field (what the user typed).
 * @property gameSuggestions List of candidate [Game] objects returned by the repository for the
 *   current query.
 * @property selectedGameUid If a game has been selected, holds its UID (Firestore document id).
 *   Empty string when nothing is selected.
 * @property gameSearchError If a search error occurred, holds the error message. Null otherwise.
 * @property fetchedGame The last fetched [Game] by ID, or null if none fetched or an error
 *   occurred.
 * @property gameFetchError If an error occurred during fetching a game by ID, holds the error
 *   message.
 */
data class GameUIState(
    val gameQuery: String = "",
    val gameSuggestions: List<Game> = emptyList(),
    val selectedGameUid: String = "",
    val gameSearchError: String? = null,
    val fetchedGame: Game? = null,
    val gameFetchError: String? = null,
    val isSearching: Boolean = false
)

/**
 * UI state for location search and selection.
 *
 * @property locationQuery Current text in the location search text field (what the user typed).
 * @property locationSuggestions List of candidate [Location] objects return by the repository for
 *   the current query.
 * @property selectedLocation If a location has been selected, holds its value. Null otherwise.
 * @property locationSearchError If a search error occurred, holds the error message. Null
 *   otherwise.
 */
data class LocationUIState(
    val locationQuery: String = "",
    val locationSuggestions: List<Location> = emptyList(),
    val selectedLocation: Location? = null,
    val locationSearchError: String? = null
)

/**
 * ViewModel that provides shared search logic for both games and locations.
 *
 * This ViewModel centralizes the logic for:
 * - Game search and selection via [GameRepository]
 * - Location search and selection via [LocationRepository]
 *
 * It exposes two independent UI states:
 * - [gameUIState] for managing game-related search and selection
 * - [locationUIState] for managing location-related search and selection
 */
open class SearchViewModel(
    private val gameRepository: GameRepository = RepositoryProvider.games,
    private val locationRepository: LocationRepository = RepositoryProvider.locations
) : ViewModel() {

  // ---------- Game Search ----------
  private val _gameUIState = MutableStateFlow(GameUIState())
  val gameUIState: StateFlow<GameUIState> = _gameUIState.asStateFlow()

  /** Resets the game search state to its default values. */
  fun clearGameSearch() {
    _gameUIState.value = GameUIState()
  }

  /**
   * Sets the selected game for the session.
   *
   * Updates the game UI state with the selected game's UID and name (requires admin privileges).
   *
   * Note: This method does **not** update the session itself with the selected game. It only
   * updates the [GameUIState] to reflect the user's current selection in the UI.
   *
   * @param game The [Game] object to select
   */
  fun setGame(game: Game) {
    _gameUIState.value = _gameUIState.value.copy(selectedGameUid = game.uid, gameQuery = game.name)
  }

  /**
   * Updates the game search query and asynchronously fetches suggestions (requires admin
   * privileges).
   *
   * The UI should call `setGameQuery` whenever the user types into the game search field. This
   * method:
   * - updates the visible `gameQuery` in [GameUIState],
   * - triggers a background search on the injected [GameRepository],
   * - updates `gameSuggestions` with the results (or empties the list on error or blank query),
   * - shows any search-related error message in `gameSearchError`.
   *
   * Note: If `gameSearchError` is set, it means the search operation itself failed (e.g. network or
   * repository error), **not** that no matching game was found. For example, if the user searches
   * for "Catan" and it exists in the database, but the error message appears, it indicates a
   * failure in the search mechanism, not an absence of results.
   *
   * @param query The substring to search for in game names.
   */
  fun setGameQuery(query: String) {
    _gameUIState.value = _gameUIState.value.copy(gameQuery = query)

    if (query.isNotBlank()) {
      viewModelScope.launch {
        try {
          val results = gameRepository.searchGamesByNameContains(query)
          _gameUIState.value = _gameUIState.value.copy(gameSuggestions = results)
        } catch (_: Exception) {
          _gameUIState.value =
              _gameUIState.value.copy(
                  gameSuggestions = emptyList(),
                  gameSearchError = "Game search failed due to a repository error")
        }
      }
    } else {
      _gameUIState.value = _gameUIState.value.copy(gameSuggestions = emptyList())
    }
  }

  /**
   * Fetches a [Game] by its Firestore document ID and updates the UI state accordingly.
   *
   * This method allows the UI layer to retrieve the full [Game] details for a given `gameId`
   * reference stored in a session.
   *
   * The result (if successful) is reflected in [gameUIState]:
   * - `fetchedGame` is set to the retrieved [Game].
   * - In case of an error, `gameFetchError` is set.
   *
   * @param gameId The Firestore document ID of the game to retrieve.
   */
  fun getGameFromId(gameId: String) {
    viewModelScope.launch {
      try {
        val game = gameRepository.getGameById(gameId)
        _gameUIState.value =
            _gameUIState.value.copy(
                fetchedGame = game, gameFetchError = null, gameQuery = game.name)
      } catch (_: Exception) {
        _gameUIState.value =
            _gameUIState.value.copy(
                fetchedGame = null, gameFetchError = "Failed to fetch game details")
      }
    }
  }

  // ---------- Location Search ----------
  private val _locationUIState = MutableStateFlow(LocationUIState())
  val locationUIState: StateFlow<LocationUIState> = _locationUIState.asStateFlow()

  /** Resets the location search state to its default values. */
  fun clearLocationSearch() {
    _locationUIState.value = LocationUIState()
  }

  /**
   * Sets the selected location and updates the query field with its name.
   *
   * @param location The [Location] object to select.
   */
  fun setLocation(location: Location) {
    _locationUIState.value =
        _locationUIState.value.copy(selectedLocation = location, locationQuery = location.name)
  }

  /**
   * Updates the location search query and asynchronously fetches suggestions.
   *
   * This method:
   * - Updates the visible `locationQuery` in [LocationUIState]
   * - Triggers a background search on the injected [LocationRepository]
   * - Updates `locationSuggestions` with the results (or empties the list on error or blank query)
   * - Shows any search-related error message in `locationSearchError`
   *
   * @param query The substring to search for in location names.
   */
  fun setLocationQuery(query: String) {
    _locationUIState.value = _locationUIState.value.copy(locationQuery = query)

    if (query.isNotBlank()) {
      viewModelScope.launch {
        try {
          val results = locationRepository.search(query)
          _locationUIState.value = _locationUIState.value.copy(locationSuggestions = results)
        } catch (_: Exception) {
          _locationUIState.value =
              _locationUIState.value.copy(
                  locationSuggestions = emptyList(),
                  locationSearchError = "Location search failed due to a repository error")
        }
      }
    } else {
      _locationUIState.value = _locationUIState.value.copy(locationSuggestions = emptyList())
    }
  }
}
