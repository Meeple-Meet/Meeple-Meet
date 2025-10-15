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

/** Instrumented test – asserts ViewModel integration with the temporary searchByHandle() Flow. */
@RunWith(AndroidJUnit4::class)
class SearchByHandleVMInstrumentedTest : FirestoreTests() {

  private lateinit var repo: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel

  private val testDispatcher = UnconfinedTestDispatcher()

  @OptIn(ExperimentalCoroutinesApi::class)
  @Before
  fun setup(): Unit = runBlocking {
    Dispatchers.setMain(testDispatcher)
    repo = FirestoreRepository()
    viewModel = FirestoreViewModel(repo)

    /* seed accounts */
    createAccount("alice", "Alice", "alice@example.com", null)
    createAccount("bob", "Bob", "bob@example.com", null)
    createAccount("john", "John", "john@example.com", null)
    createAccount("johna", "Johna", "johna@example.com", null)
    createAccount("johnny", "Johnny", "johnny@example.com", null)
  }

  @Test
  fun vmSearchByHandleEmitsCorrectHandles() = runBlocking {
    viewModel.searchByHandle("john")

    val list = withTimeout(5_000) { viewModel.handleSuggestions.first { it.isNotEmpty() } }

    assertEquals(3, list.size)
    assertTrue(list.map { it.handle }.containsAll(listOf("john", "johna", "johnny")))
  }

  @Test
  fun vmSearchByHandleClearsOnBlankInput() = runBlocking {
    viewModel.searchByHandle("john")
    viewModel.searchByHandle("") // blank

    val empty = withTimeout(5_000) { viewModel.handleSuggestions.first { it.isEmpty() } }

    assertTrue(empty.isEmpty())
  }

  @Test
  fun vmSearchByHandleLimitsTo30Items() = runBlocking {
    /* seed 35 handles that all start with "zz" */
    repeat(35) { idx -> createAccount("zz$idx", "ZZ $idx", "zz$idx@example.com", null) }

    viewModel.searchByHandle("zz")

    val list = withTimeout(5_000) { viewModel.handleSuggestions.first { it.size >= 30 } }

    assertTrue("Expected ≥ 30, got ${list.size}", list.size >= 30)
  }

  @Test
  fun vmSearchByHandleEmptyWhenNoMatch() = runBlocking {
    viewModel.searchByHandle("xyz")

    val empty = withTimeout(5_000) { viewModel.handleSuggestions.first { it.isEmpty() } }

    assertTrue(empty.isEmpty())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    testDispatcher.cancel()
  }

  /* ---------- helper ---------- */
  private suspend fun createAccount(
      handle: String,
      name: String,
      email: String,
      photoUrl: String?
  ) = repo.createAccount(handle, name, email, photoUrl)
}
