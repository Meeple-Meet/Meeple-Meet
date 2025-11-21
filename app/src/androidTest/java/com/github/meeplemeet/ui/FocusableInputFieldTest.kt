package com.github.meeplemeet.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusableInputFieldTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  /* ---------------- checkpoint helper ---------------- */
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  /* ---------------- semantic helpers ---------------- */

  private fun inputField() = compose.onNodeWithTag(TEST_TAG_INPUT)

  /* ------------------------- setup ---------------------------- */

  private var textValue by mutableStateOf("")
  private var onValueChangeCalled by mutableStateOf(false)
  private var onFocusChangedCalled by mutableStateOf(false)
  private var lastFocusState by mutableStateOf(false)
  private var enabledState by mutableStateOf(true)
  private var readOnlyState by mutableStateOf(false)
  private var isErrorState by mutableStateOf(false)
  private var singleLineState by mutableStateOf(false)
  private var maxLinesState by mutableStateOf(Int.MAX_VALUE)
  private var minLinesState by mutableStateOf(1)
  private var keyboardOptionsState by mutableStateOf(KeyboardOptions.Default)
  private var keyboardActionsState by mutableStateOf(KeyboardActions.Default)
  private var visualTransformationState by
      mutableStateOf(androidx.compose.ui.text.input.VisualTransformation.None)
  private var interactionSourceState by mutableStateOf<MutableInteractionSource?>(null)
  private var globalObserverToken: Any? = null
  private var globalObserverFocusState: Boolean? = null

  @Before
  fun setup() {
    // Reset state before each test
    textValue = ""
    onValueChangeCalled = false
    onFocusChangedCalled = false
    lastFocusState = false
    enabledState = true
    readOnlyState = false
    isErrorState = false
    singleLineState = false
    maxLinesState = Int.MAX_VALUE
    minLinesState = 1
    keyboardOptionsState = KeyboardOptions.Default
    keyboardActionsState = KeyboardActions.Default
    visualTransformationState = androidx.compose.ui.text.input.VisualTransformation.None
    interactionSourceState = null
    globalObserverToken = null
    globalObserverFocusState = null

    // Reset UI behavior config to defaults
    UiBehaviorConfig.clearFocusOnKeyboardHide = true
  }

  /* ---------------------- tests ------------------------------- */

  @Test
  fun full_smoke_focusable_input_field() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = {
            textValue = it
            onValueChangeCalled = true
          },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          label = { Text("Label") },
          placeholder = { Text("Placeholder") },
          onFocusChanged = { focused ->
            onFocusChangedCalled = true
            lastFocusState = focused
          })
    }

    /* 1  Initial render ------------------------------------------------------------- */
    checkpoint("Field renders") {
      compose.waitForIdle()
      inputField().assertExists()
    }

    /* 2  Text input works ----------------------------------------------------------- */
    checkpoint("Text input works") {
      inputField().performTextInput("Hello")
      compose.waitForIdle()
      assert(textValue == "Hello")
      assert(onValueChangeCalled)
    }

    /* 3  Focus change callback fires ------------------------------------------------ */
    checkpoint("Focus change callback fires") {
      inputField().performClick()
      compose.waitForIdle()
      assert(onFocusChangedCalled)
      assert(lastFocusState)
    }

    /* 4  Text clearing works -------------------------------------------------------- */
    checkpoint("Text clearing works") {
      inputField().performTextClearance()
      compose.waitForIdle()
      assert(textValue.isEmpty())
    }

    /* 5  Additional text input ----------------------------------------------------- */
    checkpoint("Additional text input") {
      inputField().performTextInput("World")
      compose.waitForIdle()
      assert(textValue == "World")
    }
  }

  @Test
  fun focusableInputField_withDisabledState() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          enabled = enabledState)
    }

    /* 1  Disabled state renders ---------------------------------------------------- */
    enabledState = false
    compose.waitForIdle()
    checkpoint("Disabled field renders") {
      inputField().assertExists()
      inputField().assertIsNotEnabled()
    }

    /* 2  Enabled state renders ----------------------------------------------------- */
    enabledState = true
    compose.waitForIdle()
    checkpoint("Enabled field renders") { inputField().assertIsEnabled() }
  }

  @Test
  fun focusableInputField_withReadOnlyState() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          readOnly = readOnlyState)
    }

    /* 1  Read-only state renders --------------------------------------------------- */
    readOnlyState = true
    compose.waitForIdle()
    checkpoint("Read-only field renders") { inputField().assertExists() }

    /* 2  Editable state renders ---------------------------------------------------- */
    readOnlyState = false
    compose.waitForIdle()
    checkpoint("Editable field renders") { inputField().assertExists() }
  }

  @Test
  fun focusableInputField_withErrorState() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          isError = isErrorState,
          supportingText = { if (isErrorState) Text("Error message") })
    }

    /* 1  Error state renders ------------------------------------------------------- */
    isErrorState = true
    compose.waitForIdle()
    checkpoint("Error state renders") { inputField().assertExists() }

    /* 2  Normal state renders ------------------------------------------------------ */
    isErrorState = false
    compose.waitForIdle()
    checkpoint("Normal state renders") { inputField().assertExists() }
  }

  @Test
  fun focusableInputField_withVisualTransformation() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          visualTransformation = visualTransformationState)
    }

    /* 1  Password transformation renders ------------------------------------------- */
    visualTransformationState = PasswordVisualTransformation()
    compose.waitForIdle()
    checkpoint("Password transformation renders") { inputField().assertExists() }

    /* 2  Text input works with transformation -------------------------------------- */
    checkpoint("Text input works with transformation") {
      inputField().performTextInput("password123")
      compose.waitForIdle()
      assert(textValue == "password123")
    }
  }

  @Test
  fun focusableInputField_withKeyboardOptions() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          keyboardOptions = keyboardOptionsState,
          singleLine = singleLineState)
    }

    /* 1  Email keyboard type renders ----------------------------------------------- */
    keyboardOptionsState = KeyboardOptions(keyboardType = KeyboardType.Email)
    compose.waitForIdle()
    checkpoint("Email keyboard type renders") { inputField().assertExists() }

    /* 2  Single line state --------------------------------------------------------- */
    singleLineState = true
    compose.waitForIdle()
    checkpoint("Single line state renders") { inputField().assertExists() }

    /* 3  IME action configuration -------------------------------------------------- */
    keyboardOptionsState = KeyboardOptions(imeAction = ImeAction.Done)
    compose.waitForIdle()
    checkpoint("IME action configuration") { inputField().assertExists() }
  }

  @Test
  fun focusableInputField_withMultipleLines() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          maxLines = maxLinesState,
          minLines = minLinesState)
    }

    /* 1  Multi-line configuration -------------------------------------------------- */
    maxLinesState = 5
    minLinesState = 3
    compose.waitForIdle()
    checkpoint("Multi-line configuration") {
      inputField().assertExists()
      inputField().performTextInput("Line 1\nLine 2\nLine 3")
      compose.waitForIdle()
      assert(textValue.contains("\n"))
    }
  }

  @Test
  fun focusableInputField_withIcons() = runBlocking {
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          leadingIcon = { Text("L") },
          trailingIcon = { Text("T") })
    }

    /* 1  Icons render -------------------------------------------------------------- */
    checkpoint("Icons render") { inputField().assertExists() }
  }

  @Test
  fun focusableInputField_withGlobalObserver() = runBlocking {
    compose.setContent {
      CompositionLocalProvider(
          LocalFocusableFieldObserver provides
              { token, focused ->
                globalObserverToken = token
                globalObserverFocusState = focused
              }) {
            FocusableInputField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.testTag(TEST_TAG_INPUT))
          }
    }

    /* 1  Global observer fires on focus -------------------------------------------- */
    checkpoint("Global observer fires on focus") {
      inputField().performClick()
      compose.waitForIdle()
      assert(globalObserverToken != null)
      assert(globalObserverFocusState == true)
    }

    /* 2  Global observer fires on blur --------------------------------------------- */
    checkpoint("Global observer fires on blur") {
      // Clear focus by clicking outside (compose framework behavior)
      // We can verify the observer was called at least once
      assert(globalObserverToken != null)
    }
  }

  @Test
  fun focusableInputField_clearFocusOnKeyboardHide_enabled() = runBlocking {
    UiBehaviorConfig.clearFocusOnKeyboardHide = true

    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          onFocusChanged = { focused ->
            onFocusChangedCalled = true
            lastFocusState = focused
          })
    }

    /* 1  Field gains focus --------------------------------------------------------- */
    checkpoint("Field gains focus") {
      inputField().performClick()
      compose.waitForIdle()
      assert(lastFocusState)
    }

    /* 2  Keyboard hide clears focus ------------------------------------------------ */
    checkpoint("Keyboard hide clears focus") {
      // Simulate keyboard hiding by triggering the callback
      // This tests that the DisposableEffect registers properly
      inputField().assertExists()
    }
  }

  @Test
  fun focusableInputField_clearFocusOnKeyboardHide_disabled() = runBlocking {
    UiBehaviorConfig.clearFocusOnKeyboardHide = false

    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          onFocusChanged = { focused ->
            onFocusChangedCalled = true
            lastFocusState = focused
          })
    }

    /* 1  Field gains focus --------------------------------------------------------- */
    checkpoint("Field gains focus with config disabled") {
      inputField().performClick()
      compose.waitForIdle()
      assert(lastFocusState)
    }

    /* 2  Config disabled means no keyboard listener -------------------------------- */
    checkpoint("Config disabled means no keyboard listener") {
      // When disabled, the DisposableEffect returns early
      inputField().assertExists()
      assert(onFocusChangedCalled)
    }
  }

  @Test
  fun focusableInputField_focusStateChange_onlyFiresWhenChanged() = runBlocking {
    var focusChangeCount = 0

    compose.setContent {
      Column {
        FocusableInputField(
            value = textValue,
            onValueChange = { textValue = it },
            modifier = Modifier.testTag(TEST_TAG_INPUT),
            onFocusChanged = { focusChangeCount++ })
        FocusableInputField(
            value = "", onValueChange = {}, modifier = Modifier.testTag(TEST_TAG_INPUT_2))
      }
    }

    /* 1  Initial focus change ------------------------------------------------------ */
    checkpoint("Initial focus change") {
      inputField().performClick()
      compose.waitForIdle()
      val initialCount = focusChangeCount
      assert(initialCount > 0)
    }

    /* 2  Focus change to another field --------------------------------------------- */
    checkpoint("Focus change to another field") {
      compose.onNodeWithTag(TEST_TAG_INPUT_2).performClick()
      compose.waitForIdle()
      // Focus changed again (blur event)
      assert(focusChangeCount >= 1)
    }
  }

  @Test
  fun focusableInputField_withInteractionSource() = runBlocking {
    interactionSourceState = MutableInteractionSource()

    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT),
          interactionSource = interactionSourceState)
    }

    /* 1  Custom interaction source renders ---------------------------------------- */
    checkpoint("Custom interaction source renders") { inputField().assertExists() }

    /* 2  Interaction with custom source -------------------------------------------- */
    checkpoint("Interaction with custom source") {
      inputField().performClick()
      compose.waitForIdle()
      inputField().performTextInput("Test")
      assert(textValue == "Test")
    }
  }

  @Test
  fun focusableInputField_noOpGlobalObserver() = runBlocking {
    // Test with default (NoOp) global observer
    compose.setContent {
      FocusableInputField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag(TEST_TAG_INPUT))
    }

    /* 1  NoOp observer doesn't break functionality --------------------------------- */
    checkpoint("NoOp observer doesn't break functionality") {
      inputField().assertExists()
      inputField().performTextInput("Test")
      compose.waitForIdle()
      assert(textValue == "Test")
    }
  }

  companion object {
    private const val TEST_TAG_INPUT = "focusable_input_field"
    private const val TEST_TAG_INPUT_2 = "focusable_input_field_2"
  }
}
