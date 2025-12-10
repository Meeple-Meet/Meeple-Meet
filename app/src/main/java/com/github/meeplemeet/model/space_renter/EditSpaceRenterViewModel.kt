// Docs generated with Claude Code.

package com.github.meeplemeet.model.space_renter

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shops.OpeningHours
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for editing and deleting existing space renters.
 *
 * This ViewModel handles space renter updates and deletions with permission validation to ensure
 * only the space renter owner can perform these operations. It also handles offline mode by
 * synchronizing pending changes when connectivity is restored.
 *
 * @property spaceRenterRepository The repository used for space renter operations.
 * @property imageRepository The repository used for image operations.
 */
class EditSpaceRenterViewModel(
    private val spaceRenterRepository: SpaceRenterRepository = RepositoryProvider.spaceRenters,
    private val imageRepository: ImageRepository = RepositoryProvider.images
) : SpaceRenterSearchViewModel() {
  private val _currentSpaceRenter = MutableStateFlow<SpaceRenter?>(null)
  val currentSpaceRenter: StateFlow<SpaceRenter?> = _currentSpaceRenter.asStateFlow()

  /**
   * Initializes the ViewModel with a space renter. Also loads it from cache/repository to ensure we
   * have the latest data.
   */
  fun initialize(spaceRenter: SpaceRenter) {
    _currentSpaceRenter.value = spaceRenter

    // Load latest version from cache or repository
    viewModelScope.launch {
      OfflineModeManager.loadSpaceRenter(spaceRenter.id) { loaded ->
        if (loaded != null) {
          _currentSpaceRenter.value = loaded
        }
      }
    }
  }
  /**
   * Updates one or more fields of an existing space renter.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the space renter owner
   * can update the space renter. Validates that:
   * - The requester is the space renter owner
   * - If provided, the space renter name is not blank
   * - If provided, exactly 7 opening hours entries are included (one for each day of the week)
   * - If provided, the address is valid
   *
   * The photoCollectionUrl parameter can contain a mix of:
   * - Existing Firebase URLs (kept as-is)
   * - Local file paths (uploaded to Firebase) Old photos not in the new list will be deleted from
   *   Firebase. This function automatically handles both online and offline modes through
   *   OfflineModeManager.
   *
   * @param context The Android context for image operations.
   * @param spaceRenter The space renter to update.
   * @param requester The account requesting the update.
   * @param owner The new owner account (optional).
   * @param name The new name of the space rental business (optional).
   * @param phone The new contact phone number (optional).
   * @param email The new contact email address (optional).
   * @param website The new website URL (optional).
   * @param address The new physical location (optional).
   * @param openingHours The new opening hours, must include exactly 7 entries if provided
   *   (optional).
   * @param spaces The new collection of rentable spaces (optional).
   * @param photoCollectionUrl The new photo collection (mix of Firebase URLs and local paths)
   *   (optional).
   * @throws PermissionDeniedException if the requester is not the space renter owner.
   * @throws IllegalArgumentException if the space renter name is blank, if not exactly 7 opening
   *   hours entries are provided, or if the address is not valid.
   */
  suspend fun updateSpaceRenter(
      context: Context,
      spaceRenter: SpaceRenter,
      requester: Account,
      owner: Account? = null,
      name: String? = null,
      phone: String? = null,
      email: String? = null,
      website: String? = null,
      address: Location? = null,
      openingHours: List<OpeningHours>? = null,
      spaces: List<Space>? = null,
      photoCollectionUrl: List<String>? = emptyList()
  ) {
    val params =
        SpaceRenterUpdateParams(
            owner, name, phone, email, website, address, openingHours, spaces, photoCollectionUrl)
    validateUpdateRequest(spaceRenter, requester, params)

    val isOnline = OfflineModeManager.hasInternetConnection.first()

    if (isOnline) {
      handleOnlineUpdate(spaceRenter, params, context)
    } else {
      handleOfflineUpdate(spaceRenter, params)
    }
  }

  /**
   * Validates the update request parameters.
   *
   * @throws PermissionDeniedException if the requester is not the owner
   * @throws IllegalArgumentException if validation fails
   */
  private fun validateUpdateRequest(
      spaceRenter: SpaceRenter,
      requester: Account,
      params: SpaceRenterUpdateParams
  ) {
    if (spaceRenter.owner.uid != requester.uid) {
      throw PermissionDeniedException("Only the space renter's owner can edit his own space renter")
    }

    require(!(params.name != null && params.name.isBlank())) { "SpaceRenter name cannot be blank" }

    if (params.openingHours != null) {
      val uniqueByDay = params.openingHours.distinctBy { it.day }
      require(uniqueByDay.size == 7) { "7 opening hours are needed" }
    }

    require(!(params.address != null && params.address == Location())) {
      "An address is required to create a space renter"
    }
  }

  /** Handles online update by persisting to repository and updating cache. */
  private suspend fun handleOnlineUpdate(
      spaceRenter: SpaceRenter,
      params: SpaceRenterUpdateParams,
      context: Context
  ) {
    // Determine which photos are new and which should be deleted
    val oldUrls = spaceRenter.photoCollectionUrl
    val newPaths = params.photoCollectionUrl ?: emptyList()

    // Separate existing Firebase URLs from new local paths
    val keptUrls =
        newPaths.filter { path ->
          oldUrls.contains(path) && (path.startsWith("http://") || path.startsWith("https://"))
        }
    val localPathsToUpload =
        newPaths.filter { path -> !path.startsWith("http://") && !path.startsWith("https://") }

    // Delete old photos that were removed (only Firebase URLs, not local paths)
    val urlsToDelete =
        oldUrls.filter { url ->
          !newPaths.contains(url) && (url.startsWith("http://") || url.startsWith("https://"))
        }
    if (urlsToDelete.isNotEmpty()) {
      try {
        withContext(NonCancellable) {
          imageRepository.deleteSpaceRenterPhotos(
              context, spaceRenter.id, *urlsToDelete.toTypedArray())
        }
      } catch (e: Exception) {
        Log.e(
            "upload",
            "Failed to delete old space renter photos for ${spaceRenter.id}: ${e.message}",
            e)
      }
    }

    // Upload new local photos
    val uploadedUrls =
        if (localPathsToUpload.isNotEmpty()) {
          try {
            withContext(NonCancellable) {
              imageRepository.saveSpaceRenterPhotos(
                  context, spaceRenter.id, *localPathsToUpload.toTypedArray())
            }
          } catch (e: Exception) {
            Log.e(
                "upload", "Image upload failed for space renter ${spaceRenter.id}: ${e.message}", e)
            throw e
          }
        } else {
          emptyList()
        }

    // Create a map of local paths to their uploaded URLs
    val localToUploadedMap = localPathsToUpload.zip(uploadedUrls).toMap()

    // Combine kept Firebase URLs with newly uploaded URLs (preserving order from newPaths)
    val finalPhotoUrls =
        newPaths.mapNotNull { path ->
          when {
            // If it's a kept Firebase URL, use it as-is
            keptUrls.contains(path) -> path
            // If it's a local path that was uploaded, use the uploaded URL
            localToUploadedMap.containsKey(path) -> localToUploadedMap[path]
            // Otherwise skip it (shouldn't happen, but be safe)
            else -> null
          }
        }
    spaceRenterRepository.updateSpaceRenter(
        spaceRenter.id,
        params.owner?.uid,
        params.name,
        params.phone,
        params.email,
        params.website,
        params.address,
        params.openingHours,
        params.spaces,
        finalPhotoUrls)

    // Optimistically update cache with the data we just successfully sent
    // This avoids race conditions where getSpaceRenterSafe returns stale data
    val updatedSpaceRenter =
        spaceRenter.copy(
            owner = params.owner ?: spaceRenter.owner,
            name = params.name ?: spaceRenter.name,
            phone = params.phone ?: spaceRenter.phone,
            email = params.email ?: spaceRenter.email,
            website = params.website ?: spaceRenter.website,
            address = params.address ?: spaceRenter.address,
            openingHours = params.openingHours ?: spaceRenter.openingHours,
            spaces = params.spaces ?: spaceRenter.spaces,
            photoCollectionUrl = finalPhotoUrls)

    OfflineModeManager.updateSpaceRenterCache(updatedSpaceRenter)
    _currentSpaceRenter.value = updatedSpaceRenter

    OfflineModeManager.clearSpaceRenterChanges(spaceRenter.id)
  }

  /** Handles offline update by recording changes and updating cache. */
  private suspend fun handleOfflineUpdate(
      spaceRenter: SpaceRenter,
      params: SpaceRenterUpdateParams
  ) {
    // OFFLINE: Record changes and update cached space renter object
    val changes = buildChangeMap(params)

    // Apply changes to create updated space renter object
    val updatedSpaceRenter =
        spaceRenter.copy(
            owner = params.owner ?: spaceRenter.owner,
            name = params.name ?: spaceRenter.name,
            phone = params.phone ?: spaceRenter.phone,
            email = params.email ?: spaceRenter.email,
            website = params.website ?: spaceRenter.website,
            address = params.address ?: spaceRenter.address,
            openingHours = params.openingHours ?: spaceRenter.openingHours,
            spaces = params.spaces ?: spaceRenter.spaces,
            photoCollectionUrl = params.photoCollectionUrl ?: spaceRenter.photoCollectionUrl)

    // Update cache with modified space renter
    OfflineModeManager.updateSpaceRenterCache(updatedSpaceRenter)

    // Update local StateFlow
    _currentSpaceRenter.value = updatedSpaceRenter

    // Record changes for sync
    changes.forEach { (property, value) ->
      OfflineModeManager.setSpaceRenterChange(updatedSpaceRenter, property, value)
    }
  }

  private fun buildChangeMap(params: SpaceRenterUpdateParams): Map<String, Any> {
    val changes = mutableMapOf<String, Any>()
    if (params.owner != null) changes[SpaceRenter::owner.name] = params.owner.uid
    if (params.name != null) changes[SpaceRenter::name.name] = params.name
    if (params.phone != null) changes[SpaceRenter::phone.name] = params.phone
    if (params.email != null) changes[SpaceRenter::email.name] = params.email
    if (params.website != null) changes[SpaceRenter::website.name] = params.website
    if (params.address != null) changes[SpaceRenter::address.name] = params.address
    if (params.openingHours != null) changes[SpaceRenter::openingHours.name] = params.openingHours
    if (params.spaces != null) changes[SpaceRenter::spaces.name] = params.spaces
    if (params.photoCollectionUrl != null)
        changes[SpaceRenter::photoCollectionUrl.name] = params.photoCollectionUrl
    return changes
  }

  /** Deletes a space renter. */
  fun deleteSpaceRenter(spaceRenter: SpaceRenter, requester: Account) {
    if (spaceRenter.owner.uid != requester.uid)
        throw PermissionDeniedException(
            "Only the space renter's owner can delete his own space renter")

    viewModelScope.launch {
      spaceRenterRepository.deleteSpaceRenter(spaceRenter.id)
      OfflineModeManager.removeSpaceRenter(spaceRenter.id)
    }
  }
}

/** Data class to encapsulate update parameters and reduce parameter count in methods. */
private data class SpaceRenterUpdateParams(
    val owner: Account? = null,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: Location? = null,
    val openingHours: List<OpeningHours>? = null,
    val spaces: List<Space>? = null,
    val photoCollectionUrl: List<String>? = null
)
