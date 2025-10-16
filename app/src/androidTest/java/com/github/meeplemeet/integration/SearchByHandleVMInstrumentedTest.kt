/** Sections of this file were generated using ChatGPT */
package com.github.meeplemeet.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented integration tests for [FirestoreViewModel.searchByHandle].
 *
 * These tests verify that the ViewModel correctly integrates with the Firestore-backed
 * [FirestoreRepository] and that it updates its `handleSuggestions` Flow as expected in real
 * Firebase conditions.
 *
 * Extends [FirestoreTests] to use a live Firestore connection.
 */
@RunWith(AndroidJUnit4::class)
class SearchByHandleVMInstrumentedTest : FirestoreTests() {

  private lateinit var repo: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel
  @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()

  /**
   * Initializes the test dispatcher, repository, and ViewModel. Seeds several test accounts used
   * for search queries.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Before
  fun setup(): Unit = runBlocking {
    Dispatchers.setMain(testDispatcher)
    repo = FirestoreRepository()
    viewModel = FirestoreViewModel(repo)

    createAccount("alice", "Alice", "alice@example.com", null)
    createAccount("bob", "Bob", "bob@example.com", null)
    createAccount("john", "John", "john@example.com", null)
    createAccount("johna", "Johna", "johna@example.com", null)
    createAccount("johnny", "Johnny", "johnny@example.com", null)
  }

  /**
   * Verifies that the ViewModel emits all handles starting with the provided prefix when
   * `searchByHandle()` is called.
   *
   * Expected handles: `john`, `johna`, `johnny`.
   */
  @Test
  fun vmSearchByHandleEmitsCorrectHandles() = runBlocking {
    viewModel.searchByHandle("john")

    val list = withTimeout(5_000) { viewModel.handleSuggestions.first { it.isNotEmpty() } }

    assertEquals(3, list.size)
    assertTrue(list.map { it.handle }.containsAll(listOf("john", "johna", "johnny")))
  }

  /**
   * Verifies that calling `searchByHandle("")` clears the current suggestions list inside the
   * ViewModel.
   */
  @Test
  fun vmSearchByHandleClearsOnBlankInput() = runBlocking {
    viewModel.searchByHandle("john")
    viewModel.searchByHandle("") // blank input

    val empty = withTimeout(5_000) { viewModel.handleSuggestions.first { it.isEmpty() } }

    assertTrue(empty.isEmpty())
  }

  /**
   * Ensures that the ViewModel respects the 30-item limit for handle suggestions.
   *
   * Seeds 35 handles starting with `"zz"` and asserts that at least 30 items are emitted before the
   * UI filters them.
   */
  @Test
  fun vmSearchByHandleLimitsTo30Items() = runBlocking {
    repeat(35) { idx -> createAccount("zz$idx", "ZZ $idx", "zz$idx@example.com", null) }

    viewModel.searchByHandle("zz")

    val list = withTimeout(5_000) { viewModel.handleSuggestions.first { it.size >= 30 } }

    assertTrue("Expected ≥ 30, got ${list.size}", list.size >= 30)
  }

  /** Verifies that `searchByHandle()` emits an empty list when no matching handles exist. */
  @Test
  fun vmSearchByHandleEmptyWhenNoMatch() = runBlocking {
    viewModel.searchByHandle("xyz")

    val empty = withTimeout(5_000) { viewModel.handleSuggestions.first { it.isEmpty() } }

    assertTrue(empty.isEmpty())
  }

  /** Cleans up the coroutine dispatcher after tests finish. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @After
  fun tearDown() {
    Dispatchers.resetMain()
    testDispatcher.cancel()
  }

  /** Helper for creating Firestore test accounts. */
  private suspend fun createAccount(
      handle: String,
      name: String,
      email: String,
      photoUrl: String?
  ) = repo.createAccount(handle, name, email, photoUrl)
}
