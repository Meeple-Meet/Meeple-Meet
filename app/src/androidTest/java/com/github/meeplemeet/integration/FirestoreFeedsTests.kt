package com.github.meeplemeet.integration

import com.github.meeplemeet.model.repositories.FirestorePostRepository
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreFeedsTests : FirestoreTests() {
  private lateinit var repository: FirestorePostRepository

  @Before
  fun setup() {
    repository = FirestorePostRepository()
  }

  @Test
  fun test() = runTest {
    val feed = repository.createPost("What's up world ?", "hi", "qsdf")
    val id = repository.addComment(feed.id, "Nothing", "t", feed.id)
    // repository.removeComment(feed.id, id)
    // repository.deleteFeed(feed.id)
  }
}
