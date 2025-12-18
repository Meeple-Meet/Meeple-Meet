package com.github.meeplemeet.model.sessions
// AI was used in this file

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

private const val ERROR_ADMIN_PERMISSION = "Only discussion admins can perform this operation"

/**
 * ViewModel for managing gaming sessions within a discussion.
 *
 * Provides permission-controlled access to session operations (create, update, delete) and
 * maintains the current discussion state with reactive updates.
 *
 * @property accountRepository Repository for account operations
 * @property sessionRepository Repository for session data operations
 */
class SessionEditViewModel(
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
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch { sessionRepository.deleteSession(discussion.uid) }
  }

  /**
   * Updates one or more fields of an existing session, including participant list.
   *
   * @param requester The account requesting to update the session
   * @param discussion The discussion containing the session
   * @param name Optional new session name
   * @param gameId Optional new game ID
   * @param gameName Optional new game name
   * @param date Optional new scheduled date and time
   * @param location Optional new location
   * @param participants Optional new list of participant IDs
   * @param rentalId Optional new rental ID.
   * @throws PermissionDeniedException if requester is not a discussion admin
   * @throws IllegalArgumentException if if only one of {@code gameId} or {@code gameName} is
   *   provided
   */
  fun updateSession(
      requester: Account,
      discussion: Discussion,
      name: String? = null,
      gameId: String? = null,
      gameName: String? = null,
      date: Timestamp? = null,
      location: Location? = null,
      participants: List<String>? = null,
      rentalId: String? = null
  ) {
    if (!isAdmin(requester, discussion)) {
      throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)
    }

    viewModelScope.launch {
      sessionRepository.updateSession(
          discussionId = discussion.uid,
          name = name,
          gameId = gameId,
          gameName = gameName,
          date = date,
          location = location,
          newParticipantList = participants,
          rentalId = rentalId)
    }
  }
}
