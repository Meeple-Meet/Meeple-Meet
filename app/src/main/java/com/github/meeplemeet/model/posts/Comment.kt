package com.github.meeplemeet.model.posts

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

data class Comment(
    val id: String,
    val text: String,
    val timestamp: Timestamp,
    val authorId: String,
    val children: MutableList<Comment> = mutableListOf()
)

@Serializable
data class CommentNoUid(
    val id: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val authorId: String = "",
    val parentId: String = "",
)

fun toNoUid(feedId: String, comments: List<Comment>): List<CommentNoUid> {
  val result = mutableListOf<CommentNoUid>()
  val stack = ArrayDeque<Pair<Comment, String>>()
  comments.forEach { stack.add(it to feedId) }

  while (stack.isNotEmpty()) {
    val (node, parentId) = stack.removeLast()
    result.add(
        CommentNoUid(
            id = node.id,
            text = node.text,
            timestamp = node.timestamp,
            authorId = node.authorId,
            parentId = parentId))
    node.children.forEach { child -> stack.add(child to node.id) }
  }
  return result
}

/** Reconstruct a tree of comments from flat CommentNoUid list. */
fun fromNoUid(feedId: String, docs: List<CommentNoUid>): List<Comment> {
  val map =
      docs
          .associateBy { it.id }
          .mapValues { (_, c) ->
            Comment(
                id = c.id,
                text = c.text,
                timestamp = c.timestamp,
                authorId = c.authorId,
                children = mutableListOf())
          }

  docs.forEach { c ->
    if (c.parentId.isNotBlank() && c.parentId != feedId) {
      val parent = map[c.parentId]
      val child = map[c.id]
      if (parent != null && child != null) parent.children.add(child)
    }
  }

  return docs.filter { it.parentId == feedId || it.parentId.isBlank() }.mapNotNull { map[it.id] }
}
