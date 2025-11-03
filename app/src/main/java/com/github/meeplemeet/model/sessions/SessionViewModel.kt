package com.github.meeplemeet.model.sessions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.shared.Location
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing gaming sessions within a discussion.
 *
 * Provides permission-controlled access to session operations (create, update, delete) and
 * maintains the current discussion state with reactive updates.
 *
 * @property initDiscussion The initial discussion state
 * @property repository Repository for session data operations
 */
class SessionViewModel(
    initDiscussion: Discussion,
    private val repository: SessionRepository = RepositoryProvider.sessions,
    private val gameRepository: GameRepository = RepositoryProvider.games
) : GameViewModel(gameRepository) {
  /** Observable discussion state that updates when session operations complete. */
  private val _discussion = MutableStateFlow(initDiscussion)
  val discussion: StateFlow<Discussion> = _discussion

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
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    val participantsList = participants.toList().map { it -> it.uid }
    if (participantsList.isEmpty()) throw IllegalArgumentException("No Participants")

    viewModelScope.launch {
      _discussion.value =
          repository.updateSession(discussion.uid, name, gameId, date, location, participantsList)
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
      newParticipantList: List<Account>? = null
  ) {
    if (!isAdmin(requester, discussion)) {
      // Allow non-admin to remove themselves if only updating participants and they're not in the
      // new list
      val isRemovingSelfOnly =
          newParticipantList != null &&
              discussion.session?.participants?.toSet() ==
                  (newParticipantList.map { it.uid } + requester.uid).toSet() &&
              name == null &&
              gameId == null &&
              date == null &&
              location == null

      if (!isRemovingSelfOnly) {
        throw PermissionDeniedException("Only discussion admins can perform this operation")
      }
    }

    var participantsList: List<String>? = null
    if (newParticipantList != null)
        participantsList = newParticipantList.toList().map { it -> it.uid }

    viewModelScope.launch {
      _discussion.value =
          repository.updateSession(discussion.uid, name, gameId, date, location, participantsList)
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
    setGame(game)
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

    setGameQuery(query)
  }
}
