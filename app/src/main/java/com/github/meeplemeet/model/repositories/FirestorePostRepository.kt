// Docs generated with Claude Code.

package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.structures.CommentNoUid
import com.github.meeplemeet.model.structures.Post
import com.github.meeplemeet.model.structures.PostNoUid
import com.github.meeplemeet.model.structures.fromNoUid
import com.github.meeplemeet.model.structures.toNoUid
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Path to the posts collection in Firestore. */
const val POSTS_COLLECTION_PATH = "posts"

/** Path to the comments subcollection within each post document. */
private const val COMMENTS_COLLECTION_PATH = "comments"

/**
 * Repository for managing posts and their comments in Firestore.
 *
 * Each post is stored under `posts/{postId}`. Each post's comments and replies are stored in a
 * subcollection `posts/{postId}/comments/{commentId}`. Comments can be nested by referencing parent
 * comment IDs, enabling threaded discussions.
 *
 * @property db The Firestore database instance to use for operations.
 */
class FirestorePostRepository(private val db: FirebaseFirestore = FirebaseProvider.db) {
  private val posts = db.collection(POSTS_COLLECTION_PATH)

  /** Generates a new unique ID for a post or comment. */
  private fun newFeedUID(): String = posts.document().id

  /**
   * Creates a new post in Firestore.
   *
   * @param title The title of the post.
   * @param content The main content/body of the post.
   * @param authorId The UID of the user creating the post.
   * @param tags Optional list of tags associated with the post for categorization.
   * @return The created [Post] with its generated ID and timestamp.
   */
  suspend fun createPost(
      title: String,
      content: String,
      authorId: String,
      tags: List<String> = emptyList()
  ): Post {
    val postId = newFeedUID()
    val post = Post(postId, title, content, Timestamp.now(), authorId, tags)
    val (feedNoUid, comments) = toNoUid(post)

    val batch = db.batch()
    batch.set(posts.document(postId), feedNoUid)
    val fieldsCollection = posts.document(postId).collection(COMMENTS_COLLECTION_PATH)
    comments.forEach { c -> batch.set(fieldsCollection.document(c.id), c) }

    batch.commit().await()
    return post
  }

  /**
   * Deletes a post and all its associated comments from Firestore.
   *
   * This operation is performed as a batch write to ensure atomicity.
   *
   * @param postId The ID of the post to delete.
   */
  suspend fun deletePost(postId: String) {
    val feedRef = posts.document(postId)
    val commentsSnap = feedRef.collection(COMMENTS_COLLECTION_PATH).get().await()
    val batch = db.batch()

    commentsSnap.documents.forEach { batch.delete(it.reference) }
    batch.delete(feedRef)
    batch.commit().await()
  }

  /**
   * Adds a comment or reply to a post.
   *
   * To add a top-level comment, set [parentId] to the post's ID. To add a reply to another comment,
   * set [parentId] to that comment's ID.
   *
   * @param postId The ID of the post to comment on.
   * @param text The text content of the comment.
   * @param authorId The UID of the user creating the comment.
   * @param parentId The ID of the parent (either the post ID for top-level comments, or another
   *   comment ID for replies).
   * @return The generated ID of the newly created comment.
   */
  suspend fun addComment(postId: String, text: String, authorId: String, parentId: String): String {
    val commentId = newFeedUID()
    val comment =
        CommentNoUid(
            id = commentId,
            text = text,
            timestamp = Timestamp.now(),
            authorId = authorId,
            parentId = parentId)
    posts
        .document(postId)
        .collection(COMMENTS_COLLECTION_PATH)
        .document(commentId)
        .set(comment)
        .await()
    return commentId
  }

  /**
   * Removes a comment and all its direct replies from a post.
   *
   * This operation cascades to delete all replies to the specified comment. The operation is
   * performed as a batch write to ensure atomicity.
   *
   * @param postId The ID of the post containing the comment.
   * @param commentId The ID of the comment to remove.
   * @throws IllegalArgumentException if the comment does not exist.
   */
  suspend fun removeComment(postId: String, commentId: String) {
    val fieldsRef = posts.document(postId).collection(COMMENTS_COLLECTION_PATH)
    val commentDoc = fieldsRef.document(commentId).get().await()
    if (!commentDoc.exists()) throw IllegalArgumentException("No such comment")

    val replies = fieldsRef.whereEqualTo("parentId", commentId).get().await()

    val batch = db.batch()
    replies.documents.forEach { batch.delete(it.reference) }
    batch.delete(commentDoc.reference)
    batch.commit().await()
  }

  /**
   * Retrieves a single post with all its comments from Firestore.
   *
   * This function fetches both the post document and its comments subcollection, reconstructing the
   * full hierarchical comment tree.
   *
   * @param postId The ID of the post to retrieve.
   * @return The complete [Post] object with nested comments.
   * @throws IllegalArgumentException if the post does not exist.
   */
  suspend fun getPost(postId: String): Post {
    val feedRef = posts.document(postId)
    val feedSnap = feedRef.get().await()

    if (!feedSnap.exists()) {
      throw IllegalArgumentException("Post with ID $postId does not exist")
    }

    val postNoUid =
        feedSnap.toObject(PostNoUid::class.java)
            ?: throw IllegalArgumentException("Failed to deserialize post")

    val commentsSnap = feedRef.collection(COMMENTS_COLLECTION_PATH).get().await()
    val comments = commentsSnap.toObjects(CommentNoUid::class.java)

    return fromNoUid(postNoUid, comments)
  }

  /**
   * Creates a Flow that emits real-time updates for a post and its comments.
   *
   * This function sets up Firestore listeners for both the post document and its comments
   * subcollection, reconstructing the full hierarchical comment tree whenever any change occurs.
   *
   * @param postId The ID of the post to listen to.
   * @return A [Flow] that emits the complete [Post] object with nested comments whenever updates
   *   occur.
   */
  fun listenPost(postId: String): Flow<Post> = callbackFlow {
    val feedRef = posts.document(postId)
    val commentsRef = feedRef.collection(COMMENTS_COLLECTION_PATH)

    // Listen for both feed metadata and comment updates
    var commentsListener: ListenerRegistration? = null
    val feedListener =
        feedRef.addSnapshotListener { feedSnap, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (feedSnap == null || !feedSnap.exists()) return@addSnapshotListener

          val postNoUid = feedSnap.toObject(PostNoUid::class.java) ?: return@addSnapshotListener

          commentsListener =
              commentsRef.addSnapshotListener { qs, e2 ->
                if (e2 != null) {
                  close(e2)
                  return@addSnapshotListener
                }
                if (qs != null) {
                  val comments = qs.toObjects(CommentNoUid::class.java)
                  trySend(fromNoUid(postNoUid, comments))
                }
              }
        }

    awaitClose { commentsListener?.remove() }
    awaitClose { feedListener.remove() }
  }

  /**
   * Retrieves all posts without their comments from Firestore.
   *
   * This function fetches all post documents for efficient preview display in feed/overview
   * screens. Comments are not loaded to improve performance when displaying multiple posts.
   *
   * @return A list of [Post] objects without comments, sorted by timestamp (newest first).
   */
  suspend fun getPosts(): List<Post> {
    val postsSnap = posts.get().await()

    return postsSnap.documents
        .mapNotNull { postDoc ->
          val postNoUid = postDoc.toObject(PostNoUid::class.java) ?: return@mapNotNull null
          fromNoUid(postNoUid, emptyList())
        }
        .sortedByDescending { it.timestamp }
  }
}
