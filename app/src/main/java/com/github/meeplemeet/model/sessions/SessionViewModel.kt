package com.github.meeplemeet.model.sessions

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.account.NotificationSettings
import com.github.meeplemeet.model.account.RelationshipStatus
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
class SessionViewModel(
    private val accountRepository: AccountRepository = RepositoryProvider.accounts,
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
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun updateSession(
      requester: Account,
      discussion: Discussion,
      name: String? = null,
      gameId: String? = null,
      date: Timestamp? = null,
      location: Location? = null
  ) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch {
      sessionRepository.updateSession(discussion.uid, name, gameId, date, location, null)
    }
  }

  /**
   * Adds a user to a session (admin-only operation).
   *
   * If the user is already a participant in the session, this method does nothing. This method
   * respects the notification settings of the user:
   * - Users with EVERYONE setting are added directly to the session
   * - Users with FRIENDS_ONLY setting are added only if they are friends with the requester
   * - Users who don't meet these criteria receive a join notification instead
   *
   * @param discussion The discussion containing the session
   * @param changeRequester The account requesting the change
   * @param user The account to add as a participant
   * @throws PermissionDeniedException if the requester is not an admin
   */
  fun addUserToSession(discussion: Discussion, changeRequester: Account, user: Account) {
    if (discussion.session?.participants?.contains(user.uid) == true) return
    if (!isAdmin(changeRequester, discussion))
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch {
      val session = discussion.session ?: return@launch
      val currentParticipants = session.participants

      if (user.notificationSettings == NotificationSettings.EVERYONE ||
          (user.notificationSettings == NotificationSettings.FRIENDS_ONLY &&
              changeRequester.relationships[user.uid] == RelationshipStatus.FRIEND)) {
        val updatedParticipants = currentParticipants + user.uid
        sessionRepository.updateSession(discussion.uid, null, null, null, null, updatedParticipants)
      } else {
        accountRepository.sendJoinSessionNotification(user.uid, discussion)
      }
    }
  }

  /**
   * Removes a user from a session (admin-only or self-removal).
   *
   * Users can remove themselves from a session. Admins can remove other users. If removing a user
   * would leave no admins in the session, the session is deleted instead.
   *
   * @param discussion The discussion containing the session
   * @param changeRequester The account requesting the change
   * @param user The account to remove from the session
   * @throws PermissionDeniedException if the requester is not an admin (and not removing
   *   themselves)
   */
  fun removeUserFromSession(discussion: Discussion, changeRequester: Account, user: Account) {
    if (changeRequester != user && !isAdmin(changeRequester, discussion))
        throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch {
      val session = discussion.session ?: return@launch
      val currentParticipants = session.participants
      val updatedParticipants = currentParticipants.filterNot { it == user.uid }

      // Check if any admins remain in the updated participant list
      val hasAdmin = discussion.admins.any { adminUid -> updatedParticipants.contains(adminUid) }

      if (!hasAdmin) {
        // No admin in the new participant list, delete the session instead
        sessionRepository.deleteSession(discussion.uid)
      } else {
        sessionRepository.updateSession(discussion.uid, null, null, null, null, updatedParticipants)
      }
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
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(ERROR_ADMIN_PERMISSION)

    viewModelScope.launch { sessionRepository.deleteSession(discussion.uid) }
  }
}
