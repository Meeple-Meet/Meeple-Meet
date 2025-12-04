// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for editing and deleting existing shops.
 *
 * This ViewModel handles shop updates and deletions with permission validation to ensure only the
 * shop owner can perform these operations.
 *
 * @property shopRepository The repository used for shop operations.
 */
class EditShopViewModel(
    private val shopRepository: ShopRepository = RepositoryProvider.shops,
) : ShopSearchViewModel() {

  // Expose the currently loaded shop for editing
  private val _shop = MutableStateFlow<Shop?>(null)
  val shop: StateFlow<Shop?> = _shop

  /**
   * Sets the shop to be edited.
   *
   * @param shop The shop to edit.
   */
  fun setShop(shop: Shop?) {
    _shop.value = shop

    if (shop != null) {
      // Load latest version from cache or repository
      viewModelScope.launch {
        OfflineModeManager.loadShop(shop.id) { loaded ->
          if (loaded != null) {
            _shop.value = loaded
          }
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
   * @throws PermissionDeniedException if the requester is not the shop owner.
   * @throws IllegalArgumentException if the shop name is blank, if not exactly 7 opening hours
   *   entries are provided, or if the address is not valid.
   */
  fun updateShop(
      shop: Shop,
      requester: Account,
      owner: Account? = null,
      name: String? = null,
      phone: String? = null,
      email: String? = null,
      website: String? = null,
      address: Location? = null,
      openingHours: List<OpeningHours>? = null,
      gameCollection: List<Pair<Game, Int>>? = null,
      photoCollectionUrl: List<String>? = emptyList()
  ) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can edit his own shop")

    if (name != null && name.isBlank()) throw IllegalArgumentException("Shop name cannot be blank")

    if (openingHours != null) {
      val uniqueByDay = openingHours.distinctBy { it.day }
      if (uniqueByDay.size != 7) throw IllegalArgumentException("7 opening hours are needed")
    }

    if (address != null && address == Location())
        throw IllegalArgumentException("An address is required to create a shop")

    viewModelScope.launch {
      val isOnline = OfflineModeManager.hasInternetConnection.first()

      if (isOnline) {
        shopRepository.updateShop(
            shop.id,
            owner?.uid,
            name,
            phone,
            email,
            website,
            address,
            openingHours,
            gameCollection,
            photoCollectionUrl)

        val refreshed = shopRepository.getShopSafe(shop.id)

        if (refreshed != null) {
          // Update both cache and UI state
          OfflineModeManager.updateShopCache(refreshed)
          _shop.value = refreshed
        }

        OfflineModeManager.clearShopChanges(shop.id)
      } else {
        val changes = mutableMapOf<String, Any>()
        if (owner != null) changes[Shop::owner.name] = owner.uid
        if (name != null) changes[Shop::name.name] = name
        if (phone != null) changes[Shop::phone.name] = phone
        if (email != null) changes[Shop::email.name] = email
        if (website != null) changes[Shop::website.name] = website
        if (address != null) changes[Shop::address.name] = address
        if (openingHours != null) changes[Shop::openingHours.name] = openingHours
        if (gameCollection != null) changes[Shop::gameCollection.name] = gameCollection
        if (photoCollectionUrl != null) changes[Shop::photoCollectionUrl.name] = photoCollectionUrl

        changes.forEach { (property, value) ->
          OfflineModeManager.setShopChange(shop, property, value)
        }
      }
    }
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
