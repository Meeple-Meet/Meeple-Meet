package com.github.meeplemeet.model.sessions
// AI was used in this file

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

private val onlyAdminErrorText: String = "Only discussion admins can perform this operation"

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
    private val imageRepository: ImageRepository = ImageRepository()
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
        throw PermissionDeniedException(onlyAdminErrorText)
      }
    }

    var participantsList: List<String>? = null
    // Check if the new participant list contains at least one discussion admin
    if (newParticipantList != null) {
      participantsList = newParticipantList.toList().map { it.uid }
      val newParticipantUids = newParticipantList.map { it.uid }
      val hasAdmin = discussion.admins.any { adminUid -> newParticipantUids.contains(adminUid) }

      if (!hasAdmin) {
        // No admin in the new participant list, delete the session instead
        deleteSession(requester, discussion)
        return
      }
    }

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
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(onlyAdminErrorText)

    viewModelScope.launch { sessionRepository.deleteSession(discussion.uid) }
  }

  /**
   * Saves a photo to the session and updates the session document.
   *
   * Requires admin privileges (requester must be a discussion admin). The photo is uploaded to
   * Firebase Storage at `discussions/{discussionId}/session/photo.webp` and the returned URL is
   * stored in the session's photoUrl field.
   *
   * @param requester The account requesting to save the photo
   * @param discussion The discussion containing the session
   * @param context Android context for accessing cache directory
   * @param inputPath Absolute path to the source image file (from gallery or camera)
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun saveSessionPhoto(
      requester: Account,
      discussion: Discussion,
      context: Context,
      inputPath: String
  ) {
    if (!isAdmin(requester, discussion)) throw PermissionDeniedException(onlyAdminErrorText)

    viewModelScope.launch {
      val photoUrl = imageRepository.saveSessionPhoto(context, discussion.uid, inputPath)
      sessionRepository.updateSession(discussion.uid, photoUrl = photoUrl)
    }
  }

  /**
   * Deletes the photo from the session and updates the session document.
   *
   * Requires admin privileges (requester must be a discussion admin). The photo is deleted from
   * Firebase Storage and local cache, and the session's photoUrl field is set to null.
   *
   * @param requester The account requesting to delete the photo
   * @param discussion The discussion containing the session
   * @param context Android context for accessing cache directory
   * @throws PermissionDeniedException if requester is not a discussion admin
   */
  fun deleteSessionPhoto(requester: Account, discussion: Discussion, context: Context) {
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch {
      imageRepository.deleteSessionPhoto(context, discussion.uid)
      sessionRepository.updateSession(discussion.uid, photoUrl = "")
    }
  }

  /**
   * Loads the session photo from cache or Firebase Storage.
   *
   * This is a read operation and does not require admin privileges. The photo is returned as a byte
   * array in WebP format.
   *
   * @param discussion The discussion containing the session
   * @param context Android context for accessing cache directory
   * @return The image as a byte array in WebP format
   */
  suspend fun loadSessionPhoto(discussion: Discussion, context: Context): ByteArray {
    return imageRepository.loadSessionPhoto(context, discussion.uid)
  }
}
