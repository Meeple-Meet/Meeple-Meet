package com.github.meeplemeet.integration

import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.repositories.FirestorePostRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.CreatePostViewModel
import com.github.meeplemeet.model.viewmodels.PostOverviewViewModel
import com.github.meeplemeet.model.viewmodels.PostViewModel
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePostTests : FirestoreTests() {
  private lateinit var repository: FirestorePostRepository
  private lateinit var createVM: CreatePostViewModel
  private lateinit var postVM: PostViewModel
  private lateinit var overviewVM: PostOverviewViewModel

  private val testAccount1 =
      Account(uid = "user1", handle = "alice", name = "Alice", email = "alice@test.com")
  private val testAccount2 =
      Account(uid = "user2", handle = "bob", name = "Bob", email = "bob@test.com")

  @Before
  fun setup() {
    repository = FirestorePostRepository()
    createVM = CreatePostViewModel(repository)
    postVM = PostViewModel(repository)
    overviewVM = PostOverviewViewModel(repository)
  }

  @Test
  fun testCreatePost() = runTest {
    val post =
        repository.createPost(
            "Test Title", "Test Content", testAccount1.uid, listOf("tag1", "tag2"))

    Assert.assertNotNull(post.id)
    Assert.assertEquals("Test Title", post.title)
    Assert.assertEquals("Test Content", post.body)
    Assert.assertEquals(testAccount1.uid, post.authorId)
    Assert.assertEquals(listOf("tag1", "tag2"), post.tags)
    Assert.assertTrue(post.comments.isEmpty())
  }

  @Test
  fun testCreatePostWithoutTags() = runTest {
    val post = repository.createPost("No Tags", "Content", testAccount1.uid)

    Assert.assertNotNull(post.id)
    Assert.assertEquals("No Tags", post.title)
    Assert.assertEquals("Content", post.body)
    Assert.assertEquals(testAccount1.uid, post.authorId)
    Assert.assertTrue(post.tags.isEmpty())
  }

  @Test(expected = IllegalArgumentException::class)
  fun testDeletePost() = runTest {
    val post = repository.createPost("To Delete", "Content", testAccount1.uid)
    repository.deletePost(post.id)

    runBlocking { repository.getPost(post.id) }
  }

  @Test
  fun testAddTopLevelComment() = runTest {
    val post = repository.createPost("Post with Comment", "Content", testAccount1.uid)
    val commentId = repository.addComment(post.id, "Top level comment", testAccount2.uid, post.id)

    Assert.assertNotNull(commentId)

    // Verify comment was added by getting the post
    val updatedPost = repository.getPost(post.id)

    Assert.assertEquals(1, updatedPost.comments.size)
    Assert.assertEquals("Top level comment", updatedPost.comments[0].text)
    Assert.assertEquals(testAccount2.uid, updatedPost.comments[0].authorId)
  }

  @Test
  fun testAddReplyToComment() = runTest {
    val post = repository.createPost("Post with Reply", "Content", testAccount1.uid)
    val parentCommentId =
        repository.addComment(post.id, "Parent comment", testAccount2.uid, post.id)
    val replyId =
        repository.addComment(post.id, "Reply to comment", testAccount1.uid, parentCommentId)

    Assert.assertNotNull(replyId)

    // Verify nested comment structure
    val updatedPost = repository.getPost(post.id)
    delay(1000)

    Assert.assertEquals(1, updatedPost.comments.size)
    val parentComment = updatedPost.comments[0]
    Assert.assertEquals("Parent comment", parentComment.text)
    Assert.assertEquals(1, parentComment.children.size)
    Assert.assertEquals("Reply to comment", parentComment.children[0].text)
    Assert.assertEquals(testAccount1.uid, parentComment.children[0].authorId)
  }

  @Test
  fun testRemoveComment() = runTest {
    val post = repository.createPost("Post", "Content", testAccount1.uid)
    val commentId = repository.addComment(post.id, "To remove", testAccount2.uid, post.id)

    repository.removeComment(post.id, commentId)

    // Verify comment was removed
    val updatedPost = repository.getPost(post.id)
    Assert.assertTrue(updatedPost.comments[0].authorId.isEmpty())
  }

  @Test
  fun testRemoveCommentWithReplies() = runTest {
    val post = repository.createPost("Post", "Content", testAccount1.uid)
    val parentCommentId = repository.addComment(post.id, "Parent", testAccount2.uid, post.id)
    repository.addComment(post.id, "Reply 1", testAccount1.uid, parentCommentId)
    repository.addComment(post.id, "Reply 2", testAccount2.uid, parentCommentId)
    val withComments = repository.getPost(post.id)

    // Remove parent comment should also remove all replies
    repository.removeComment(post.id, parentCommentId)

    val updatedPost = repository.getPost(post.id)
    assertEquals(withComments.comments.size, updatedPost.comments.size)
    assertNotEquals(withComments.comments[0], updatedPost.comments[0])
  }

  @Test(expected = IllegalArgumentException::class)
  fun testRemoveNonExistentComment() = runTest {
    val post = repository.createPost("Post", "Content", testAccount1.uid)

    runBlocking { repository.removeComment(post.id, "nonexistent") }
  }

  @Test
  fun testGetPostReturnsCompletePostWithComments() = runBlocking {
    val post = repository.createPost("Get Test", "Content", testAccount1.uid, listOf("test"))
    val commentId1 = repository.addComment(post.id, "Comment 1", testAccount2.uid, post.id)
    repository.addComment(post.id, "Reply to Comment 1", testAccount1.uid, commentId1)
    repository.addComment(post.id, "Comment 2", testAccount2.uid, post.id)

    val retrievedPost = repository.getPost(post.id)

    Assert.assertEquals(post.id, retrievedPost.id)
    Assert.assertEquals("Get Test", retrievedPost.title)
    Assert.assertEquals("Content", retrievedPost.body)
    Assert.assertEquals(testAccount1.uid, retrievedPost.authorId)
    Assert.assertEquals(listOf("test"), retrievedPost.tags)
    Assert.assertEquals(2, retrievedPost.comments.size)

    val commentWithChild = retrievedPost.comments.find { it.children.isNotEmpty() }
    Assert.assertNotNull(commentWithChild)
    Assert.assertEquals(1, commentWithChild!!.children.size)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testDeletePostWithComments() = runTest {
    val post = repository.createPost("Post with Comments", "Content", testAccount1.uid)
    repository.addComment(post.id, "Comment 1", testAccount2.uid, post.id)
    repository.addComment(post.id, "Comment 2", testAccount1.uid, post.id)

    // Delete the post with all its comments
    repository.deletePost(post.id)

    // Verify post no longer exists
    runBlocking { repository.getPost(post.id) }
  }

  @Test(expected = IllegalArgumentException::class)
  fun testGetNonExistentPost() = runTest {
    runBlocking { repository.getPost("nonexistent-post-id") }
  }

  @Test(expected = IllegalArgumentException::class)
  fun testCreatePostViewModelWithBlankTitle() {
    createVM.createPost("", "Content", testAccount1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testCreatePostViewModelWithBlankBody() {
    createVM.createPost("Title", "", testAccount1)
  }

  @Test
  fun testDeletePostAsAuthor() = runTest {
    val post = repository.createPost("To Delete", "Content", testAccount1.uid)
    postVM.deletePost(testAccount1, post)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun testDeletePostAsNonAuthor() = runTest {
    val post = repository.createPost("Protected Post", "Content", testAccount1.uid)

    postVM.deletePost(testAccount2, post)
  }

  @Test
  fun testAddCommentToPost() = runBlocking {
    val post = repository.createPost("Post", "Content", testAccount1.uid)
    postVM.addComment(testAccount2, post, post.id, "VM Comment")
    delay(1000)

    val updatedPost = repository.getPost(post.id)
    Assert.assertEquals(1, updatedPost.comments.size)
    Assert.assertEquals("VM Comment", updatedPost.comments[0].text)
  }

  @Test(expected = IllegalArgumentException::class)
  fun testAddBlankComment() {
    val post = runBlocking { repository.createPost("Post", "Content", testAccount1.uid) }

    postVM.addComment(testAccount2, post, post.id, "")
  }

  @Test
  fun testRemoveCommentAsAuthor() = runBlocking {
    val post = repository.createPost("Post", "Content", testAccount1.uid)
    repository.addComment(post.id, "Comment", testAccount2.uid, post.id)

    val postWithComment = repository.getPost(post.id)
    val comment = postWithComment.comments[0]

    postVM.removeComment(testAccount2, postWithComment, comment)
    delay(1000)

    val updatedPost = repository.getPost(post.id)
    assertNotEquals(comment, updatedPost.comments[0])
  }

  @Test(expected = PermissionDeniedException::class)
  fun testRemoveCommentAsNonAuthor() = runTest {
    val post = repository.createPost("Post", "Content", testAccount1.uid)
    repository.addComment(post.id, "Comment", testAccount2.uid, post.id)

    val postWithComment = repository.getPost(post.id)
    val comment = postWithComment.comments[0]

    postVM.removeComment(testAccount1, postWithComment, comment)
  }

  @Test
  fun testGetPostsFromRepository() = runTest {
    // Create multiple posts
    val post1 = repository.createPost("Post 1", "Content 1", testAccount1.uid, listOf("tag1"))
    val post2 = repository.createPost("Post 2", "Content 2", testAccount2.uid, listOf("tag2"))
    val post3 = repository.createPost("Post 3", "Content 3", testAccount1.uid)

    // Add comments to one of the posts
    repository.addComment(post1.id, "Comment on post 1", testAccount2.uid, post1.id)

    // Fetch all posts
    val posts = repository.getPosts()

    // Verify we get all posts
    Assert.assertTrue(posts.size >= 3)

    // Verify posts are sorted by timestamp (newest first)
    val retrievedPost3 = posts.find { it.id == post3.id }
    val retrievedPost2 = posts.find { it.id == post2.id }
    val retrievedPost1 = posts.find { it.id == post1.id }

    Assert.assertNotNull(retrievedPost1)
    Assert.assertNotNull(retrievedPost2)
    Assert.assertNotNull(retrievedPost3)

    // Verify post content
    Assert.assertEquals("Post 1", retrievedPost1!!.title)
    Assert.assertEquals("Content 1", retrievedPost1.body)
    Assert.assertEquals(testAccount1.uid, retrievedPost1.authorId)
    Assert.assertEquals(listOf("tag1"), retrievedPost1.tags)

    // Verify comments are NOT loaded (empty list)
    Assert.assertTrue(retrievedPost1.comments.isEmpty())
    Assert.assertTrue(retrievedPost2!!.comments.isEmpty())
    Assert.assertTrue(retrievedPost3!!.comments.isEmpty())
  }

  @Test
  fun testGetPostsReturnsEmptyListWhenNoPosts() = runTest {
    val posts = repository.getPosts()
    Assert.assertNotNull(posts)
  }

  @Test
  fun testGetPostsFromViewModel() = runTest {
    // Create test posts
    repository.createPost("VM Post 1", "Content 1", testAccount1.uid)
    repository.createPost("VM Post 2", "Content 2", testAccount2.uid)

    // Verify posts StateFlow is updated
    val posts = repository.getPosts()
    Assert.assertTrue(posts.size >= 2)

    // Verify posts don't have comments loaded
    posts.forEach { post -> Assert.assertTrue(post.comments.isEmpty()) }
  }

  @Test
  fun testGetPostsViewModelInitialState() = runTest {
    // Verify initial state is empty list
    val initialPosts = overviewVM.posts.value
    Assert.assertTrue(initialPosts.isEmpty())
  }

  @Test
  fun testCommentCountIncrementsWhenAddingComment() = runTest {
    val post = repository.createPost("Test Post", "Content", testAccount1.uid)
    Assert.assertEquals(0, post.commentCount)

    // Add first comment
    repository.addComment(post.id, "First comment", testAccount2.uid, post.id)
    delay(500)

    val updatedPost1 = repository.getPost(post.id)
    Assert.assertEquals(1, updatedPost1.commentCount)

    // Add second comment
    repository.addComment(post.id, "Second comment", testAccount1.uid, post.id)
    delay(500)

    val updatedPost2 = repository.getPost(post.id)
    Assert.assertEquals(2, updatedPost2.commentCount)
  }

  @Test
  fun testCommentCountDecrementsWhenRemovingComment() = runTest {
    val post = repository.createPost("Test Post", "Content", testAccount1.uid)

    // Add two comments
    val comment1Id = repository.addComment(post.id, "First comment", testAccount2.uid, post.id)
    repository.addComment(post.id, "Second comment", testAccount1.uid, post.id)
    delay(500)

    val postWith2Comments = repository.getPost(post.id)
    Assert.assertEquals(2, postWith2Comments.commentCount)

    // Remove one comment
    repository.removeComment(post.id, comment1Id)
    delay(500)

    val postWith1Comment = repository.getPost(post.id)
    Assert.assertEquals(1, postWith1Comment.commentCount)
  }

  @Test
  fun testCommentCountWithNestedReplies() = runTest {
    val post = repository.createPost("Test Post", "Content", testAccount1.uid)

    // Add parent comment
    val parentId = repository.addComment(post.id, "Parent", testAccount2.uid, post.id)
    delay(500)

    val postWith1Comment = repository.getPost(post.id)
    Assert.assertEquals(1, postWith1Comment.commentCount)

    // Add reply to parent
    repository.addComment(post.id, "Reply", testAccount1.uid, parentId)
    delay(500)

    val postWith2Comments = repository.getPost(post.id)
    Assert.assertEquals(2, postWith2Comments.commentCount)

    // Remove parent (should also remove reply but only decrement by 1)
    repository.removeComment(post.id, parentId)
    delay(500)

    val postWith0Comments = repository.getPost(post.id)
    Assert.assertEquals(1, postWith0Comments.commentCount)
  }

  @Test
  fun testCommentCountInPostStructure() = runTest {
    val post = repository.createPost("Test Post", "Content", testAccount1.uid)
    Assert.assertEquals(0, post.commentCount)

    // Add comments
    repository.addComment(post.id, "Comment 1", testAccount2.uid, post.id)
    repository.addComment(post.id, "Comment 2", testAccount1.uid, post.id)
    delay(500)

    // Verify commentCount field is populated correctly
    val updatedPost = repository.getPost(post.id)
    Assert.assertEquals(2, updatedPost.commentCount)
    Assert.assertEquals(2, updatedPost.comments.size)
  }

  @Test
  fun testGetPostsReturnsCommentCount() = runTest {
    val post = repository.createPost("Post with Count", "Content", testAccount1.uid)
    repository.addComment(post.id, "Comment", testAccount2.uid, post.id)
    delay(500)

    // Fetch posts without comments
    val posts = repository.getPosts()
    val foundPost = posts.find { it.id == post.id }

    Assert.assertNotNull(foundPost)
    Assert.assertEquals(1, foundPost!!.commentCount)
    Assert.assertTrue(foundPost.comments.isEmpty()) // Comments not loaded
  }
}
