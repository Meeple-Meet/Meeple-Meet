package com.github.meeplemeet.model.structures

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

data class Feed(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Timestamp,
    val authorId: String,
    val tags: List<String>,
    val nodes: MutableList<Comment> = mutableListOf()
)

@Serializable
data class FeedNoUid(
    val id: String,
    val title: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val tags: List<String> = emptyList(),
    val authorId: String = "",
)

/** Flatten feed and comments into Firestore-storable forms. */
fun toNoUid(feed: Feed): Pair<FeedNoUid, List<CommentNoUid>> {
  val commentDocs = toNoUid(feed.id, feed.nodes)
  val feedNoUid =
      FeedNoUid(
          id = feed.id,
          title = feed.title,
          content = feed.content,
          timestamp = feed.timestamp,
          authorId = feed.authorId,
          tags = feed.tags)
  return feedNoUid to commentDocs
}

/** Reconstruct a full Feed from Firestore-stored parts. */
fun fromNoUid(feedNoUid: FeedNoUid, commentDocs: List<CommentNoUid>): Feed {
  val rootComments = fromNoUid(feedNoUid.id, commentDocs)
  return Feed(
      id = feedNoUid.id,
      title = feedNoUid.title,
      content = feedNoUid.content,
      timestamp = feedNoUid.timestamp,
      authorId = feedNoUid.authorId,
      tags = feedNoUid.tags,
      nodes = rootComments,
  )
}
