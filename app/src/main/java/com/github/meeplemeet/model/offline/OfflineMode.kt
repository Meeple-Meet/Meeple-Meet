package com.github.meeplemeet.model.offline
// AI was used on this fille

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.map.GeoPinWithLocation
import com.github.meeplemeet.model.posts.Post
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.space_renter.SpaceRenter

const val MAX_CACHED_DISCUSSIONS = 50

/**
 * Maximum number of accounts to cache in offline mode.
 *
 * When this limit is exceeded, the oldest accounts (in insertion order) are evicted from the cache
 * and their profile pictures are deleted from local storage to free up space.
 */
const val MAX_CACHED_ACCOUNTS = 50

/**
 * Maximum number of posts to cache in offline mode.
 *
 * When this limit is exceeded, the oldest posts (in LRU order) are evicted from the cache.
 */
const val MAX_CACHED_POSTS = 3

/**
 * Maximum number of offline-created posts that can be queued for upload.
 *
 * When connectivity is restored, these posts are uploaded to Firestore.
 */
const val MAX_OFFLINE_CREATED_POSTS = 2

const val LOAD_FACTOR = 0.9f

/**
 * Represents the offline mode state for the application, caching data for offline access.
 *
 * This data class holds cached entities that have been loaded from the repository, allowing the
 * application to function with limited connectivity. LinkedHashMaps are configured with accessOrder
 * = true to enable proper LRU (Least Recently Used) cache eviction when size limits are enforced.
 *
 * @property accounts Map of account IDs to pairs of Account objects and their associated metadata.
 *   LinkedHashMap maintains access order (LRU) for cache eviction.
 * @property discussions Map of discussion IDs to pairs of Discussion objects and their cached
 *   messages. LinkedHashMap maintains access order (LRU) for cache eviction.
 * @property posts Map of post IDs to cached Post objects. Posts are cached for offline viewing.
 *   LinkedHashMap maintains LRU access order.
 * @property postsToAdd Map of temporary IDs to Post objects created offline. These are uploaded
 *   when connectivity is restored. LinkedHashMap maintains access order (LRU) for cache eviction.
 * @property shopsToAdd Map of shop IDs to pairs of Shop objects and their associated metadata.
 *   LinkedHashMap maintains access order (LRU) for cache eviction.
 * @property spaceRentersToAdd Map of space renter IDs to pairs of SpaceRenter objects and their
 *   associated metadata. LinkedHashMap maintains access order (LRU) for cache eviction.
 * @property loadedPins Map of pin IDs to GeoPinWithLocation objects, representing map pins that
 *   have been loaded and cached for offline viewing. LinkedHashMap maintains access order (LRU) for
 *   cache eviction.
 */
data class OfflineMode(
    val accounts: LinkedHashMap<String, Pair<Account, Map<String, Any>>> =
        LinkedHashMap(MAX_CACHED_ACCOUNTS, LOAD_FACTOR, true),
    val posts: LinkedHashMap<String, Post> = LinkedHashMap(MAX_CACHED_POSTS, LOAD_FACTOR, true),
    val postsToAdd: LinkedHashMap<String, Post> =
        LinkedHashMap(MAX_OFFLINE_CREATED_POSTS, LOAD_FACTOR, true),
    val discussions: LinkedHashMap<String, Triple<Discussion, List<Message>, List<Message>>> =
        LinkedHashMap(MAX_CACHED_DISCUSSIONS, LOAD_FACTOR, true),
    val shopsToAdd: LinkedHashMap<String, Pair<Shop, Map<String, Any>>> =
        LinkedHashMap(16, LOAD_FACTOR, true),
    val spaceRentersToAdd: LinkedHashMap<String, Pair<SpaceRenter, Map<String, Any>>> =
        LinkedHashMap(16, LOAD_FACTOR, true),
    val loadedPins: LinkedHashMap<String, GeoPinWithLocation> =
        LinkedHashMap(16, LOAD_FACTOR, true),
)
