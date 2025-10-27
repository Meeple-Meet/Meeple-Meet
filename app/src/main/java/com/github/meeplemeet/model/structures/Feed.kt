package com.github.meeplemeet.model.structures

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

data class Feed(
    val id: String,
    val text: String,
    val timestamp: Timestamp,
    val authorId: String,
    val nodes: MutableList<Feed> = mutableListOf()
)

@Serializable
data class FeedSerializable(
    val id: String,
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val authorId: String = "",
    val parentId: String = ""
)

fun toNoUid(feed: Feed): List<FeedSerializable> {
  val result = mutableListOf<FeedSerializable>()
  val stack = ArrayDeque<Pair<Feed, String>>()
  stack.add(feed to "")

  while (stack.isNotEmpty()) {
    val (node, parentId) = stack.removeLast()
    result.add(
        FeedSerializable(
            id = node.id,
            text = node.text,
            timestamp = node.timestamp,
            authorId = node.authorId,
            parentId = parentId))
    node.nodes.forEach { child -> stack.add(child to node.id) }
  }
  return result
}

fun fromNoUid(rootId: String, docs: List<FeedSerializable>): Feed {
  val feeds =
      docs
          .associateBy { it.id }
          .mapValues { Feed(it.key, it.value.text, it.value.timestamp, it.value.authorId) }
          .toMutableMap()

  feeds.values.forEach { feed ->
    val parentId = docs.find { it.id == feed.id }?.parentId ?: ""
    feeds[parentId]?.nodes?.add(feed)
  }

  return feeds[rootId] ?: error("Root not found")
}
