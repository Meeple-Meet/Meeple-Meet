package com.github.meeplemeet.model.structures

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
fun fromNoUid(feedId: String, docs: List<CommentNoUid>): MutableList<Comment> {
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
          .toMutableMap()

  docs.forEach { c ->
    if (c.parentId.isNotEmpty() && c.parentId != feedId) {
      map[c.parentId]?.children?.add(map[c.id]!!)
    }
  }

  return docs
      .filter { it.parentId == feedId || it.parentId.isEmpty() }
      .mapNotNull { map[it.id] }
      .toMutableList()
}
