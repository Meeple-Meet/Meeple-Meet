package com.github.meeplemeet.model.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.repositories.FirestoreSessionRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Location
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
class FirestoreSessionViewModel(
    initDiscussion: Discussion,
    private val repository: FirestoreSessionRepository = FirestoreSessionRepository()
) : ViewModel() {
  private val _discussion = MutableStateFlow(initDiscussion)

  /** Observable discussion state that updates when session operations complete. */
  val discussion: StateFlow<Discussion> = _discussion

  /**
   * Checks if an account has admin privileges for a discussion.
   *
   * Currently checks if the requester is a participant in the discussion.
   */
  private fun isAdmin(requester: Account, discussion: Discussion): Boolean {
    return discussion.participants.contains(requester.uid)
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
}
