package com.github.meeplemeet.integration

import com.github.meeplemeet.model.repositories.FirestoreFeedRepository
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreFeedsTests : FirestoreTests() {
  private lateinit var repository: FirestoreFeedRepository

  @Before
  fun setup() {
    repository = FirestoreFeedRepository()
  }

  @Test
  fun test() = runTest {
    val feed = repository.createFeed("What's up world ?", "qsdf")
    val id = repository.addComment(feed.id, "Nothing", "t", feed.id)
    repository.removeComment(feed.id, id)
    repository.deleteFeed(feed.id)
  }
}
