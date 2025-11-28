package com.github.meeplemeet.model.offline

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.map.GeoPinWithLocation
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.space_renter.SpaceRenter

/**
 * Represents the offline mode state for the application, storing all data that needs to be
 * synchronized when the device goes back online.
 *
 * This data class holds pending changes made while offline, including new or modified entities that
 * should be uploaded to the backend once connectivity is restored.
 *
 * @property accounts Map of account IDs to pairs of Account objects and their associated metadata.
 *   The metadata map contains additional information needed for synchronization.
 * @property discussions Map of discussion IDs to pairs of Discussion objects and their associated
 *   pending messages.
 * @property posts Map of post IDs to Post objects representing posts created (true) or modified
 *   offline (false).
 * @property shopsToAdd Map of shop IDs to pairs of Shop objects and their associated metadata,
 *   representing shops to be created or updated when online.
 * @property spaceRentersToAdd Map of space renter IDs to pairs of SpaceRenter objects and their
 *   associated metadata, representing space renters to be created or updated when online.
 * @property loadedPins Map of pin IDs to GeoPinWithLocation objects, representing map pins that
 *   have been loaded and cached for offline viewing.
 */
data class OfflineMode(
    val accounts: Map<String, Pair<Account, Map<String, Any>>>,
    val discussions: Map<String, Pair<Discussion, List<Message>>>,
    val posts: Map<String, Pair<Post, Boolean>>,
    val shopsToAdd: Map<String, Pair<Shop, Map<String, Any>>>,
    val spaceRentersToAdd: Map<String, Pair<SpaceRenter, Map<String, Any>>>,
    val loadedPins: Map<String, GeoPinWithLocation>
)
