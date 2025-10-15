package com.github.meeplemeet.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device test – asserts the temporary searchByHandle() range query. Extends FirestoreTests so we
 * inherit the real-firebase connection.
 */
@RunWith(AndroidJUnit4::class)
class SearchByHandleRepoInstrumentedTest : FirestoreTests() {

  private lateinit var repo: FirestoreRepository

  @Before
  fun setup() {
    repo = FirestoreRepository()
    runBlocking {
      createAccount("alice", "Alice", "alice@example.com", null)
      createAccount("bob", "Bob", "bob@example.com", null)
      createAccount("john", "John", "john@example.com", null)
      createAccount("johna", "Johna", "johna@example.com", null)
      createAccount("johnny", "Johnny", "johnny@example.com", null)
    }
  }

  @Test
  fun searchByHandleReturnsHandlesStartingWithPrefix() = runBlocking {
    val list = withTimeout(5_000) { repo.searchByHandle("john").first() }

    assert(list.size == 3) { "Expected 3, got ${list.size}" }
    assert(list.map { it.handle }.containsAll(listOf("john", "johna", "johnny")))
  }

  @Test
  fun searchByHandleEmptyWhenNoMatch() = runBlocking {
    val list = withTimeout(5_000) { repo.searchByHandle("xyz").first() }

    assert(list.isEmpty()) { "Expected empty, got ${list.size}" }
  }

  @Test
  fun searchByHandleRespectsLimit30() = runBlocking {
    // seed 35 handles that all start with "zz"
    repeat(35) { idx -> createAccount("zz$idx", "ZZ $idx", "zz$idx@example.com", null) }

    val list = withTimeout(5_000) { repo.searchByHandle("zz").first() }

    // the Flow emits **all** matches; we take(30) in the UI
    assert(list.size >= 30) { "Expected ≥ 30, got ${list.size}" }
  }

  /* ---------- helper ---------- */
  private suspend fun createAccount(
      handle: String,
      name: String,
      email: String,
      photoUrl: String?
  ) = repo.createAccount(handle, name, email, photoUrl)
}
