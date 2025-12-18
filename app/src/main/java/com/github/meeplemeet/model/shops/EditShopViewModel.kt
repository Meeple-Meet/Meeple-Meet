// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.model.shared.location.LocationRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for editing and deleting existing shops.
 *
 * This ViewModel handles shop updates and deletions with permission validation to ensure only the
 * shop owner can perform these operations. It also handles offline mode by synchronizing pending
 * changes when connectivity is restored.
 *
 * @property shopRepository The repository used for shop operations.
 * @property imageRepository The repository used for image operations.
 */
class EditShopViewModel(
    private val shopRepository: ShopRepository = RepositoryProvider.shops,
    private val imageRepository: ImageRepository = RepositoryProvider.images,
    gameRepository: GameRepository = RepositoryProvider.games,
    locationRepository: LocationRepository = RepositoryProvider.locations
) : ShopSearchViewModel(gameRepository, locationRepository) {

  private val _currentShop = MutableStateFlow<Shop?>(null)
  val currentShop: StateFlow<Shop?> = _currentShop.asStateFlow()

  /**
   * Initializes the ViewModel with a shop. Also loads it from cache/repository to ensure we have
   * the latest data.
   */
  fun initialize(shop: Shop) {
    _currentShop.value = shop
    setLocation(shop.address)

    // Load latest version from cache or repository
    viewModelScope.launch {
      OfflineModeManager.loadShop(shop.id) { loaded ->
        if (loaded != null) {
          _currentShop.value = loaded
        }
      }
    }
  }

  /**
   * Updates one or more fields of an existing shop.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the shop owner can
   * update the shop. Validates that:
   * - The requester is the shop owner
   * - If provided, the shop name is not blank
   * - If provided, exactly 7 opening hours entries are included (one for each day of the week)
   * - If provided, the address is valid
   *
   * The photoCollectionUrl parameter can contain a mix of:
   * - Existing Firebase URLs (kept as-is)
   * - Local file paths (uploaded to Firebase) Old photos not in the new list will be deleted from
   *   Firebase.
   *
   *   This function automatically handles both online and offline modes through OfflineModeManager.
   *
   * @param context The Android context for image operations.
   * @param shop The shop to update.
   * @param requester The account requesting the update.
   * @param owner The new owner account (optional).
   * @param name The new name of the shop (optional).
   * @param phone The new contact phone number (optional).
   * @param email The new contact email address (optional).
   * @param website The new website URL (optional).
   * @param address The new physical location (optional).
   * @param openingHours The new opening hours, must include exactly 7 entries if provided
   *   (optional).
   * @param gameCollection The new game collection (optional).
   * @param photoCollectionUrl The new photo collection (mix of Firebase URLs and local paths)
   *   (optional).
   * @throws PermissionDeniedException if the requester is not the shop owner.
   * @throws IllegalArgumentException if the shop name is blank, if not exactly 7 opening hours
   *   entries are provided, or if the address is not valid.
   */
  suspend fun updateShop(
      context: Context,
      shop: Shop,
      requester: Account,
      owner: Account? = null,
      name: String? = null,
      phone: String? = null,
      email: String? = null,
      website: String? = null,
      address: Location? = null,
      openingHours: List<OpeningHours>? = null,
      gameCollection: List<GameItem>? = null,
      photoCollectionUrl: List<String>? = null
  ) {
    val params =
        ShopUpdateParams(
            owner,
            name,
            phone,
            email,
            website,
            address,
            openingHours,
            gameCollection,
            photoCollectionUrl)
    validateUpdateRequest(shop, requester, params)

    val isOnline = OfflineModeManager.hasInternetConnection.first()

    if (isOnline) {
      handleOnlineUpdate(shop, params, context)
    } else {
      handleOfflineUpdate(shop, params)
    }
  }

  /**
   * Validates the update request parameters.
   *
   * @throws PermissionDeniedException if the requester is not the owner
   * @throws IllegalArgumentException if validation fails
   */
  private fun validateUpdateRequest(shop: Shop, requester: Account, params: ShopUpdateParams) {
    if (shop.owner.uid != requester.uid) {
      throw PermissionDeniedException("Only the shop's owner can edit his own shop")
    }

    require(!(params.name != null && params.name.isBlank())) { "Shop name cannot be blank" }

    if (params.openingHours != null) {
      val uniqueByDay = params.openingHours.distinctBy { it.day }
      require(uniqueByDay.size == 7) { "7 opening hours are needed" }
    }

    require(!(params.address != null && params.address == Location())) {
      "An address is required to create a shop"
    }
  }

  /** Handles online update by persisting to repository and updating cache. */
  private suspend fun handleOnlineUpdate(shop: Shop, params: ShopUpdateParams, context: Context) {
    // Determine which photos are new and which should be deleted
    val oldUrls = shop.photoCollectionUrl
    val newLocalPaths = params.photoCollectionUrl ?: emptyList()

    // Separate existing Firebase URLs from new local paths
    val keptUrls =
        newLocalPaths.filter { path ->
          oldUrls.contains(path) && (path.startsWith("http://") || path.startsWith("https://"))
        }
    val localPathsToUpload =
        newLocalPaths.filter { path -> !path.startsWith("http://") && !path.startsWith("https://") }

    // Delete old photos that were removed (only Firebase URLs, not local paths)
    val urlsToDelete =
        oldUrls.filter { url ->
          !newLocalPaths.contains(url) && (url.startsWith("http://") || url.startsWith("https://"))
        }
    if (urlsToDelete.isNotEmpty()) {
      try {
        withContext(NonCancellable) {
          imageRepository.deleteShopPhotos(context, shop.id, *urlsToDelete.toTypedArray())
        }
      } catch (e: Exception) {
        Log.e("upload", "Failed to delete old shop photos for ${shop.id}: ${e.message}", e)
      }
    }

    // Upload new local photos
    val uploadedUrls =
        if (localPathsToUpload.isNotEmpty()) {
          try {
            withContext(NonCancellable) {
              imageRepository.saveShopPhotos(context, shop.id, *localPathsToUpload.toTypedArray())
            }
          } catch (e: Exception) {
            Log.e("upload", "Image upload failed for shop ${shop.id}: ${e.message}", e)
            throw e
          }
        } else {
          emptyList()
        }

    // Create a map of local paths to their uploaded URLs
    val localToUploadedMap = localPathsToUpload.zip(uploadedUrls).toMap()

    // Combine kept Firebase URLs with newly uploaded URLs (preserving order from newPaths)
    val finalPhotoUrls =
        newLocalPaths.mapNotNull { path ->
          when {
            // If it's a kept Firebase URL, use it as-is
            keptUrls.contains(path) -> path
            // If it's a local path that was uploaded, use the uploaded URL
            localToUploadedMap.containsKey(path) -> localToUploadedMap[path]
            // Otherwise skip it (shouldn't happen, but be safe)
            else -> null
          }
        }

    shopRepository.updateShop(
        shop.id,
        params.owner?.uid,
        params.name,
        params.phone,
        params.email,
        params.website,
        params.address,
        params.openingHours,
        params.gameCollection,
        finalPhotoUrls)

    // Optimistically update cache with the data we just successfully sent
    // This avoids race conditions where getShopSafe returns stale data
    val updatedShop =
        shop.copy(
            owner = params.owner ?: shop.owner,
            name = params.name ?: shop.name,
            phone = params.phone ?: shop.phone,
            email = params.email ?: shop.email,
            website = params.website ?: shop.website,
            address = params.address ?: shop.address,
            openingHours = params.openingHours ?: shop.openingHours,
            gameCollection = params.gameCollection ?: shop.gameCollection,
            photoCollectionUrl = finalPhotoUrls)

    OfflineModeManager.updateShopCache(updatedShop)
    _currentShop.value = updatedShop

    OfflineModeManager.clearShopChanges(shop.id)
  }

  /** Handles offline update by recording changes and updating cache. */
  private suspend fun handleOfflineUpdate(shop: Shop, params: ShopUpdateParams) {
    // OFFLINE: Record changes and update cached shop object
    val changes = buildChangeMap(params)

    // Apply changes to create updated shop object
    val updatedShop =
        shop.copy(
            owner = params.owner ?: shop.owner,
            name = params.name ?: shop.name,
            phone = params.phone ?: shop.phone,
            email = params.email ?: shop.email,
            website = params.website ?: shop.website,
            address = params.address ?: shop.address,
            openingHours = params.openingHours ?: shop.openingHours,
            gameCollection = params.gameCollection ?: shop.gameCollection,
            photoCollectionUrl = params.photoCollectionUrl ?: shop.photoCollectionUrl)

    // Update cache with modified shop
    OfflineModeManager.updateShopCache(updatedShop)

    // Update local StateFlow
    _currentShop.value = updatedShop

    // Record changes for sync
    changes.forEach { (property, value) ->
      OfflineModeManager.setShopChange(updatedShop, property, value)
    }
  }

  private fun buildChangeMap(params: ShopUpdateParams): Map<String, Any> {
    val changes = mutableMapOf<String, Any>()
    if (params.owner != null) changes[Shop::owner.name] = params.owner.uid
    if (params.name != null) changes[Shop::name.name] = params.name
    if (params.phone != null) changes[Shop::phone.name] = params.phone
    if (params.email != null) changes[Shop::email.name] = params.email
    if (params.website != null) changes[Shop::website.name] = params.website
    if (params.address != null) changes[Shop::address.name] = params.address
    if (params.openingHours != null) changes[Shop::openingHours.name] = params.openingHours
    if (params.gameCollection != null) changes[Shop::gameCollection.name] = params.gameCollection
    if (params.photoCollectionUrl != null)
        changes[Shop::photoCollectionUrl.name] = params.photoCollectionUrl
    return changes
  }

  /**
   * Deletes a shop from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the shop owner can
   * delete the shop. The repository will automatically remove the shop ID from the owner's
   * businesses subcollection.
   *
   * @param shop The shop to delete.
   * @param requester The account requesting the deletion.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun deleteShop(shop: Shop, requester: Account) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can delete his own shop")

    viewModelScope.launch {
      shopRepository.deleteShop(shop.id)
      OfflineModeManager.removeShop(shop.id)
    }
  }
}

/** Data class to encapsulate update parameters and reduce parameter count in methods. */
private data class ShopUpdateParams(
    val owner: Account? = null,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: Location? = null,
    val openingHours: List<OpeningHours>? = null,
    val gameCollection: List<GameItem>? = null,
    val photoCollectionUrl: List<String>? = null
)
