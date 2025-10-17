package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.repositories.FirestoreGameRepository
import com.github.meeplemeet.model.repositories.FirestoreSessionRepository
import com.github.meeplemeet.model.repositories.GameRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Game
import com.github.meeplemeet.model.structures.Location
import com.google.firebase.Timestamp
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
    val gameFetchError: String? = null
)

/**
 * ViewModel for managing gaming sessions within a discussion.
 *
 * Provides permission-controlled access to session operations (create, update, delete) and
 * maintains the current discussion state with reactive updates.
 *
 * @property initDiscussion The initial discussion state
 * @property repository Repository for session data operations
 */
class FirestoreSessionViewModel(
    initDiscussion: Discussion,
    private val repository: FirestoreSessionRepository = FirestoreSessionRepository(),
    private val gameRepository: GameRepository = FirestoreGameRepository()
) : ViewModel() {
  /** Observable discussion state that updates when session operations complete. */
  private val _discussion = MutableStateFlow(initDiscussion)
  val discussion: StateFlow<Discussion> = _discussion

  /** UI state for game selection within the session. */
  private val _gameUIState = MutableStateFlow(GameUIState())
  val gameUIState: StateFlow<GameUIState> = _gameUIState.asStateFlow()

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
      minParticipants: Int = 1,
      maxParticipants: Int = 10,
      vararg participants: Account
  ) {
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    if (minParticipants > maxParticipants)
        throw IllegalArgumentException(
            "The minimum number of participants can not be more than the maximum number of participants")

    val participantsList = participants.toList().map { it -> it.uid }
    if (participantsList.isEmpty()) throw IllegalArgumentException("No Participants")
    if (participantsList.size < minParticipants)
        throw IllegalArgumentException("To little participants")
    if (participantsList.size > maxParticipants)
        throw IllegalArgumentException("To many participants")

    viewModelScope.launch {
      _discussion.value =
          repository.updateSession(
              discussion.uid,
              name,
              gameId,
              date,
              location,
              minParticipants,
              maxParticipants,
              participantsList)
    }
  }

  /**
   * Updates one or more fields of an existing session.
   *
   * Requires admin privileges (requester must be a discussion participant). Only provided
   * (non-null) fields will be updated. Updates the discussion state flow upon successful
   * modification.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param name Optional new session name
   * @param gameId Optional new game ID
   * @param date Optional new scheduled date and time
   * @param location Optional new location
   * @param newParticipantList Optional new participant list
   * @throws PermissionDeniedException if requester is not a discussion admin
   * @throws IllegalArgumentException if no fields are provided for update
   */
  fun updateSession(
      requester: Account,
      discussion: Discussion,
      name: String? = null,
      gameId: String? = null,
      date: Timestamp? = null,
      location: Location? = null,
      minParticipants: Int? = null,
      maxParticipants: Int? = null,
      newParticipantList: List<Account>? = null
  ) {
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    if ((minParticipants ?: discussion.session!!.minParticipants) >
        (maxParticipants ?: discussion.session!!.maxParticipants))
        throw IllegalArgumentException(
            "The minimum number of participants can not be more than the maximum number of participants")

    var participantsList: List<String>? = null
    if (newParticipantList != null) {
      participantsList = newParticipantList.toList().map { it -> it.uid }
      if (participantsList.isEmpty()) throw IllegalArgumentException("No Participants")
      if (participantsList.size < (minParticipants ?: discussion.session!!.minParticipants))
          throw IllegalArgumentException("To little participants")
      if (participantsList.size > (maxParticipants ?: discussion.session!!.maxParticipants))
          throw IllegalArgumentException("To many participants")
    }

    viewModelScope.launch {
      _discussion.value =
          repository.updateSession(
              discussion.uid,
              name,
              gameId,
              date,
              location,
              minParticipants,
              maxParticipants,
              participantsList)
    }
  }

  /**
   * Deletes the session from a discussion.
   *
   * Requires admin privileges (requester must be a discussion participant). Removes the session by
   * setting it to null in Firestore.
   *
   * @param requester The account requesting to delete the session
   * @param discussion The discussion containing the session to delete
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun deleteSession(requester: Account, discussion: Discussion) {
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { repository.deleteSession(discussion.uid) }
  }

  /**
   * Sets the selected game for the session.
   *
   * Updates the game UI state with the selected game's UID and name (requires admin privileges).
   *
   * Note: This method does **not** update the session itself with the selected game. It only
   * updates the [GameUIState] to reflect the user's current selection in the UI.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param game The [Game] object to select
   */
  fun setGame(requester: Account, discussion: Discussion, game: Game) {
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")
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
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param query The substring to search for in game names.
   */
  fun setGameQuery(requester: Account, discussion: Discussion, query: String) {
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

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
                fetchedGame = game,
                gameFetchError = null,
            )
      } catch (_: Exception) {
        _gameUIState.value =
            _gameUIState.value.copy(
                fetchedGame = null, gameFetchError = "Failed to fetch game details")
      }
    }
  }
}
