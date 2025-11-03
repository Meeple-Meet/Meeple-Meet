// Docs generated with Claude Code.

package com.github.meeplemeet.model.posts

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

/**
 * Represents a post in the application with hierarchical comments.
 *
 * A post contains main content and can have threaded comments attached to it. Comments are stored
 * in the [comments] list as a tree structure.
 *
 * @property id Unique identifier for the post.
 * @property title The title/heading of the post.
 * @property body The main content/body text of the post.
 * @property timestamp When the post was created.
 * @property authorId The UID of the user who created the post.
 * @property tags List of tags for categorizing and filtering posts.
 * @property comments Top-level comments on this post, which may contain nested replies.
 */
data class Post(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Timestamp,
    val authorId: String,
    val tags: List<String>,
    val comments: List<Comment> = emptyList(),
    val commentCount: Int = 0,
)

/**
 * Firestore-serializable representation of a post without nested comment data.
 *
 * This class is used for storing post metadata in Firestore. Comments are stored separately in a
 * subcollection and are not included in this structure.
 *
 * @property id Unique identifier for the post.
 * @property title The title/heading of the post.
 * @property body The main content/body text of the post.
 * @property timestamp When the post was created.
 * @property tags List of tags for categorizing and filtering posts.
 * @property authorId The UID of the user who created the post.
 */
@Serializable
data class PostNoUid(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val tags: List<String> = emptyList(),
    val authorId: String = "",
    val commentCount: Int = 0,
)

/**
 * Converts a [Post] with nested comments into Firestore-storable forms.
 *
 * This function flattens the hierarchical comment structure into a list of [CommentNoUid] objects
 * that can be stored in a Firestore subcollection, along with a [PostNoUid] containing the post
 * metadata.
 *
 * @param post The post to convert.
 * @return A pair of ([PostNoUid], [List]<[CommentNoUid]>) ready for Firestore storage.
 */
fun toNoUid(post: Post): Pair<PostNoUid, List<CommentNoUid>> {
  val postNoUid =
      PostNoUid(
          id = post.id,
          title = post.title,
          body = post.body,
          timestamp = post.timestamp,
          authorId = post.authorId,
          tags = post.tags,
          commentCount = post.comments.size)
  return postNoUid to toNoUid(post.id, post.comments)
}

/**
 * Reconstructs a complete [Post] with nested comments from Firestore-stored data.
 *
 * This function takes the flat list of comments from Firestore and rebuilds the hierarchical
 * comment tree structure based on parent-child relationships.
 *
 * @param postNoUid The post metadata from Firestore.
 * @param commentDocs The list of comments from the Firestore subcollection.
 * @return A fully reconstructed [Post] with nested comment hierarchy.
 */
fun fromNoUid(postNoUid: PostNoUid, commentDocs: List<CommentNoUid>): Post =
    Post(
        id = postNoUid.id,
        title = postNoUid.title,
        body = postNoUid.body,
        timestamp = postNoUid.timestamp,
        authorId = postNoUid.authorId,
        tags = postNoUid.tags,
        comments = fromNoUid(postNoUid.id, commentDocs),
        commentCount = postNoUid.commentCount)
