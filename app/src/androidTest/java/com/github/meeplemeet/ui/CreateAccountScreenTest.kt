package com.github.meeplemeet.ui

import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [CreateAccountScreen] using a real [FirestoreHandlesViewModel].
 * These tests validate UI field behaviour, validation logic, and integration with the ViewModel.
 */
@RunWith(AndroidJUnit4::class)
class CreateAccountScreenTest {

    /* ---------- Test rule ---------- */

    @get:Rule
    val compose = createComposeRule()

    /* ---------- Dependencies ---------- */

    /** Single repository instance shared by ViewModel and test verification. */
    private lateinit var handlesRepo: FirestoreHandlesRepository

    /** ViewModel under test. */
    private lateinit var viewModel: FirestoreHandlesViewModel

    /* ---------- Test data ---------- */

    /** Account representing the current user. */
    private lateinit var me: Account

    /** Another existing account used to test handle collisions. */
    private lateinit var existing: Account

    /** Flag to verify that onCreate was called. */
    private var onCreateCalled = false

    /* ---------- Semantic helpers ---------- */

    private fun handleField() = compose.onNodeWithText("Handle", substring = true)
    private fun usernameField() = compose.onNodeWithText("Username", substring = true)
    private fun createBtn() = compose.onNodeWithText("Let's go!", substring = true)

    /* ---------- Setup ---------- */

    @After
    fun tearDown() = runBlocking {
        // clean up the seeded handle so the next test starts fresh
        handlesRepo.deleteAccountHandle("bobhandle")
        handlesRepo.deleteAccountHandle("uniqueHandle")
        handlesRepo.deleteAccountHandle("newHandle")

    }

    @Before
    fun setup() = runBlocking {
        val bootstrapRepo = FirestoreRepository()
        me = bootstrapRepo.createAccount("111","frank","frank@email.com",null)

        handlesRepo = FirestoreHandlesRepository()
        viewModel = FirestoreHandlesViewModel(handlesRepo)

        existing = bootstrapRepo.createAccount("222","bob","bob@email.com",null)

        compose.setContent {
            MaterialTheme {
                CreateAccountScreen(
                    viewModel = viewModel,
                    currentAccount = me,
                    onCreate = { onCreateCalled = true }
                )
            }
        }
    }

    /* ---------- Tests ---------- */

    @Test
    fun initial_state_all_fields_empty() {
        handleField().assertTextContains("")
        usernameField().assertTextContains("")
    }

    @Test
    fun clicking_button_with_empty_username_shows_error() {
        handleField().performTextInput("newHandle")
        createBtn().performClick()

        createBtn().assertIsNotEnabled()
    }

    @Test
    fun entering_existing_handle_shows_error_message() {
        // seed the conflict
        runBlocking { handlesRepo.createAccountHandle(existing.uid, "bobhandle") }

        // type the conflicting handle
        handleField().performTextInput("bobhandle")
        usernameField().performTextInput("Frank")
        createBtn().performClick()

        // WAIT until the ViewModel posts the error
        compose.waitUntil(timeoutMillis = 3_000) {
            // read the same flow the screen observes
            viewModel.errorMessage.value == HandleAlreadyTakenException.DEFAULT_MESSAGE
        }

        // now the node is on screen
        compose
            .onNodeWithText(HandleAlreadyTakenException.DEFAULT_MESSAGE, substring = true)
            .assertExists()
    }

    @Test
    fun valid_input_creates_handle_and_calls_onCreate() = runBlocking {
        // Act
        handleField().performTextInput("uniqueHandle")
        usernameField().performTextInput("Frank")
        createBtn().performClick()

        // Wait until the handle is visible in the same repo the ViewModel wrote to
        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                handlesRepo.getAccount(me.uid).handle == "uniqueHandle"
            }
        }

        // Assert
        val updated = handlesRepo.getAccount(me.uid)
        assert(updated.handle == "uniqueHandle")
        assert(onCreateCalled)
    }

    @Test
    fun empty_handle_does_not_trigger_creation() {
        usernameField().performTextInput("Frank")
        createBtn().performClick()
        compose.waitForIdle()

        // Should not call onCreate nor create a handle
        val updated = runBlocking { handlesRepo.getAccount(me.uid) }
        assert(updated.handle == me.handle) // still null / empty
        assert(!onCreateCalled)
    }
}

/* ---------- test-only helper ---------- */

private suspend fun FirestoreHandlesRepository.getAccount(uid: String): Account {
    val snap = db.collection(ACCOUNT_COLLECTION_PATH).document(uid).get().await()
    val noUid = snap.toObject(AccountNoUid::class.java) ?: throw AccountNotFoundException()
    return fromNoUid(uid, noUid)
}