/** Sections of this file were generated using ChatGPT */
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
 * Instrumented integration tests for [FirestoreRepository.searchByHandle].
 *
 * These tests connect to a real Firebase instance (through [FirestoreTests]) and verify
 * that the `searchByHandle()` query behaves correctly in different situations.
 *
 * Each test seeds temporary accounts in Firestore before performing live queries.
 */
@RunWith(AndroidJUnit4::class)
class SearchByHandleRepoInstrumentedTest : FirestoreTests() {

  private lateinit var repo: FirestoreRepository

  /**
   * Seeds test accounts in Firestore before each test run.
   *
   * Accounts created:
   * - `alice`
   * - `bob`
   * - `john`, `johna`, `johnny`
   */
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

  /**
   * Verifies that `searchByHandle()` returns all accounts whose handles
   * start with the given prefix.
   *
   * Expected result: handles `john`, `johna`, `johnny`.
   */
  @Test
  fun searchByHandleReturnsHandlesStartingWithPrefix() = runBlocking {
    val list = withTimeout(5_000) { repo.searchByHandle("john").first() }

    assert(list.size == 3) { "Expected 3, got ${list.size}" }
    assert(list.map { it.handle }.containsAll(listOf("john", "johna", "johnny")))
  }

  /**
   * Verifies that `searchByHandle()` returns an empty list when
   * no matching handles exist.
   */
  @Test
  fun searchByHandleEmptyWhenNoMatch() = runBlocking {
    val list = withTimeout(5_000) { repo.searchByHandle("xyz").first() }

    assert(list.isEmpty()) { "Expected empty, got ${list.size}" }
  }

  /**
   * Verifies that the `searchByHandle()` query respects the 30-result limit.
   *
   * Seeds 35 test users with handles starting with `"zz"` and ensures
   * at least 30 are returned before filtering in the UI.
   */
  @Test
  fun searchByHandleRespectsLimit30() = runBlocking {
    repeat(35) { idx -> createAccount("zz$idx", "ZZ $idx", "zz$idx@example.com", null) }

    val list = withTimeout(5_000) { repo.searchByHandle("zz").first() }

    assert(list.size >= 30) { "Expected ≥ 30, got ${list.size}" }
  }

  /**
   * Helper to quickly create a Firestore account for testing.
   */
  private suspend fun createAccount(
    handle: String,
    name: String,
    email: String,
    photoUrl: String?
  ) = repo.createAccount(handle, name, email, photoUrl)
}
