package com.github.meeplemeet.integration

import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for offline posts synchronization with Firestore.
 *
 * Tests the complete offline-to-online flow:
 * - Creating posts offline
 * - Adding comments offline
 * - Syncing queued posts when back online
 * - Syncing offline comments when back online
 * - Proper ID mapping from temp IDs to real Firestore IDs
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflinePostsSyncTest : FirestoreTests() {
  @get:Rule val ck = Checkpoint.rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  @Before
  fun setup() {
    // Start in offline mode
    OfflineModeManager.setInternetConnection(false)
  }

  @After
  fun cleanup() = runBlocking {
    // Clear offline state
    OfflineModeManager.clearCachedPosts()
    val queuedPosts = OfflineModeManager.getQueuedPosts()
    queuedPosts.forEach { post -> OfflineModeManager.removeQueuedPost(post.id) }
  }

  @Test
  fun syncQueuedPostsUploadsOfflinePostsToFirestore() = runTest {
    checkpoint("Create posts offline") {
      runBlocking {
        // Given: Offline mode with queued posts
        OfflineModeManager.setInternetConnection(false)

        OfflineModeManager.createPost(
            title = "Offline Post 1",
            body = "Created while offline",
            authorId = "user123",
            tags = listOf("offline", "test"))

        OfflineModeManager.createPost(
            title = "Offline Post 2",
            body = "Another offline post",
            authorId = "user456",
            tags = listOf("offline"))

        delay(500)

        // Verify posts are queued
        val queuedPosts = OfflineModeManager.getQueuedPosts()
        assertEquals(2, queuedPosts.size)
        assertTrue(queuedPosts.all { it.id.startsWith("temp_") })
      }
    }

    checkpoint("Sync posts when back online") {
      runBlocking {
        // When: Come back online and sync
        OfflineModeManager.setInternetConnection(true)
        OfflineModeManager.syncQueuedPosts()
        delay(1500) // Wait for sync

        // Then: Posts are uploaded to Firestore
        val allPosts = postRepository.getPosts()
        assertTrue(allPosts.size >= 2)

        val uploadedPost1 = allPosts.find { it.title == "Offline Post 1" }
        val uploadedPost2 = allPosts.find { it.title == "Offline Post 2" }

        assertNotNull(uploadedPost1)
        assertNotNull(uploadedPost2)

        // Verify posts have real Firestore IDs (not temp)
        assertFalse(uploadedPost1!!.id.startsWith("temp_"))
        assertFalse(uploadedPost2!!.id.startsWith("temp_"))

        // Verify post content
        assertEquals("Created while offline", uploadedPost1.body)
        assertEquals("user123", uploadedPost1.authorId)
        assertEquals(listOf("offline", "test"), uploadedPost1.tags)

        // Verify queue is cleared
        val remainingQueued = OfflineModeManager.getQueuedPosts()
        assertTrue(remainingQueued.isEmpty())
      }
    }
  }

  @Test
  fun syncOfflineCommentsSyncsCommentsAndMapsIDs() = runTest {
    var createdPostId: String = ""

    checkpoint("Create post online") {
      runBlocking {
        // Given: Create a post online first
        OfflineModeManager.setInternetConnection(true)
        val post =
            postRepository.createPost(
                title = "Test Post", content = "For offline comments", authorId = "user123")
        createdPostId = post.id
        delay(1000)
      }
    }

    checkpoint("Add comments offline") {
      runBlocking {
        // When: Go offline and fetch the post, then add comments
        OfflineModeManager.setInternetConnection(false)

        // Fetch the post from Firestore while online to cache it
        OfflineModeManager.setInternetConnection(true)
        val postWithData = postRepository.getPost(createdPostId)
        OfflineModeManager.cachePosts(listOf(postWithData))
        delay(1000)

        // Now go offline
        OfflineModeManager.setInternetConnection(false)

        // Add top-level comments
        OfflineModeManager.addComment(
            postId = createdPostId,
            text = "Offline comment 1",
            authorId = "user456",
            parentId = createdPostId)

        OfflineModeManager.addComment(
            postId = createdPostId,
            text = "Offline comment 2",
            authorId = "user789",
            parentId = createdPostId)

        delay(500)

        // Verify comments are added with temp IDs
        val cachedPosts = OfflineModeManager.getCachedPosts()
        assertTrue("No posts cached", cachedPosts.isNotEmpty())
        val updatedPost = cachedPosts.find { it.id == createdPostId }
        assertNotNull("Post not found in cache", updatedPost)
        assertEquals(2, updatedPost!!.comments.size)
        assertTrue(updatedPost.comments.all { it.id.startsWith("temp_comment_") })
      }
    }

    checkpoint("Sync comments when back online") {
      runBlocking {
        // When: Come back online and sync comments
        OfflineModeManager.setInternetConnection(true)

        OfflineModeManager.syncOfflineComments()
        delay(1500) // Wait for sync

        // Then: Comments are synced to Firestore
        val syncedPost = postRepository.getPost(createdPostId)

        assertEquals(2, syncedPost.comments.size)

        // Verify comments have real Firestore IDs (not temp)
        assertFalse(syncedPost.comments[0].id.startsWith("temp_comment_"))
        assertFalse(syncedPost.comments[1].id.startsWith("temp_comment_"))

        // Verify comment content
        val commentTexts = syncedPost.comments.map { it.text }
        assertTrue(commentTexts.contains("Offline comment 1"))
        assertTrue(commentTexts.contains("Offline comment 2"))

        // Verify cache is cleared after sync
        val cachedPosts = OfflineModeManager.getCachedPosts()
        assertTrue(cachedPosts.isEmpty())
      }
    }
  }

  @Test
  fun syncNestedOfflineCommentsPreservesHierarchy() = runTest {
    var createdPostId: String = ""
    var parentCommentId: String = ""

    checkpoint("Create post and add parent comment online") {
      runBlocking {
        // Given: Post with a parent comment created online
        OfflineModeManager.setInternetConnection(true)
        val post =
            postRepository.createPost(
                title = "Nested Comments Post",
                content = "Testing nested comments",
                authorId = "user123")
        createdPostId = post.id

        parentCommentId =
            postRepository.addComment(
                postId = post.id, text = "Parent comment", authorId = "user456", parentId = post.id)

        delay(1000)

        // Fetch and cache the post with parent comment
        val postWithComment = postRepository.getPost(post.id)
        OfflineModeManager.cachePosts(listOf(postWithComment))
        delay(1000)
      }
    }

    checkpoint("Add reply offline") {
      runBlocking {
        // When: Go offline and add reply to parent comment
        OfflineModeManager.setInternetConnection(false)

        val cachedPosts = OfflineModeManager.getCachedPosts()
        assertTrue("No posts cached", cachedPosts.isNotEmpty())
        val cachedPost = cachedPosts.find { it.id == createdPostId }
        assertNotNull("Post not found in cache", cachedPost)
        val parentComment = cachedPost!!.comments[0]

        // Add reply to parent comment
        OfflineModeManager.addComment(
            postId = cachedPost.id,
            text = "Offline reply to parent",
            authorId = "user789",
            parentId = parentComment.id)

        delay(500)

        // Verify reply is nested under parent
        val updatedPost = OfflineModeManager.getCachedPosts().find { it.id == createdPostId }
        assertNotNull("Updated post not found", updatedPost)
        assertEquals(1, updatedPost!!.comments.size)
        assertEquals(1, updatedPost.comments[0].children.size)
        assertEquals("Offline reply to parent", updatedPost.comments[0].children[0].text)
      }
    }

    checkpoint("Sync nested comments") {
      runBlocking {
        // When: Come back online and sync
        OfflineModeManager.setInternetConnection(true)

        OfflineModeManager.syncOfflineComments()
        delay(1500)

        // Then: Nested structure is preserved in Firestore
        val syncedPost = postRepository.getPost(createdPostId)

        assertEquals(1, syncedPost.comments.size)
        assertEquals("Parent comment", syncedPost.comments[0].text)

        // Verify reply is nested correctly
        assertEquals(1, syncedPost.comments[0].children.size)
        assertEquals("Offline reply to parent", syncedPost.comments[0].children[0].text)

        // All IDs are real Firestore IDs
        assertFalse(syncedPost.comments[0].id.startsWith("temp_comment_"))
        assertFalse(syncedPost.comments[0].children[0].id.startsWith("temp_comment_"))
      }
    }
  }

  @Test
  fun syncHandlesMultipleNestedLevels() = runTest {
    var createdPostId: String = ""

    checkpoint("Setup post with comment hierarchy") {
      runBlocking {
        // Given: Create post online
        OfflineModeManager.setInternetConnection(true)
        val post =
            postRepository.createPost(
                title = "Deep Nesting",
                content = "Testing deep comment nesting",
                authorId = "user1")
        createdPostId = post.id

        // Add level 1 comment online
        val level1Id = postRepository.addComment(post.id, "Level 1", "user2", post.id)
        delay(500)

        // Fetch and cache post
        val postWithL1 = postRepository.getPost(post.id)
        OfflineModeManager.cachePosts(listOf(postWithL1))
        delay(1000)
      }
    }

    checkpoint("Add nested replies offline") {
      runBlocking {
        // When: Go offline and add nested replies
        OfflineModeManager.setInternetConnection(false)

        val cachedPosts = OfflineModeManager.getCachedPosts()
        assertTrue("No posts cached", cachedPosts.isNotEmpty())
        val cachedPost = cachedPosts.find { it.id == createdPostId }
        assertNotNull("Post not found in cache", cachedPost)
        val level1Comment = cachedPost!!.comments[0]

        // Add level 2 reply offline
        OfflineModeManager.addComment(cachedPost.id, "Level 2 offline", "user3", level1Comment.id)
        delay(500)

        // Get updated post with level 2
        val postWithL2 = OfflineModeManager.getCachedPosts().find { it.id == createdPostId }
        assertNotNull("Post with L2 not found", postWithL2)
        val level2Comment = postWithL2!!.comments[0].children[0]

        // Add level 3 reply offline
        OfflineModeManager.addComment(cachedPost.id, "Level 3 offline", "user4", level2Comment.id)
        delay(500)

        // Verify 3-level nesting locally
        val finalPost = OfflineModeManager.getCachedPosts().find { it.id == createdPostId }
        assertNotNull("Final post not found", finalPost)
        assertEquals(1, finalPost!!.comments.size)
        assertEquals(1, finalPost.comments[0].children.size)
        assertEquals(1, finalPost.comments[0].children[0].children.size)
      }
    }

    checkpoint("Sync preserves all nesting levels") {
      runBlocking {
        // When: Sync to Firestore
        OfflineModeManager.setInternetConnection(true)

        OfflineModeManager.syncOfflineComments()
        delay(1500)

        // Then: All nesting levels preserved
        val syncedPost = postRepository.getPost(createdPostId)

        assertEquals("Level 1", syncedPost.comments[0].text)
        assertEquals("Level 2 offline", syncedPost.comments[0].children[0].text)
        assertEquals("Level 3 offline", syncedPost.comments[0].children[0].children[0].text)

        // All have real IDs
        assertFalse(syncedPost.comments[0].id.startsWith("temp_"))
        assertFalse(syncedPost.comments[0].children[0].id.startsWith("temp_"))
        assertFalse(syncedPost.comments[0].children[0].children[0].id.startsWith("temp_"))
      }
    }
  }

  @Test
  fun syncQueuedPostsDoesNothingWhenOffline() = runTest {
    checkpoint("Create posts offline") {
      runBlocking {
        // Given: Offline with queued posts
        OfflineModeManager.setInternetConnection(false)
        OfflineModeManager.createPost("Offline Post", "Body", "user1")
        delay(500)

        assertEquals(1, OfflineModeManager.getQueuedPosts().size)
      }
    }

    checkpoint("Try to sync while still offline") {
      runBlocking {
        // When: Try to sync while offline
        OfflineModeManager.syncQueuedPosts()
        delay(500)

        // Then: Posts remain queued (not synced)
        assertEquals(1, OfflineModeManager.getQueuedPosts().size)
      }
    }
  }

  @Test
  fun syncOfflineCommentsDoesNothingWhenOffline() = runTest {
    var createdPostId: String = ""

    checkpoint("Setup") {
      runBlocking {
        // Given: Post with offline comments
        OfflineModeManager.setInternetConnection(true)
        val post = postRepository.createPost("Post", "Body", "user1")
        createdPostId = post.id

        // Fetch and cache it
        val postData = postRepository.getPost(post.id)
        OfflineModeManager.cachePosts(listOf(postData))
        delay(1000)

        OfflineModeManager.setInternetConnection(false)
        OfflineModeManager.addComment(post.id, "Offline comment", "user2", post.id)
        delay(500)

        val cachedPosts = OfflineModeManager.getCachedPosts()
        assertTrue("No cached posts", cachedPosts.isNotEmpty())
        val cachedPost = cachedPosts.find { it.id == createdPostId }
        assertNotNull("Post not found", cachedPost)
        assertEquals(1, cachedPost!!.comments.size)
      }
    }

    checkpoint("Try to sync while offline") {
      runBlocking {
        // When: Try to sync while offline
        OfflineModeManager.syncOfflineComments()
        delay(500)

        // Then: Comments remain cached (not synced)
        val cachedPosts = OfflineModeManager.getCachedPosts()
        assertTrue("No cached posts after sync", cachedPosts.isNotEmpty())
        val cachedPost = cachedPosts.find { it.id == createdPostId }
        assertNotNull("Post not found after sync", cachedPost)
        assertEquals(1, cachedPost!!.comments.size)
        assertTrue(cachedPost.comments[0].id.startsWith("temp_comment_"))
      }
    }
  }

  @Test
  fun cachePostsFetchesFullPostsWithComments() = runTest {
    var createdPostId: String = ""

    checkpoint("Create post with comments online") {
      runBlocking {
        // Given: Post with comments in Firestore
        OfflineModeManager.setInternetConnection(true)
        val post = postRepository.createPost("Full Post", "Body", "user1")
        createdPostId = post.id
        postRepository.addComment(post.id, "Comment 1", "user2", post.id)
        postRepository.addComment(post.id, "Comment 2", "user3", post.id)
        delay(500)
      }
    }

    checkpoint("Cache posts fetches with comments") {
      runBlocking {
        // When: Fetch and cache the post
        val fullPost = postRepository.getPost(createdPostId)
        OfflineModeManager.cachePosts(listOf(fullPost))
        delay(1000) // Wait for cachePosts coroutine to complete

        // Then: Cached post includes comments
        val cachedPosts = OfflineModeManager.getCachedPosts()
        val cachedPost = cachedPosts.find { it.title == "Full Post" }

        assertNotNull("Post not found in cache", cachedPost)
        assertEquals(2, cachedPost!!.comments.size)
      }
    }
  }
}
