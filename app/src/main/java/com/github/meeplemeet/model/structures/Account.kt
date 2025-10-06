package com.github.meeplemeet.model.structures

import kotlinx.serialization.Serializable

data class Account(
    val uid: String,
    val name: String,
    val previews: Map<String, DiscussionPreview> = emptyMap()
)

@Serializable data class AccountNoUid(val name: String = "")

fun toNoUid(account: Account): AccountNoUid = AccountNoUid(account.name)

fun fromNoUid(
    id: String,
    accountNoUid: AccountNoUid,
    previews: Map<String, DiscussionPreviewNoUid> = emptyMap()
): Account =
    Account(id, accountNoUid.name, previews.mapValues { (uid, preview) -> fromNoUid(uid, preview) })
