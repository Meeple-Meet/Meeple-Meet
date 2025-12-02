package com.github.meeplemeet.integration

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.posts.CreatePostViewModel
import com.github.meeplemeet.model.posts.PostOverviewViewModel
import com.github.meeplemeet.model.posts.PostViewModel
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePostTests : FirestoreTests() {
  @get:Rule val ck = Checkpoint.rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var createVM: CreatePostViewModel
  private lateinit var postVM: PostViewModel
  private lateinit var overviewVM: PostOverviewViewModel

  private val testAccount1 =
      Account(uid = "user1", handle = "alice", name = "Alice", email = "alice@test.com")
  private val testAccount2 =
      Account(uid = "user2", handle = "bob", name = "Bob", email = "bob@test.com")

  @Before
  fun setup() {
    createVM = CreatePostViewModel()
    postVM = PostViewModel()
    overviewVM = PostOverviewViewModel()
  }

  @Test
  fun smoke_createAndDeletePost() = runTest {
    checkpoint("Create post with tags") {
      runBlocking {
        val post =
            postRepository.createPost(
                "Test Title", "Test Content", testAccount1.uid, listOf("tag1", "tag2"))
        assertNotNull(post.id)
        assertEquals("Test Title", post.title)
        assertEquals("Test Content", post.body)
        assertEquals(testAccount1.uid, post.authorId)
        assertEquals(listOf("tag1", "tag2"), post.tags)
        assertTrue(post.comments.isEmpty())
      }
    }

    checkpoint("Create post without tags") {
      runBlocking {
        val post = postRepository.createPost("No Tags", "Content", testAccount1.uid)
        assertNotNull(post.id)
        assertEquals("No Tags", post.title)
        assertEquals("Content", post.body)
        assertEquals(testAccount1.uid, post.authorId)
        assertTrue(post.tags.isEmpty())
      }
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun testDeletePost() = runTest {
    val post = postRepository.createPost("To Delete", "Content", testAccount1.uid)
    postRepository.deletePost(post.id)
    runBlocking { postRepository.getPost(post.id) }
  }

  @Test
  fun smoke_comments() = runTest {
    checkpoint("Add top-level comment") {
      runBlocking {
        val post = postRepository.createPost("Post with Comment", "Content", testAccount1.uid)
        val commentId =
            postRepository.addComment(post.id, "Top level comment", testAccount2.uid, post.id)
        assertNotNull(commentId)
        val updatedPost = postRepository.getPost(post.id)
        assertEquals(1, updatedPost.comments.size)
        assertEquals("Top level comment", updatedPost.comments[0].text)
        assertEquals(testAccount2.uid, updatedPost.comments[0].authorId)
      }
    }

    checkpoint("Add reply to comment") {
      runBlocking {
        val post = postRepository.createPost("Post with Reply", "Content", testAccount1.uid)
        val parentCommentId =
            postRepository.addComment(post.id, "Parent comment", testAccount2.uid, post.id)
        val replyId =
            postRepository.addComment(
                post.id, "Reply to comment", testAccount1.uid, parentCommentId)
        assertNotNull(replyId)
        delay(1000)
        val updatedPost = postRepository.getPost(post.id)
        assertEquals(1, updatedPost.comments.size)
        val parentComment = updatedPost.comments[0]
        assertEquals("Parent comment", parentComment.text)
        assertEquals(1, parentComment.children.size)
        assertEquals("Reply to comment", parentComment.children[0].text)
        assertEquals(testAccount1.uid, parentComment.children[0].authorId)
      }
    }

    checkpoint("Remove comment") {
      runBlocking {
        val post = postRepository.createPost("Post", "Content", testAccount1.uid)
        val commentId = postRepository.addComment(post.id, "To remove", testAccount2.uid, post.id)
        postRepository.removeComment(post.id, commentId)
        val updatedPost = postRepository.getPost(post.id)
        assertTrue(updatedPost.comments[0].authorId.isEmpty())
      }
    }

    checkpoint("Remove comment with replies") {
      runBlocking {
        val post = postRepository.createPost("Post", "Content", testAccount1.uid)
        val parentCommentId =
            postRepository.addComment(post.id, "Parent", testAccount2.uid, post.id)
        postRepository.addComment(post.id, "Reply 1", testAccount1.uid, parentCommentId)
        postRepository.addComment(post.id, "Reply 2", testAccount2.uid, parentCommentId)
        val withComments = postRepository.getPost(post.id)
        postRepository.removeComment(post.id, parentCommentId)
        val updatedPost = postRepository.getPost(post.id)
        assertEquals(withComments.comments.size, updatedPost.comments.size)
        assertNotEquals(withComments.comments[0], updatedPost.comments[0])
      }
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun testRemoveNonExistentComment() = runTest {
    val post = postRepository.createPost("Post", "Content", testAccount1.uid)
    runBlocking { postRepository.removeComment(post.id, "nonexistent") }
  }

  @Test
  fun testGetPostReturnsCompletePostWithComments() = runBlocking {
    val post = postRepository.createPost("Get Test", "Content", testAccount1.uid, listOf("test"))
    val commentId1 = postRepository.addComment(post.id, "Comment 1", testAccount2.uid, post.id)
    postRepository.addComment(post.id, "Reply to Comment 1", testAccount1.uid, commentId1)
    postRepository.addComment(post.id, "Comment 2", testAccount2.uid, post.id)

    val retrievedPost = postRepository.getPost(post.id)

    assertEquals(post.id, retrievedPost.id)
    assertEquals("Get Test", retrievedPost.title)
    assertEquals("Content", retrievedPost.body)
    assertEquals(testAccount1.uid, retrievedPost.authorId)
    assertEquals(listOf("test"), retrievedPost.tags)
    assertEquals(2, retrievedPost.comments.size)

    val commentWithChild = retrievedPost.comments.find { it.children.isNotEmpty() }
    assertNotNull(commentWithChild)
    assertEquals(1, commentWithChild!!.children.size)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testDeletePostWithComments() = runTest {
    val post = postRepository.createPost("Post with Comments", "Content", testAccount1.uid)
    postRepository.addComment(post.id, "Comment 1", testAccount2.uid, post.id)
    postRepository.addComment(post.id, "Comment 2", testAccount1.uid, post.id)

    // Delete the post with all its comments
    postRepository.deletePost(post.id)

    // Verify post no longer exists
    runBlocking { postRepository.getPost(post.id) }
  }

  @Test(expected = IllegalArgumentException::class)
  fun testGetNonExistentPost() = runTest {
    runBlocking { postRepository.getPost("nonexistent-post-id") }
  }

  @Test(expected = IllegalArgumentException::class)
  fun testCreatePostViewModelWithBlankTitle() = runBlocking {
    createVM.createPost("", "Content", testAccount1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testCreatePostViewModelWithBlankBody() = runBlocking {
    createVM.createPost("Title", "", testAccount1)
  }

  @Test
  fun testDeletePostAsAuthor() = runTest {
    val post = postRepository.createPost("To Delete", "Content", testAccount1.uid)
    postVM.deletePost(testAccount1, post)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun testDeletePostAsNonAuthor() = runTest {
    val post = postRepository.createPost("Protected Post", "Content", testAccount1.uid)

    postVM.deletePost(testAccount2, post)
  }

  @Test
  fun testAddCommentToPost() = runBlocking {
    val post = postRepository.createPost("Post", "Content", testAccount1.uid)
    postVM.addComment(testAccount2, post, post.id, "VM Comment")
    delay(1000)

    val updatedPost = postRepository.getPost(post.id)
    assertEquals(1, updatedPost.comments.size)
    assertEquals("VM Comment", updatedPost.comments[0].text)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testAddBlankComment() {
    val post = runBlocking { postRepository.createPost("Post", "Content", testAccount1.uid) }

    postVM.addComment(testAccount2, post, post.id, "")
  }

  @Test
  fun testRemoveCommentAsAuthor() = runBlocking {
    val post = postRepository.createPost("Post", "Content", testAccount1.uid)
    postRepository.addComment(post.id, "Comment", testAccount2.uid, post.id)

    val postWithComment = postRepository.getPost(post.id)
    val comment = postWithComment.comments[0]

    postVM.removeComment(testAccount2, postWithComment, comment)
    delay(1000)

    val updatedPost = postRepository.getPost(post.id)
    assertNotEquals(comment, updatedPost.comments[0])
  }

  @Test(expected = PermissionDeniedException::class)
  fun testRemoveCommentAsNonAuthor() = runTest {
    val post = postRepository.createPost("Post", "Content", testAccount1.uid)
    postRepository.addComment(post.id, "Comment", testAccount2.uid, post.id)

    val postWithComment = postRepository.getPost(post.id)
    val comment = postWithComment.comments[0]

    postVM.removeComment(testAccount1, postWithComment, comment)
  }

  @Test
  fun testGetPostsFromRepository() = runTest {
    // Create multiple posts
    val post1 = postRepository.createPost("Post 1", "Content 1", testAccount1.uid, listOf("tag1"))
    val post2 = postRepository.createPost("Post 2", "Content 2", testAccount2.uid, listOf("tag2"))
    val post3 = postRepository.createPost("Post 3", "Content 3", testAccount1.uid)

    // Add comments to one of the posts
    postRepository.addComment(post1.id, "Comment on post 1", testAccount2.uid, post1.id)

    // Fetch all posts
    val posts = postRepository.getPosts()

    // Verify we get all posts
    assertTrue(posts.size >= 3)

    // Verify posts are sorted by timestamp (newest first)
    val retrievedPost3 = posts.find { it.id == post3.id }
    val retrievedPost2 = posts.find { it.id == post2.id }
    val retrievedPost1 = posts.find { it.id == post1.id }

    assertNotNull(retrievedPost1)
    assertNotNull(retrievedPost2)
    assertNotNull(retrievedPost3)

    // Verify post content
    assertEquals("Post 1", retrievedPost1!!.title)
    assertEquals("Content 1", retrievedPost1.body)
    assertEquals(testAccount1.uid, retrievedPost1.authorId)
    assertEquals(listOf("tag1"), retrievedPost1.tags)

    // Verify comments are NOT loaded (empty list)
    assertTrue(retrievedPost1.comments.isEmpty())
    assertTrue(retrievedPost2!!.comments.isEmpty())
    assertTrue(retrievedPost3!!.comments.isEmpty())
  }

  @Test
  fun testGetPostsReturnsEmptyListWhenNoPosts() = runTest {
    val posts = postRepository.getPosts()
    assertNotNull(posts)
  }

  @Test
  fun testGetPostsFromViewModel() = runTest {
    // Create test posts
    postRepository.createPost("VM Post 1", "Content 1", testAccount1.uid)
    postRepository.createPost("VM Post 2", "Content 2", testAccount2.uid)

    // Verify posts StateFlow is updated
    val posts = postRepository.getPosts()
    assertTrue(posts.size >= 2)

    // Verify posts don't have comments loaded
    posts.forEach { post -> assertTrue(post.comments.isEmpty()) }
  }

  @Test
  fun testGetPostsViewModelInitialState() = runTest {
    // Verify initial state is empty list
    val initialPosts = overviewVM.posts.value
    assertTrue(initialPosts.isEmpty())
  }

  @Test
  fun testCommentCountIncrementsWhenAddingComment() = runTest {
    val post = postRepository.createPost("Test Post", "Content", testAccount1.uid)
    assertEquals(0, post.commentCount)

    // Add first comment
    postRepository.addComment(post.id, "First comment", testAccount2.uid, post.id)
    delay(500)

    val updatedPost1 = postRepository.getPost(post.id)
    assertEquals(1, updatedPost1.commentCount)

    // Add second comment
    postRepository.addComment(post.id, "Second comment", testAccount1.uid, post.id)
    delay(500)

    val updatedPost2 = postRepository.getPost(post.id)
    assertEquals(2, updatedPost2.commentCount)
  }

  @Test
  fun testCommentCountDecrementsWhenRemovingComment() = runTest {
    val post = postRepository.createPost("Test Post", "Content", testAccount1.uid)

    // Add two comments
    val comment1Id = postRepository.addComment(post.id, "First comment", testAccount2.uid, post.id)
    postRepository.addComment(post.id, "Second comment", testAccount1.uid, post.id)
    delay(500)

    val postWith2Comments = postRepository.getPost(post.id)
    assertEquals(2, postWith2Comments.commentCount)

    // Remove one comment
    postRepository.removeComment(post.id, comment1Id)
    delay(500)

    val postWith1Comment = postRepository.getPost(post.id)
    assertEquals(1, postWith1Comment.commentCount)
  }

  @Test
  fun testCommentCountWithNestedReplies() = runTest {
    val post = postRepository.createPost("Test Post", "Content", testAccount1.uid)

    // Add parent comment
    val parentId = postRepository.addComment(post.id, "Parent", testAccount2.uid, post.id)
    delay(500)

    val postWith1Comment = postRepository.getPost(post.id)
    assertEquals(1, postWith1Comment.commentCount)

    // Add reply to parent
    postRepository.addComment(post.id, "Reply", testAccount1.uid, parentId)
    delay(500)

    val postWith2Comments = postRepository.getPost(post.id)
    assertEquals(2, postWith2Comments.commentCount)

    // Remove parent (should also remove reply but only decrement by 1)
    postRepository.removeComment(post.id, parentId)
    delay(500)

    val postWith0Comments = postRepository.getPost(post.id)
    assertEquals(1, postWith0Comments.commentCount)
  }

  @Test
  fun testCommentCountInPostStructure() = runTest {
    val post = postRepository.createPost("Test Post", "Content", testAccount1.uid)
    assertEquals(0, post.commentCount)

    // Add comments
    postRepository.addComment(post.id, "Comment 1", testAccount2.uid, post.id)
    postRepository.addComment(post.id, "Comment 2", testAccount1.uid, post.id)
    delay(500)

    // Verify commentCount field is populated correctly
    val updatedPost = postRepository.getPost(post.id)
    assertEquals(2, updatedPost.commentCount)
    assertEquals(2, updatedPost.comments.size)
  }

  @Test
  fun testGetPostsReturnsCommentCount() = runTest {
    val post = postRepository.createPost("Post with Count", "Content", testAccount1.uid)
    postRepository.addComment(post.id, "Comment", testAccount2.uid, post.id)
    delay(500)

    // Fetch posts without comments
    val posts = postRepository.getPosts()
    val foundPost = posts.find { it.id == post.id }

    assertNotNull(foundPost)
    assertEquals(1, foundPost!!.commentCount)
    assertTrue(foundPost.comments.isEmpty()) // Comments not loaded
  }

  @Test
  fun testEditPostAndComment() = runBlocking {
    // Test editing post at repository level
    val post =
        postRepository.createPost(
            "Original Title", "Original Body", testAccount1.uid, listOf("tag1"))

    // Edit all fields
    postRepository.editPost(
        post.id,
        newTitle = "Updated Title",
        newBody = "Updated Body",
        newTags = listOf("tag2", "tag3"))
    delay(500)

    val updatedPost = postRepository.getPost(post.id)
    assertEquals("Updated Title", updatedPost.title)
    assertEquals("Updated Body", updatedPost.body)
    assertEquals(listOf("tag2", "tag3"), updatedPost.tags)

    // Edit only title
    postRepository.editPost(post.id, newTitle = "Title Only")
    delay(500)
    val titleOnlyPost = postRepository.getPost(post.id)
    assertEquals("Title Only", titleOnlyPost.title)
    assertEquals("Updated Body", titleOnlyPost.body)

    // Test editing comment at repository level
    val commentId =
        postRepository.addComment(post.id, "Original Comment", testAccount2.uid, post.id)
    delay(500)

    postRepository.editComment(post.id, commentId, "Updated Comment")
    delay(500)

    val postWithUpdatedComment = postRepository.getPost(post.id)
    assertEquals("Updated Comment", postWithUpdatedComment.comments[0].text)
  }

  @Test
  fun testEditPostAndCommentWithPermissionsAndValidation() = runTest {
    // Test ViewModel edit with permissions
    val post = postRepository.createPost("Test Post", "Test Body", testAccount1.uid)

    // Edit as author (should succeed)
    postVM.editPost(testAccount1, post, newTitle = "Edited by Author")

    val editedPost = postRepository.getPost(post.id)
    assertEquals("Edited by Author", editedPost.title)

    // Try to edit as non-author (should fail)
    try {
      postVM.editPost(testAccount2, post, newTitle = "Hacked Title")
      throw AssertionError("Expected PermissionDeniedException")
    } catch (_: PermissionDeniedException) {
      // Expected
    }

    // Test blank title validation
    try {
      postVM.editPost(testAccount1, post, newTitle = "")
      throw AssertionError("Expected IllegalArgumentException for blank title")
    } catch (_: IllegalArgumentException) {
      // Expected
    }

    // Test blank body validation
    try {
      postVM.editPost(testAccount1, post, newBody = "")
      throw AssertionError("Expected IllegalArgumentException for blank body")
    } catch (_: IllegalArgumentException) {
      // Expected
    }

    // Test comment editing with permissions
    postRepository.addComment(post.id, "Test Comment", testAccount2.uid, post.id)
    val postWithComment = postRepository.getPost(post.id)
    val comment = postWithComment.comments[0]

    // Edit as comment author (should succeed)
    postVM.editComment(testAccount2, postWithComment, comment, "Edited Comment")

    val postWithEditedComment = postRepository.getPost(post.id)
    assertEquals("Edited Comment", postWithEditedComment.comments[0].text)

    // Try to edit as non-author (should fail)
    try {
      postVM.editComment(
          testAccount1, postWithEditedComment, postWithEditedComment.comments[0], "Hacked Comment")
      throw AssertionError("Expected PermissionDeniedException")
    } catch (_: PermissionDeniedException) {
      // Expected
    }

    // Test blank comment validation
    try {
      postVM.editComment(testAccount2, postWithEditedComment, postWithEditedComment.comments[0], "")
      throw AssertionError("Expected IllegalArgumentException for blank comment")
    } catch (_: IllegalArgumentException) {
      // Expected
    }
  }
}
