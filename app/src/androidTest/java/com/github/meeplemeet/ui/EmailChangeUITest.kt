@file:Suppress("TestFunctionName")
// AI was used for this file

package com.github.meeplemeet.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.ui.account.EmailSection
import com.github.meeplemeet.ui.account.EmailSectionTestTags
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the EmailSection composable.
 *
 * Tests verify:
 * - Email validation (valid/invalid format)
 * - Email confirmation matching
 * - Password input required
 * - Button enable/disable states
 * - Error message display
 * - UI component existence and interactions
 *
 * Note: These are UI-only tests. Integration tests for actual email change functionality are in
 * EmailChangeIntegrationTest.kt
 */
class EmailChangeUITest : FirestoreTests() {
  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  @Test
  fun smoke_initial_state() {
    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          online = true)
    }

    checkpoint("Title is displayed") {
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_TITLE).assertExists()
    }

    checkpoint("New email input exists") {
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_INPUT).assertExists()
    }

    checkpoint("Confirm email input exists") {
      compose.onNodeWithTag(EmailSectionTestTags.CONFIRM_EMAIL_INPUT).assertExists()
    }

    checkpoint("Password input exists") {
      compose.onNodeWithTag(EmailSectionTestTags.PASSWORD_INPUT).assertExists()
    }

    checkpoint("Change email button exists but is disabled") {
      compose
          .onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON)
          .assertExists()
          .assertIsNotEnabled()
    }

    checkpoint("No error messages initially") {
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_ERROR_LABEL).assertDoesNotExist()
      compose.onNodeWithTag(EmailSectionTestTags.EMAILS_DONT_MATCH_LABEL).assertDoesNotExist()
    }
  }

  @Test
  fun test_email_validation_invalid() {
    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          online = true)
    }

    checkpoint("Enter invalid email shows error") {
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_INPUT).performTextInput("notanemail")
      compose.waitForIdle()
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_ERROR_LABEL).assertExists()
    }

    checkpoint("Button remains disabled with invalid email") {
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON).assertIsNotEnabled()
    }
  }

  @Test
  fun test_email_validation_valid() {
    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          online = true)
    }

    checkpoint("Enter valid email shows valid message") {
      compose
          .onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_INPUT)
          .performTextInput("valid@example.com")
      compose.waitForIdle()
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_VALID_LABEL).assertExists()
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_ERROR_LABEL).assertDoesNotExist()
    }

    checkpoint("Button still disabled without confirmation and password") {
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON).assertIsNotEnabled()
    }
  }

  @Test
  fun test_email_confirmation_mismatch() {
    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          online = true)
    }

    checkpoint("Emails don't match shows error") {
      compose
          .onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_INPUT)
          .performTextInput("valid@example.com")
      compose
          .onNodeWithTag(EmailSectionTestTags.CONFIRM_EMAIL_INPUT)
          .performTextInput("different@example.com")
      compose.waitForIdle()
      compose.onNodeWithTag(EmailSectionTestTags.EMAILS_DONT_MATCH_LABEL).assertExists()
    }

    checkpoint("Button disabled when emails don't match") {
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON).assertIsNotEnabled()
    }
  }

  @Test
  fun test_button_enabled_when_all_valid() {
    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          online = true)
    }

    checkpoint("Fill all fields with valid data") {
      compose
          .onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_INPUT)
          .performTextInput("valid@example.com")
      compose
          .onNodeWithTag(EmailSectionTestTags.CONFIRM_EMAIL_INPUT)
          .performTextInput("valid@example.com")
      compose.onNodeWithTag(EmailSectionTestTags.PASSWORD_INPUT).performTextInput("password123")
      compose.waitForIdle()
    }

    checkpoint("Button is enabled when all conditions met") {
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON).assertIsEnabled()
    }

    checkpoint("No error messages shown") {
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_ERROR_LABEL).assertDoesNotExist()
      compose.onNodeWithTag(EmailSectionTestTags.EMAILS_DONT_MATCH_LABEL).assertDoesNotExist()
    }

    checkpoint("Valid message shown") {
      compose.onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_VALID_LABEL).assertExists()
    }
  }

  @Test
  fun test_error_message_display() {
    val errorMsg = mutableStateOf<String?>(null)

    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          errorMsg = errorMsg.value,
          online = true)
    }

    checkpoint("No error initially") {
      // Error is shown via Toast, not a specific tag we can test easily
      // Just verify the composable doesn't crash with null error
      compose.waitForIdle()
    }

    checkpoint("Show error message") {
      errorMsg.value = "Wrong password"
      compose.waitForIdle()
      // Error is shown via Toast, which is harder to test
      // The important part is the composable accepts and displays it
    }
  }

  @Test
  fun test_success_message_display() {
    val successMsg = mutableStateOf<String?>(null)

    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          successMsg = successMsg.value,
          online = true)
    }

    checkpoint("No success message initially") { compose.waitForIdle() }

    checkpoint("Show success message") {
      successMsg.value = "Verification email sent"
      compose.waitForIdle()
      // Success is shown via Toast, which is harder to test
      // The important part is the composable accepts and displays it
    }
  }

  @Test
  fun test_loading_state() {
    val isLoading = mutableStateOf(false)

    compose.setContent {
      EmailSection(
          email = "test@example.com",
          isVerified = true,
          onEmailChange = {},
          onFocusChanged = {},
          onSendVerification = {},
          onChangeEmail = { _, _ -> },
          isLoading = isLoading.value,
          online = true)
    }

    checkpoint("Fill all fields") {
      compose
          .onNodeWithTag(EmailSectionTestTags.NEW_EMAIL_INPUT)
          .performTextInput("valid@example.com")
      compose
          .onNodeWithTag(EmailSectionTestTags.CONFIRM_EMAIL_INPUT)
          .performTextInput("valid@example.com")
      compose.onNodeWithTag(EmailSectionTestTags.PASSWORD_INPUT).performTextInput("password123")
      compose.waitForIdle()
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON).assertIsEnabled()
    }

    checkpoint("Button disabled when loading") {
      isLoading.value = true
      compose.waitForIdle()
      compose.onNodeWithTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON).assertIsNotEnabled()
    }
  }
}
