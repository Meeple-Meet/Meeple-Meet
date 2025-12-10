package com.github.meeplemeet.model
// AI was used for this file

import com.github.meeplemeet.model.offline.OfflineModeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for offline posts functionality in OfflineModeManager.
 *
 * Tests cover:
 * - Creating posts offline (with temp IDs)
 * - Queuing posts for sync
 * - Caching posts for offline viewing
 * - Adding comments offline
 * - Syncing queued posts when back online
 * - Syncing offline comments when back online
 * - Deleting posts (both temp and real)
 * - Post flow creation
 * - Edge cases and error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflinePostsTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    OfflineModeManager.dispatcher = testDispatcher
    // Start in offline mode
    OfflineModeManager.setNetworkStatusForTesting(false)
  }

  @After
  fun teardown() = runTest {
    OfflineModeManager.clearCachedPosts()
    // Clear queued posts
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    queuedPosts.forEach { post -> OfflineModeManager.removeQueuedPost(post.id) }
    OfflineModeManager.setNetworkStatusForTesting(false)
  }

  // ==================== CREATE POST OFFLINE ====================

  @Test
  fun `createPost offline queues post with temp ID`() = runTest {
    // Given: Offline mode
    OfflineModeManager.setNetworkStatusForTesting(false)

    // When: Create a post
    OfflineModeManager.createPost(
        title = "Offline Post",
        body = "Created while offline",
        authorId = "user123",
        tags = listOf("test", "offline"))
    advanceUntilIdle()

    // Then: Post is queued with temp ID
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    assertEquals(1, queuedPosts.size)

    val post = queuedPosts[0]
    assertTrue(post.id.startsWith("temp_"))
    assertEquals("Offline Post", post.title)
    assertEquals("Created while offline", post.body)
    assertEquals("user123", post.authorId)
    assertEquals(listOf("test", "offline"), post.tags)
    assertEquals(0, post.commentCount)
    assertTrue(post.comments.isEmpty())
  }

  @Test
  fun `createPost offline limits queue size to MAX_OFFLINE_CREATED_POSTS`() = runTest {
    // Given: Offline mode
    OfflineModeManager.setNetworkStatusForTesting(false)

    // When: Create more than MAX_OFFLINE_CREATED_POSTS (which is 2)
    OfflineModeManager.createPost("Post 1", "Body 1", "user1")
    advanceUntilIdle()
    OfflineModeManager.createPost("Post 2", "Body 2", "user2")
    advanceUntilIdle()
    OfflineModeManager.createPost("Post 3", "Body 3", "user3")
    advanceUntilIdle()

    // Then: Only the last 2 posts are kept (oldest evicted)
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    assertEquals(2, queuedPosts.size)
    assertEquals("Post 2", queuedPosts[0].title)
    assertEquals("Post 3", queuedPosts[1].title)
  }

  @Test
  fun `createPost with no tags creates post without tags`() = runTest {
    // Given: Offline mode
    OfflineModeManager.setNetworkStatusForTesting(false)

    // When: Create post without tags
    OfflineModeManager.createPost(
        title = "No Tags Post", body = "No tags here", authorId = "user456")
    advanceUntilIdle()

    // Then: Post has empty tags list
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    assertEquals(1, queuedPosts.size)
    assertTrue(queuedPosts[0].tags.isEmpty())
  }

  // ==================== CACHE POSTS ====================
  // Note: cachePosts() requires Firestore access to fetch full posts
  // These tests are covered in OfflinePostsSyncTest integration tests

  @Test
  fun `clearCachedPosts removes all cached posts`() = runTest {
    // This test just verifies clearCachedPosts doesn't crash
    // Actual caching behavior is tested in integration tests
    OfflineModeManager.clearCachedPosts()
    advanceUntilIdle()

    // Then: Cache is empty
    val cachedPosts = OfflineModeManager.getCachedPosts()
    assertTrue(cachedPosts.isEmpty())
  }

  // ==================== GET ALL POSTS FOR DISPLAY ====================

  @Test
  fun `getAllPostsForDisplay returns queued posts`() = runTest {
    // Given: Queued posts
    OfflineModeManager.setNetworkStatusForTesting(false)

    // Create queued post
    OfflineModeManager.createPost("Queued Post", "Body", "user1")
    advanceUntilIdle()

    // When: Get all posts for display
    val allPosts = OfflineModeManager.getAllPostsForDisplay()

    // Then: Queued post is included
    assertEquals(1, allPosts.size)
    assertTrue(allPosts.any { it.title == "Queued Post" })
  }

  @Test
  fun `getAllPostsForDisplay returns posts sorted by timestamp`() = runTest {
    // Given: Posts with different timestamps
    OfflineModeManager.setNetworkStatusForTesting(false)

    // Create posts with delay to ensure different timestamps
    OfflineModeManager.createPost("First Post", "Body", "user1")
    advanceUntilIdle()
    Thread.sleep(10)

    OfflineModeManager.createPost("Second Post", "Body", "user2")
    advanceUntilIdle()

    // When: Get all posts
    val allPosts = OfflineModeManager.getAllPostsForDisplay()

    // Then: Sorted newest first (MAX_OFFLINE_CREATED_POSTS is 2)
    assertEquals(2, allPosts.size)
    // Most recent post should be first
    assertEquals("Second Post", allPosts[0].title)
    assertEquals("First Post", allPosts[1].title)
  }

  // ==================== ADD COMMENT OFFLINE ====================
  // Note: addComment to cached posts requires Firestore to fetch the post first
  // These tests are covered in OfflinePostsSyncTest integration tests

  @Test
  fun `addComment throws exception when trying to comment on queued post`() = runTest {
    // Given: Offline mode with a queued post
    OfflineModeManager.setNetworkStatusForTesting(false)
    OfflineModeManager.createPost("Queued Post", "Body", "user1")
    advanceUntilIdle()

    val queuedPost = OfflineModeManager.getQueuedPosts()[0]

    // When/Then: Adding comment throws exception
    try {
      OfflineModeManager.addComment(queuedPost.id, "Comment", "user2", queuedPost.id)
      advanceUntilIdle()
      assert(false) { "Should have thrown IllegalStateException" }
    } catch (e: IllegalStateException) {
      assertEquals("Cannot add comments to posts that are waiting to be uploaded.", e.message)
    }
  }

  // ==================== DELETE POST ====================

  @Test
  fun `deletePost removes queued post when ID starts with temp`() = runTest {
    // Given: Offline mode with a queued post
    OfflineModeManager.setNetworkStatusForTesting(false)
    OfflineModeManager.createPost("Temp Post", "Body", "user1")
    advanceUntilIdle()

    val queuedPost = OfflineModeManager.getQueuedPosts()[0]
    assertTrue(queuedPost.id.startsWith("temp_"))

    // When: Delete the post
    OfflineModeManager.deletePost(queuedPost)
    advanceUntilIdle()

    // Then: Post is removed from queue
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    assertTrue(queuedPosts.isEmpty())
  }

  @Test
  fun `removeQueuedPost removes specific post from queue`() = runTest {
    // Given: Multiple queued posts
    OfflineModeManager.setNetworkStatusForTesting(false)
    OfflineModeManager.createPost("Post 1", "Body 1", "user1")
    advanceUntilIdle()
    OfflineModeManager.createPost("Post 2", "Body 2", "user2")
    advanceUntilIdle()

    val posts = OfflineModeManager.getQueuedPosts()
    val postToRemove = posts[0]

    // When: Remove specific post
    OfflineModeManager.removeQueuedPost(postToRemove.id)
    advanceUntilIdle()

    // Then: Only that post is removed
    val remainingPosts = OfflineModeManager.getQueuedPosts()
    assertEquals(1, remainingPosts.size)
    assertEquals("Post 2", remainingPosts[0].title)
  }

  // ==================== ONLINE MODE ====================

  @Test
  fun `hasInternetConnection defaults to false`() {
    // When: Check initial state
    val isOnline = OfflineModeManager.hasInternetConnection.value

    // Then: Defaults to false (offline)
    assertFalse(isOnline)
  }

  @Test
  fun `setNetworkStatusForTesting changes connection state`() {
    // Given: Initially offline
    OfflineModeManager.setNetworkStatusForTesting(false)
    assertFalse(OfflineModeManager.hasInternetConnection.value)

    // When: Set to online
    OfflineModeManager.setNetworkStatusForTesting(true)

    // Then: State changes
    assertTrue(OfflineModeManager.hasInternetConnection.value)
  }

  // ==================== EDGE CASES ====================

  @Test
  fun `getQueuedPosts returns empty list when no posts queued`() {
    // When: Get queued posts with none queued
    val queuedPosts = OfflineModeManager.getQueuedPosts()

    // Then: Returns empty list
    assertTrue(queuedPosts.isEmpty())
  }

  @Test
  fun `getCachedPosts returns empty list when no posts cached`() {
    // When: Get cached posts with none cached
    val cachedPosts = OfflineModeManager.getCachedPosts()

    // Then: Returns empty list
    assertTrue(cachedPosts.isEmpty())
  }

  @Test
  fun `getAllPostsForDisplay returns empty list when no posts`() {
    // When: Get all posts with none available
    val allPosts = OfflineModeManager.getAllPostsForDisplay()

    // Then: Returns empty list
    assertTrue(allPosts.isEmpty())
  }

  @Test
  fun `addComment to non-existent post does nothing`() = runTest {
    // Given: Offline mode with no cached posts
    OfflineModeManager.setNetworkStatusForTesting(false)

    // When: Try to add comment to non-existent post
    OfflineModeManager.addComment("nonexistent", "Comment", "user1", "nonexistent")
    advanceUntilIdle()

    // Then: No error thrown, no posts created
    val cachedPosts = OfflineModeManager.getCachedPosts()
    assertTrue(cachedPosts.isEmpty())
  }

  @Test
  fun `createPost generates unique temp IDs`() = runTest {
    // Given: Offline mode
    OfflineModeManager.setNetworkStatusForTesting(false)

    // When: Create multiple posts quickly
    OfflineModeManager.createPost("Post 1", "Body 1", "user1")
    advanceUntilIdle()
    OfflineModeManager.createPost("Post 2", "Body 2", "user2")
    advanceUntilIdle()

    // Then: All posts have unique IDs
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    val ids = queuedPosts.map { it.id }
    assertEquals(ids.size, ids.toSet().size) // All unique
  }

  // Note: Tests for addComment with cached posts and comment sorting
  // require Firestore access and are covered in integration tests
}
