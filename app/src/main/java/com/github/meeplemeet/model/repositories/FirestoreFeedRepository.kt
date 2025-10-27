package com.github.meeplemeet.model.repositories

import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.model.structures.Feed
import com.github.meeplemeet.model.structures.FeedSerializable
import com.github.meeplemeet.model.structures.fromNoUid
import com.github.meeplemeet.model.structures.toNoUid
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

const val FEEDS_COLLECTION_PATH = "feeds"

/**
 * Repository for managing feeds and their comments in Firestore.
 *
 * Each feed is stored under `feeds/{feedId}`. Each feed’s comments and replies are stored in a
 * subcollection `feeds/{feedId}/fields/{commentId}`.
 */
class FirestoreFeedRepository(private val db: FirebaseFirestore = FirebaseProvider.db) {

  private val feeds = db.collection(FEEDS_COLLECTION_PATH)

  private fun newFeedUID(): String = feeds.document().id

  /** Create a new feed and initialize its subcollection `fields/{id}`. */
  suspend fun createFeed(text: String, authorId: String): Feed {
    val feedId = newFeedUID()
    val root = Feed(feedId, text, Timestamp.now(), authorId)
    val batch = db.batch()

    // main feed document
    batch.set(feeds.document(feedId), mapOf("createdAt" to Timestamp.now()))

    // create subcollection root comment
    val flattened = toNoUid(root)
    val fieldsCollection = feeds.document(feedId).collection("fields")
    flattened.forEach { node ->
      val docRef = fieldsCollection.document()
      batch.set(docRef, node)
    }

    batch.commit().await()
    return root
  }

  /** Delete a feed and all its comments. */
  suspend fun deleteFeed(feedId: String) {
    val feedRef = feeds.document(feedId)
    val subDocs = feedRef.collection("fields").get().await()
    val batch = db.batch()

    subDocs.documents.forEach { batch.delete(it.reference) }
    batch.delete(feedRef)
    batch.commit().await()
  }

  /** Add a comment or reply to a feed. */
  suspend fun addComment(feedId: String, text: String, authorId: String, parentId: String): String {
    val feedRef = feeds.document(feedId).collection("fields")
    val commentId = newFeedUID()
    val comment =
        FeedSerializable(
            id = commentId,
            text = text,
            timestamp = Timestamp.now(),
            authorId = authorId,
            parentId = parentId)
    feedRef.document(commentId).set(comment).await()
    return commentId
  }

  /** Remove a comment (and optionally its subcomments) by document ID. */
  suspend fun removeComment(feedId: String, commentId: String) {
    val fieldsRef = feeds.document(feedId).collection("fields")
    val doc = fieldsRef.document(commentId).get().await()
    if (!doc.exists()) throw IllegalArgumentException("No such comment")
    val snapshot = fieldsRef.whereEqualTo("parentId", commentId).get().await()

    val batch = db.batch()
    snapshot.documents.forEach { batch.delete(it.reference) }
    batch.delete(doc.reference)
    batch.commit().await()
  }

  /** Listen to live updates of a given feed with its hierarchical comments reconstructed. */
  fun listenFeed(feedId: String): Flow<Feed> = callbackFlow {
    val collectionRef = feeds.document(feedId).collection("fields")
    val reg =
        collectionRef.addSnapshotListener { qs, e ->
          if (e != null) {
            close(e)
            return@addSnapshotListener
          }
          if (qs != null) {
            val list = qs.toObjects(FeedSerializable::class.java)
            trySend(fromNoUid(feedId, list))
          }
        }
    awaitClose { reg.remove() }
  }
}
