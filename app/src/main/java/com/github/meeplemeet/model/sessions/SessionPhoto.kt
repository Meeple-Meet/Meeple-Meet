package com.github.meeplemeet.model.sessions
// AI was used in this file

import kotlinx.serialization.Serializable

/**
 * Represents a photo attached to a session.
 *
 * Stores both UUID (for tracking/deletion) and URL (for frontend display).
 * This enables simple frontend image loading via AsyncImage while maintaining
 * the ability to perform reverse lookups (photo → session) via UUID.
 *
 * @property uuid Unique identifier for the photo (used for deletion and tracking)
 * @property url Firebase Storage download URL for the photo (used for display)
 */
@Serializable
data class SessionPhoto(
    val uuid: String = "",
    val url: String = ""
)
