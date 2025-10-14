package com.github.meeplemeet.model.structures

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val name: String = "",
)
