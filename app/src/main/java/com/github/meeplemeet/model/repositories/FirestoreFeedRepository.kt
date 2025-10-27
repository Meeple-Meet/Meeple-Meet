package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.structures.CommentNoUid
import com.github.meeplemeet.model.structures.Feed
import com.github.meeplemeet.model.structures.FeedNoUid
import com.github.meeplemeet.model.structures.fromNoUid
import com.github.meeplemeet.model.structures.toNoUid
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

const val FEEDS_COLLECTION_PATH = "feeds"
private const val COMMENTS_COLLECTION_PATH = "comments"

/**
 * Repository for managing feeds and their comments in Firestore.
 *
 * Each feed is stored under `feeds/{feedId}`. Each feed’s comments and replies are stored in a
 * subcollection `feeds/{feedId}/fields/{commentId}`.
 */
class FirestoreFeedRepository(private val db: FirebaseFirestore = FirebaseProvider.db) {

  private val feeds = db.collection(FEEDS_COLLECTION_PATH)

  private fun newFeedUID(): String = feeds.document().id

  /** Create a new feed with its main document and empty subcollection. */
  suspend fun createFeed(
      title: String,
      content: String,
      authorId: String,
      tags: List<String> = emptyList()
  ): Feed {
    val feedId = newFeedUID()
    val feed = Feed(feedId, title, content, Timestamp.now(), authorId, tags)
    val (feedNoUid, comments) = toNoUid(feed)

    val batch = db.batch()
    batch.set(feeds.document(feedId), feedNoUid)
    val fieldsCollection = feeds.document(feedId).collection(COMMENTS_COLLECTION_PATH)
    comments.forEach { c -> batch.set(fieldsCollection.document(c.id), c) }

    batch.commit().await()
    return feed
  }

  /** Delete a feed and all its comments. */
  suspend fun deleteFeed(feedId: String) {
    val feedRef = feeds.document(feedId)
    val commentsSnap = feedRef.collection(COMMENTS_COLLECTION_PATH).get().await()
    val batch = db.batch()

    commentsSnap.documents.forEach { batch.delete(it.reference) }
    batch.delete(feedRef)
    batch.commit().await()
  }

  /** Add a comment or reply to a feed. */
  suspend fun addComment(feedId: String, text: String, authorId: String, parentId: String): String {
    val commentId = newFeedUID()
    val comment =
        CommentNoUid(
            id = commentId,
            text = text,
            timestamp = Timestamp.now(),
            authorId = authorId,
            parentId = parentId)
    feeds
        .document(feedId)
        .collection(COMMENTS_COLLECTION_PATH)
        .document(commentId)
        .set(comment)
        .await()
    return commentId
  }

  /** Remove a comment and all its direct replies. */
  suspend fun removeComment(feedId: String, commentId: String) {
    val fieldsRef = feeds.document(feedId).collection(COMMENTS_COLLECTION_PATH)
    val commentDoc = fieldsRef.document(commentId).get().await()
    if (!commentDoc.exists()) throw IllegalArgumentException("No such comment")

    val replies = fieldsRef.whereEqualTo("parentId", commentId).get().await()

    val batch = db.batch()
    replies.documents.forEach { batch.delete(it.reference) }
    batch.delete(commentDoc.reference)
    batch.commit().await()
  }

  /** Listen for live updates to a feed and its hierarchical comments. */
  fun listenFeed(feedId: String): Flow<Feed> = callbackFlow {
    val feedRef = feeds.document(feedId)
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

          val feedNoUid = feedSnap.toObject(FeedNoUid::class.java) ?: return@addSnapshotListener

          commentsListener =
              commentsRef.addSnapshotListener { qs, e2 ->
                if (e2 != null) {
                  close(e2)
                  return@addSnapshotListener
                }
                if (qs != null) {
                  val comments = qs.toObjects(CommentNoUid::class.java)
                  trySend(fromNoUid(feedNoUid, comments))
                }
              }
        }

    awaitClose { commentsListener?.remove() }
    awaitClose { feedListener.remove() }
  }
}
