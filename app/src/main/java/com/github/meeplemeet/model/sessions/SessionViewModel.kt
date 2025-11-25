package com.github.meeplemeet.model.sessions
// AI was used for this file

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val imageRepository: ImageRepository = RepositoryProvider.images
) : CreateSessionViewModel() {

  private val _errorMessage = MutableStateFlow<String?>(null)
  /** StateFlow for observing error messages from photo operations */
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  /** Clears the current error message */
  fun clearError() {
    _errorMessage.value = null
  }
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
    if (!isAdmin(requester, discussion))
        throw PermissionDeniedException("Only discussion admins can perform this operation")

    viewModelScope.launch { sessionRepository.deleteSession(discussion.uid) }
  }

  /**
   * Adds photos to a gaming session.
   *
   * Uploads photos to Firebase Storage, retrieves their URLs, and updates the session's photo list
   * in Firestore with SessionPhoto objects (containing UUID and URL). The operation is performed
   * asynchronously in the viewModelScope. Errors are communicated via [errorMessage] StateFlow.
   *
   * @param context Android context for accessing storage
   * @param discussionId The unique identifier of the discussion containing the session
   * @param photoPaths Absolute paths to the photo files to upload
   */
  fun addSessionPhotos(context: Context, discussionId: String, vararg photoPaths: String) {
    viewModelScope.launch {
      try {
        _errorMessage.value = null
        val photos = imageRepository.saveSessionPhotos(context, discussionId, *photoPaths)
        sessionRepository.addSessionPhotos(discussionId, photos)
      } catch (e: Exception) {
        _errorMessage.value = "Failed to add photo: ${e.message}"
      }
    }
  }

  /**
   * Removes a photo from a gaming session.
   *
   * Deletes the photo from both Firebase Storage and the session's photo list in Firestore. The
   * imageRepository handles both operations internally. Errors are communicated via [errorMessage]
   * StateFlow.
   *
   * @param context Android context for accessing storage
   * @param discussionId The unique identifier of the discussion containing the session
   * @param photoUuid The UUID of the photo to remove (without .webp extension)
   */
  fun removeSessionPhoto(context: Context, discussionId: String, photoUuid: String) {
    viewModelScope.launch {
      try {
        _errorMessage.value = null
        imageRepository.deleteSessionPhoto(context, discussionId, photoUuid)
        sessionRepository.removeSessionPhoto(discussionId, photoUuid)
      } catch (e: Exception) {
        _errorMessage.value = "Failed to delete photo: ${e.message}"
      }
    }
  }

  /**
   * Loads all photos belonging to a gaming session.
   *
   * Fetches photo UUIDs from the session document, then loads the corresponding images from
   * Firebase Storage. Returns results via a callback. On success, the callback receives
   * Result.success with the photo list. On failure, it receives Result.failure with the exception.
   * The operation is performed asynchronously in the viewModelScope.
   *
   * @param context Android context for accessing storage
   * @param discussionId The unique identifier of the discussion containing the session
   * @param onResult Callback that receives a Result containing either the list of (UUID, photo
   *   bytes) pairs or an exception
   */
  fun loadSessionPhotos(
      context: Context,
      discussionId: String,
      onResult: (Result<List<Pair<String, ByteArray>>>) -> Unit
  ) {
    viewModelScope.launch {
      try {
        // Get UUIDs from session repository
        val session = sessionRepository.getSession(discussionId)
        if (session == null) {
          onResult(Result.success(emptyList()))
          return@launch
        }

        val photoUuids = session.sessionPhotos.map { it.uuid }

        // Load images from image repository
        val photos = imageRepository.loadSessionPhotos(context, discussionId, photoUuids)
        onResult(Result.success(photos))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }
}
