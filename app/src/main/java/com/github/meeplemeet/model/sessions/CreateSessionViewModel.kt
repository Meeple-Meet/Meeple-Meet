package com.github.meeplemeet.model.sessions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountViewModel
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.shared.SearchViewModel
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ERROR_ADMIN_PERMISSION = "Only discussion admins can perform this operation"

open class CreateSessionViewModel(
    private val sessionRepository: SessionRepository = RepositoryProvider.sessions,
    gameRepository: GameRepository = RepositoryProvider.games
) : SearchViewModel(gameRepository), AccountViewModel {
  override val scope: CoroutineScope
    get() = this.viewModelScope

  /**
   * Checks if an account has admin privileges for a discussion.
   *
   * Currently checks if the requester is a participant in the discussion.
   */
  private fun isAdmin(requester: Account, discussion: Discussion): Boolean {
    return discussion.admins.contains(requester.uid)
  }

  /**
   * Creates a new gaming session within the discussion.
   *
   * Requires admin privileges (requester must be a discussion participant). Updates the discussion
   * state flow upon successful creation.
   *
   * @param requester The account requesting to create the session
   * @param discussion The discussion to add the session to
   * @param name The name of the session
   * @param gameId The ID of the game to be played
   * @param date The scheduled date and time of the session
   * @param location Where the session will take place
   * @param participants Participant IDs for the session
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun createSession(
      requester: Account,
      discussion: Discussion,
      name: String,
      gameId: String,
      date: Timestamp,
      location: Location,
      vararg participants: Account
  ) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    val participantsList = participants.toList().map { it.uid }
    if (participantsList.isEmpty()) throw IllegalArgumentException("No Participants")

    viewModelScope.launch {
      sessionRepository.updateSession(
          discussion.uid, name, gameId, date, location, participantsList)
    }
  }

  /**
   * Sets the selected location for the session (requires admin privileges).
   *
   * Updates the location UI state with the selected location object. This method does **not**
   * update the session itself with the selected location.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param location The [Location] object to select
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun setLocation(requester: Account, discussion: Discussion, location: Location) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    setLocation(location)
  }

  /**
   * Updates the location search query and asynchronously fetches suggestions (requires admin
   * privileges).
   *
   * The UI should call `setLocationQuery` whenever the user types into the location search field.
   * This method:
   * - updates the visible `locationQuery` in [com.github.meeplemeet.model.shared.LocationUIState],
   * - triggers a background search on the injected
   *   [com.github.meeplemeet.model.shared.location.LocationRepository],
   * - updates `locationSuggestions` with the results (or empties the list on error or blank query),
   * - shows any search-related error message in `locationSearchError`.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param query The substring to search for in location names.
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun setLocationQuery(requester: Account, discussion: Discussion, query: String) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    setLocationQuery(query)
  }

  /**
   * Sets the selected game for the session.
   *
   * Updates the game UI state with the selected game's UID and name (requires admin privileges).
   *
   * Note: This method does **not** update the session itself with the selected game. It only
   * updates the [com.github.meeplemeet.model.shared.GameUIState] to reflect the user's current
   * selection in the UI.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param game The [Game] object to select
   */
  fun setGame(requester: Account, discussion: Discussion, game: Game) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)
    setGame(game)
  }

  /**
   * Updates the game search query and asynchronously fetches suggestions (requires admin
   * privileges).
   *
   * The UI should call `setGameQuery` whenever the user types into the game search field. This
   * method:
   * - updates the visible `gameQuery` in [com.github.meeplemeet.model.shared.GameUIState],
   * - triggers a background search on the injected [GameRepository],
   * - updates `gameSuggestions` with the results (or empties the list on error or blank query),
   * - shows any search-related error message in `gameSearchError`.
   *
   * Note: If `gameSearchError` is set, it means the search operation itself failed (e.g. network or
   * repository error), **not** that no matching game was found. For example, if the user searches
   * for "Catan" and it exists in the database, but the error message appears, it indicates a
   * failure in the search mechanism, not an absence of results.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param query The substring to search for in game names.
   */
  fun setGameQuery(requester: Account, discussion: Discussion, query: String) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    setGameQuery(query)
  }
}
