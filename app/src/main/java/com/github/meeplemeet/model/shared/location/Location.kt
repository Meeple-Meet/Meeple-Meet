package com.github.meeplemeet.model.shared.location

import kotlinx.serialization.Serializable

/**
 * Lightweight, serializable representation of a geographic point used across the app and Firestore
 * documents.
 *
 * @property latitude Latitude in decimal degrees.
 * @property longitude Longitude in decimal degrees.
 * @property name Optional human-readable description (e.g., address or place name).
 */
@Serializable
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val name: String = "",
)
