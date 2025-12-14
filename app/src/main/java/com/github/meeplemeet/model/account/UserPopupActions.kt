package com.github.meeplemeet.model.account

interface UserProfilePopupActions {
  fun onBlock(curr: Account, other: Account)

  fun onSendFriendRequest(curr: Account, other: Account)

  fun onRemoveFriend(curr: Account, other: Account)
}
