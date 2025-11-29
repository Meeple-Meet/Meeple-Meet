package com.github.meeplemeet.model.offline

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.map.GeoPinWithLocation
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.space_renter.SpaceRenter

/**
 * Represents the offline mode state for the application, caching data for offline access.
 *
 * This data class holds cached entities that have been loaded from the repository, allowing the
 * application to function with limited connectivity. LinkedHashMaps are used to maintain insertion
 * order, enabling LRU-style cache eviction when size limits are enforced.
 *
 * @property accounts Map of account IDs to pairs of Account objects and their associated metadata.
 *   LinkedHashMap maintains insertion order for potential cache eviction.
 * @property discussions Map of discussion IDs to pairs of Discussion objects and their cached
 *   messages. LinkedHashMap maintains insertion order for potential cache eviction.
 * @property posts Map of post IDs to pairs of Post objects and a boolean flag. LinkedHashMap
 *   maintains insertion order for potential cache eviction.
 * @property shopsToAdd Map of shop IDs to pairs of Shop objects and their associated metadata.
 *   LinkedHashMap maintains insertion order for potential cache eviction.
 * @property spaceRentersToAdd Map of space renter IDs to pairs of SpaceRenter objects and their
 *   associated metadata. LinkedHashMap maintains insertion order for potential cache eviction.
 * @property loadedPins Map of pin IDs to GeoPinWithLocation objects, representing map pins that
 *   have been loaded and cached for offline viewing. LinkedHashMap maintains insertion order for
 *   potential cache eviction.
 */
data class OfflineMode(
    val accounts: LinkedHashMap<String, Pair<Account, Map<String, Any>>> = LinkedHashMap(),
    val discussions: LinkedHashMap<String, Pair<Discussion, List<Message>>> = LinkedHashMap(),
    val posts: LinkedHashMap<String, Pair<Post, Boolean>> = LinkedHashMap(),
    val shopsToAdd: LinkedHashMap<String, Pair<Shop, Map<String, Any>>> = LinkedHashMap(),
    val spaceRentersToAdd: LinkedHashMap<String, Pair<SpaceRenter, Map<String, Any>>> =
        LinkedHashMap(),
    val loadedPins: LinkedHashMap<String, GeoPinWithLocation> = LinkedHashMap(),
)
