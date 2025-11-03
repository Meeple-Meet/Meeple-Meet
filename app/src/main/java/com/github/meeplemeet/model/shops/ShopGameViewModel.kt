// Docs generated with Claude Code.

package com.github.meeplemeet.model.shops

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.sessions.GameViewModel

/**
 * Base ViewModel for shop-related screens that need game selection functionality.
 *
 * Extends [GameViewModel] to provide game search and selection with shop owner permission
 * validation. This ViewModel ensures that only the shop owner can modify game-related data.
 */
open class ShopGameViewModel() : GameViewModel() {

  /**
   * Sets the selected game for a shop with permission validation.
   *
   * Only the shop owner can select games for their shop. Updates the game UI state with the
   * selected game's UID and name.
   *
   * @param shop The shop to add the game to.
   * @param requester The account requesting the game selection.
   * @param game The game to select.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun setGame(shop: Shop, requester: Account, game: Game) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can edit his own shop")

    setGame(game)
  }

  /**
   * Updates the game search query for a shop with permission validation.
   *
   * Only the shop owner can search for games to add to their shop. This method updates the visible
   * game query in the UI state and triggers a background search for matching games.
   *
   * @param shop The shop context for the game search.
   * @param requester The account requesting the game search.
   * @param query The search query string to find games by name.
   * @throws PermissionDeniedException if the requester is not the shop owner.
   */
  fun setGameQuery(shop: Shop, requester: Account, query: String) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can edit his own shop")

    setGameQuery(query)
  }
}
