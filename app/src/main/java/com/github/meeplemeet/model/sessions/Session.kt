package com.github.meeplemeet.model.sessions

import com.github.meeplemeet.model.shared.location.Location
import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a gaming session with full game details.
 *
 * This is the primary data class used within the application for representing sessions with
 * complete game information.
 *
 * @property name The name or title of the session
 * @property gameId The if of the complete game object associated with this session
 * @property date The scheduled date and time of the session
 * @property location The physical or virtual location where the session will take place
 * @property participants List of participant IDs (typically user IDs) who are part of this session
 */
@Serializable
data class Session(
    val name: String = "",
    val gameId: String = "",
    val date: Timestamp = Timestamp.now(),
    val location: Location = Location(),
    val participants: List<String> = emptyList()
)
