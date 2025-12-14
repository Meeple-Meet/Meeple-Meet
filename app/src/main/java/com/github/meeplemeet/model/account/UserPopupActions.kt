package com.github.meeplemeet.model.account

interface UserProfilePopupActions {
    fun onBlock(curr: Account, account: Account)
    fun onSendFriendRequest(curr: Account, account: Account)
    fun onRemoveFriend(curr: Account, account: Account)
}
