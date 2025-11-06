// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import androidx.lifecycle.viewModelScope
import com.github.meeplemeet.RepositoryProvider
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.model.shared.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for editing and deleting existing shops.
 *
 * This ViewModel handles shop updates and deletions with permission validation to ensure only the
 * shop owner can perform these operations.
 *
 * @property repository The repository used for shop operations.
 */
class EditShopViewModel(private val repository: ShopRepository = RepositoryProvider.shops) :
    ShopSearchViewModel() {

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
      repository.updateShop(
          shop.id, owner?.uid, name, phone, email, website, address, openingHours, gameCollection)
    }
  }

  /**
   * Deletes a shop from Firestore.
   *
   * This operation is performed asynchronously in the viewModelScope. Only the shop owner can
   * delete the shop.
   *
   * @param shop The shop to delete.
   * @param requester The account requesting the deletion.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun deleteShop(shop: Shop, requester: Account) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can delete his own shop")

    viewModelScope.launch { repository.deleteShop(shop.id) }
  }
}
