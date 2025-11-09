package com.github.meeplemeet.model.sessions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

/**
 * ViewModel for managing gaming sessions within a discussion.
 *
 * Provides permission-controlled access to session operations (create, update, delete) and
 * maintains the current discussion state with reactive updates.
 *
 * @property sessionRepository Repository for session data operations
 */
class SessionViewModel(
    private val sessionRepository: SessionRepository = RepositoryProvider.sessions,
) : CreateSessionViewModel() {
  /**
   * Checks if an account has admin privileges for a discussion.
   *
   * Currently checks if the requester is a participant in the discussion.
   */
  private fun isAdmin(requester: Account, discussion: Discussion): Boolean {
    return discussion.admins.contains(requester.uid)
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
    if (newParticipantList != null) participantsList = newParticipantList.toList().map { it.uid }

    viewModelScope.launch {
      sessionRepository.updateSession(
          discussion.uid, name, gameId, date, location, participantsList)
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

    viewModelScope.launch { sessionRepository.deleteSession(discussion.uid) }
  }
}
