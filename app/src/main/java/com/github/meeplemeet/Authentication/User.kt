package com.github.meeplemeet.Authentication

data class User(
    val uid: String,
    var name: String?,
    val email: String?,
    var photoUrl: String? = null,
    var description: String? = null
)
