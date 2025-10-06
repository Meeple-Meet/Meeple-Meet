package com.github.meeplemeet.model.structures

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val uid: String = "",
    val name: String = "",
)
