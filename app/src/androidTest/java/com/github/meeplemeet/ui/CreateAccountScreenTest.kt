/** Sections of this file were generated using ChatGPT */
package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.HandleAlreadyTakenException
import com.github.meeplemeet.model.repositories.ACCOUNT_COLLECTION_PATH
import com.github.meeplemeet.model.repositories.FirestoreHandlesRepository
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.AccountNoUid
import com.github.meeplemeet.model.structures.fromNoUid
import com.github.meeplemeet.model.viewmodels.FirestoreHandlesViewModel
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [CreateAccountScreen] using a real [FirestoreHandlesViewModel].
 *
 * Validates:
 * - Initial field states.
 * - Input validation for empty username and handle conflicts.
 * - Integration with [FirestoreHandlesViewModel] and [FirestoreHandlesRepository].
 * - Successful handle creation triggers [onCreate] callback.
 */
@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {

  @get:Rule val compose = createComposeRule()

  /** Repository used by the ViewModel and for test verification. */
  private lateinit var handlesRepo: FirestoreHandlesRepository

  /** ViewModel under test. */
  private lateinit var viewModel: FirestoreHandlesViewModel

  /** Account representing the current user. */
  private lateinit var me: Account

  /** Existing account used to test handle collisions. */
  private lateinit var existing: Account

  /** Flag to verify that onCreate was called. */
  private var onCreateCalled = false

  /** Returns the handle input field node. */
  private fun handleField() = compose.onNodeWithText("Handle", substring = true)

  /** Returns the username input field node. */
  private fun usernameField() = compose.onNodeWithText("Username", substring = true)

  /** Returns the create button node. */
  private fun createBtn() = compose.onNodeWithText("Let's go!", substring = true)

  /** Cleans up seeded handles after each test to ensure a fresh start. */
  @After
  fun tearDown() = runBlocking {
    handlesRepo.deleteAccountHandle("bobhandle")
    handlesRepo.deleteAccountHandle("uniqueHandle")
    handlesRepo.deleteAccountHandle("newHandle")
  }

  /**
   * Sets up test dependencies and seeds initial accounts.
   *
   * Initializes a ViewModel, repositories, and Compose content for testing.
   */
  @Before
  fun setup() = runBlocking {
    val bootstrapRepo = FirestoreRepository()
    me = bootstrapRepo.createAccount("111", "frank", "frank@email.com", null)

    handlesRepo = FirestoreHandlesRepository()
    viewModel = FirestoreHandlesViewModel(handlesRepo)

    existing = bootstrapRepo.createAccount("222", "bob", "bob@email.com", null)

    compose.setContent {
      AppTheme(themeMode = ThemeMode.DARK) {
        CreateAccountScreen(
            viewModel = viewModel, currentAccount = me, onCreate = { onCreateCalled = true })
      }
    }
  }

  /** Verifies that all input fields are initially empty. */
  @Test
  fun initial_state_all_fields_empty() {
    handleField().assertTextContains("")
    usernameField().assertTextContains("")
  }

  /** Verifies that clicking the create button with empty username disables the button. */
  @Test
  fun clicking_button_with_empty_username_shows_error() {
    handleField().performTextInput("newHandle")
    createBtn().performClick()
    createBtn().assertIsNotEnabled()
  }

  /**
   * Tests that entering a handle already taken by another account displays the appropriate error
   * message.
   */
  @Test
  fun entering_existing_handle_shows_error_message() {
    runBlocking { handlesRepo.createAccountHandle(existing.uid, "bobhandle") }

    handleField().performTextInput("bobhandle")
    usernameField().performTextInput("Frank")
    createBtn().performClick()

    compose.waitUntil(timeoutMillis = 3_000) {
      viewModel.errorMessage.value == HandleAlreadyTakenException.DEFAULT_MESSAGE
    }

    compose
        .onNodeWithText(HandleAlreadyTakenException.DEFAULT_MESSAGE, substring = true)
        .assertExists()
  }

  /** Tests that valid input creates the handle and triggers onCreate. */
  @Test
  fun valid_input_creates_handle_and_calls_onCreate() = runBlocking {
    handleField().performTextInput("uniqueHandle")
    usernameField().performTextInput("Frank")
    createBtn().performClick()

    compose.waitUntil(timeoutMillis = 5_000) {
      runBlocking { handlesRepo.getAccount(me.uid).handle == "uniqueHandle" }
    }

    val updated = handlesRepo.getAccount(me.uid)
    assert(updated.handle == "uniqueHandle")
    assert(onCreateCalled)
    Thread.sleep(5000) // For observing the result during test runs
  }

  /** Verifies that empty handle input does not create a handle or call onCreate. */
  @Test
  fun empty_handle_does_not_trigger_creation() {
    usernameField().performTextInput("Frank")
    createBtn().performClick()
    compose.waitForIdle()

    val updated = runBlocking { handlesRepo.getAccount(me.uid) }
    assert(updated.handle == me.handle)
    assert(!onCreateCalled)
  }
  /**
   * Tests that typing something in the username field and then deleting it shows the "Username
   * cannot be empty" error message.
   */
  @Test
  fun deleting_username_shows_empty_error_message() {
    usernameField().performTextInput("Frank")
    compose.waitForIdle()
    usernameField().performTextClearance()
    compose.waitForIdle()
    compose.onNodeWithText("Username cannot be empty", substring = true).assertExists()
  }
}

/**
 * Test-only helper to retrieve an [Account] from [FirestoreHandlesRepository].
 *
 * @param uid The UID of the account to retrieve.
 * @return The [Account] object corresponding to the UID.
 * @throws AccountNotFoundException If no account exists with the given UID.
 */
private suspend fun FirestoreHandlesRepository.getAccount(uid: String): Account {
  val snap = db.collection(ACCOUNT_COLLECTION_PATH).document(uid).get().await()
  val noUid = snap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
  return fromNoUid(uid, noUid)
}
