package com.github.meeplemeet.model.shops

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.sessions.GameViewModel

open class ShopGameViewModel() : GameViewModel() {

  fun setGame(shop: Shop, requester: Account, game: Game) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can edit his own shop")

    setGame(game)
  }

  fun setGameQuery(shop: Shop, requester: Account, query: String) {
    if (shop.owner.uid != requester.uid)
        throw PermissionDeniedException("Only the shop's owner can edit his own shop")

    setGameQuery(query)
  }
}
