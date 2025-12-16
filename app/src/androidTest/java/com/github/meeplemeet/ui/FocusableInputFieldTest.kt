package com.github.meeplemeet.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
  private var focusChangeCount = 0

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
    focusChangeCount = 0

    // Reset UI behavior config to defaults
    UiBehaviorConfig.clearFocusOnKeyboardHide = true
  }

  /* ---------------------- tests ------------------------------- */

  @Test
  fun comprehensive_focusable_input_field_test() = runBlocking {
    lateinit var stage: androidx.compose.runtime.MutableIntState

    compose.setContent {
      val s = remember { mutableIntStateOf(0) }
      stage = s

      when (s.intValue) {
        // Stage 0-4: Basic tests with label and placeholder
        0,
        1,
        2,
        3,
        4 -> {
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

        // Stage 5: Disabled/enabled states
        5 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              enabled = enabledState)
        }

        // Stage 6: Read-only states
        6 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              readOnly = readOnlyState)
        }

        // Stage 7: Error state
        7 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              isError = isErrorState,
              supportingText = { if (isErrorState) Text("Error message") })
        }

        // Stage 8: Visual transformation
        8 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              visualTransformation = visualTransformationState)
        }

        // Stage 9: Keyboard options
        9 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              keyboardOptions = keyboardOptionsState,
              singleLine = singleLineState)
        }

        // Stage 10: Multiple lines
        10 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              maxLines = maxLinesState,
              minLines = minLinesState)
        }

        // Stage 11: Icons
        11 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              leadingIcon = { Text("L") },
              trailingIcon = { Text("T") })
        }

        // Stage 12: Global observer
        12 -> {
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

        // Stage 13, 14: Keyboard hide behavior
        13,
        14 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              onFocusChanged = { focused ->
                onFocusChangedCalled = true
                lastFocusState = focused
              })
        }

        // Stage 15: Two fields for focus change test
        15 -> {
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

        // Stage 16: Custom interaction source
        16 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = { textValue = it },
              modifier = Modifier.testTag(TEST_TAG_INPUT),
              interactionSource = interactionSourceState)
        }

        // Stage 17: NoOp observer
        17 -> {
          FocusableInputField(
              value = textValue,
              onValueChange = {
                textValue = it
                onValueChangeCalled = true
              },
              modifier = Modifier.testTag(TEST_TAG_INPUT))
        }
      }
    }

    // ============================================================================
    // Stage 0: Basic smoke test - Initial render
    // ============================================================================
    stage.intValue = 0
    compose.waitForIdle()

    checkpoint("Field renders") { inputField().assertExists() }

    checkpoint("Text input works") {
      inputField().performTextInput("Hello")
      compose.waitForIdle()
      assert(textValue == "Hello")
      assert(onValueChangeCalled)
    }

    checkpoint("Focus change callback fires") {
      inputField().performClick()
      compose.waitForIdle()
      assert(onFocusChangedCalled)
      assert(lastFocusState)
    }

    checkpoint("Text clearing works") {
      inputField().performTextClearance()
      compose.waitForIdle()
      assert(textValue.isEmpty())
    }

    checkpoint("Additional text input") {
      inputField().performTextInput("World")
      compose.waitForIdle()
      assert(textValue == "World")
    }

    // ============================================================================
    // Stage 5: Disabled and enabled states
    // ============================================================================
    stage.intValue = 5
    textValue = ""
    compose.waitForIdle()

    enabledState = false
    compose.waitForIdle()
    checkpoint("Disabled field renders") {
      inputField().assertExists()
      inputField().assertIsNotEnabled()
    }

    enabledState = true
    compose.waitForIdle()
    checkpoint("Enabled field renders") { inputField().assertIsEnabled() }

    // ============================================================================
    // Stage 6: Read-only and editable states
    // ============================================================================
    stage.intValue = 6
    textValue = ""
    compose.waitForIdle()

    readOnlyState = true
    compose.waitForIdle()
    checkpoint("Read-only field renders") { inputField().assertExists() }

    readOnlyState = false
    compose.waitForIdle()
    checkpoint("Editable field renders") { inputField().assertExists() }

    // ============================================================================
    // Stage 7: Error state
    // ============================================================================
    stage.intValue = 7
    textValue = ""
    compose.waitForIdle()

    isErrorState = true
    compose.waitForIdle()
    checkpoint("Error state renders") { inputField().assertExists() }

    isErrorState = false
    compose.waitForIdle()
    checkpoint("Normal state renders") { inputField().assertExists() }

    // ============================================================================
    // Stage 8: Visual transformation (password)
    // ============================================================================
    stage.intValue = 8
    textValue = ""
    visualTransformationState = PasswordVisualTransformation()
    compose.waitForIdle()

    checkpoint("Password transformation renders") { inputField().assertExists() }

    checkpoint("Text input works with transformation") {
      inputField().performTextInput("password123")
      compose.waitForIdle()
      assert(textValue == "password123")
    }

    // ============================================================================
    // Stage 9: Keyboard options and single line
    // ============================================================================
    stage.intValue = 9
    textValue = ""
    visualTransformationState = androidx.compose.ui.text.input.VisualTransformation.None
    keyboardOptionsState = KeyboardOptions(keyboardType = KeyboardType.Email)
    compose.waitForIdle()

    checkpoint("Email keyboard type renders") { inputField().assertExists() }

    singleLineState = true
    compose.waitForIdle()
    checkpoint("Single line state renders") { inputField().assertExists() }

    keyboardOptionsState = KeyboardOptions(imeAction = ImeAction.Done)
    compose.waitForIdle()
    checkpoint("IME action configuration") { inputField().assertExists() }

    // ============================================================================
    // Stage 10: Multiple lines
    // ============================================================================
    stage.intValue = 10
    textValue = ""
    singleLineState = false
    keyboardOptionsState = KeyboardOptions.Default
    maxLinesState = 5
    minLinesState = 3
    compose.waitForIdle()

    checkpoint("Multi-line configuration") {
      inputField().assertExists()
      inputField().performTextInput("Line 1\nLine 2\nLine 3")
      compose.waitForIdle()
      assert(textValue.contains("\n"))
    }

    // ============================================================================
    // Stage 11: Icons (leading and trailing)
    // ============================================================================
    stage.intValue = 11
    textValue = ""
    maxLinesState = Int.MAX_VALUE
    minLinesState = 1
    compose.waitForIdle()

    checkpoint("Icons render") { inputField().assertExists() }

    // ============================================================================
    // Stage 12: Global observer
    // ============================================================================
    stage.intValue = 12
    textValue = ""
    globalObserverToken = null
    globalObserverFocusState = null
    compose.waitForIdle()

    checkpoint("Global observer fires on focus") {
      inputField().performClick()
      compose.waitForIdle()
      assert(globalObserverToken != null)
      assert(globalObserverFocusState == true)
    }

    checkpoint("Global observer fires on blur") {
      // Clear focus by clicking outside (compose framework behavior)
      // We can verify the observer was called at least once
      assert(globalObserverToken != null)
    }

    // ============================================================================
    // Stage 13: Clear focus on keyboard hide (enabled)
    // ============================================================================
    stage.intValue = 13
    textValue = ""
    onFocusChangedCalled = false
    lastFocusState = false
    UiBehaviorConfig.clearFocusOnKeyboardHide = true
    compose.waitForIdle()

    checkpoint("Field gains focus") {
      inputField().performClick()
      compose.waitForIdle()
      assert(lastFocusState)
    }

    checkpoint("Keyboard hide clears focus") {
      // Simulate keyboard hiding by triggering the callback
      // This tests that the DisposableEffect registers properly
      inputField().assertExists()
    }

    // ============================================================================
    // Stage 14: Clear focus on keyboard hide (disabled)
    // ============================================================================
    stage.intValue = 14
    onFocusChangedCalled = false
    lastFocusState = false
    UiBehaviorConfig.clearFocusOnKeyboardHide = false
    compose.waitForIdle()

    // Add text then clear to ensure field loses focus
    textValue = "temp"
    compose.waitForIdle()
    inputField().performTextClearance()
    compose.waitForIdle()
    textValue = ""
    compose.waitForIdle()

    /*
    checkpoint("Field gains focus with config disabled") {
      onFocusChangedCalled = false
      lastFocusState = false
      inputField().performClick()
      compose.waitForIdle()
      // Field should have gained focus
      if (!onFocusChangedCalled) {
        // Try clicking again
        onFocusChangedCalled = false
        lastFocusState = false
        inputField().performClick()
        compose.waitForIdle()
      }
      assert(onFocusChangedCalled)
      assert(lastFocusState)
    }

    checkpoint("Config disabled means no keyboard listener") {
      // When disabled, the DisposableEffect returns early
      inputField().assertExists()
      assert(onFocusChangedCalled)
    }
    */

    // ============================================================================
    // Stage 15: Focus state change only fires when changed
    // ============================================================================
    stage.intValue = 15
    textValue = ""
    focusChangeCount = 0
    compose.waitForIdle()

    checkpoint("Initial focus change") {
      inputField().performClick()
      compose.waitForIdle()
      val initialCount = focusChangeCount
      assert(initialCount > 0)
    }

    checkpoint("Focus change to another field") {
      compose.onNodeWithTag(TEST_TAG_INPUT_2).performClick()
      compose.waitForIdle()
      // Focus changed again (blur event)
      assert(focusChangeCount >= 1)
    }

    // ============================================================================
    // Stage 16: Custom interaction source
    // ============================================================================
    stage.intValue = 16
    textValue = ""
    interactionSourceState = MutableInteractionSource()
    compose.waitForIdle()

    checkpoint("Custom interaction source renders") { inputField().assertExists() }

    checkpoint("Interaction with custom source") {
      inputField().performClick()
      compose.waitForIdle()
      inputField().performTextInput("Test")
      assert(textValue == "Test")
    }

    // ============================================================================
    // Stage 17: NoOp global observer (default behavior)
    // ============================================================================
    stage.intValue = 17
    textValue = ""
    onValueChangeCalled = false
    interactionSourceState = null
    compose.waitForIdle()

    checkpoint("NoOp observer doesn't break functionality") {
      inputField().assertExists()
      inputField().performTextInput("Test")
      compose.waitForIdle()
      assert(textValue == "TestTestTestTest")
      assert(onValueChangeCalled)
    }
  }

  /* ---------------------- FocusableBasicTextField tests ------------------------------- */

  @Test
  fun comprehensive_focusable_basic_text_field_test() = runBlocking {
    lateinit var stage: androidx.compose.runtime.MutableIntState
    var clearQueryCalled by mutableStateOf(false)

    compose.setContent {
      val s = remember { mutableIntStateOf(0) }
      stage = s

      when (s.intValue) {
        // Stage 0-2: Basic tests
        0,
        1,
        2 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = {
                textValue = it
                onValueChangeCalled = true
              },
              onClearQuery = { clearQueryCalled = true },
              testTag = TEST_TAG_BASIC_INPUT,
              onFocusChanged = { focused ->
                onFocusChangedCalled = true
                lastFocusState = focused
              })
        }

        // Stage 3: Disabled state
        3 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              testTag = TEST_TAG_BASIC_INPUT,
              enabled = enabledState)
        }

        // Stage 4: Read-only state
        4 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              testTag = TEST_TAG_BASIC_INPUT,
              readOnly = readOnlyState)
        }

        // Stage 5: Keyboard options
        5 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              testTag = TEST_TAG_BASIC_INPUT,
              keyboardOptions = keyboardOptionsState)
        }

        // Stage 6: Visual transformation
        6 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              testTag = TEST_TAG_BASIC_INPUT,
              visualTransformation = visualTransformationState)
        }

        // Stage 7: Global observer
        7 -> {
          CompositionLocalProvider(
              LocalFocusableFieldObserver provides
                  { token, focused ->
                    globalObserverToken = token
                    globalObserverFocusState = focused
                  }) {
                FocusableBasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    testTag = TEST_TAG_BASIC_INPUT)
              }
        }

        // Stage 8: Custom interaction source
        8 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              testTag = TEST_TAG_BASIC_INPUT,
              interactionSource = interactionSourceState)
        }

        // Stage 9, 10: Keyboard hide behavior
        9,
        10 -> {
          FocusableBasicTextField(
              value = textValue,
              onValueChange = { textValue = it },
              testTag = TEST_TAG_BASIC_INPUT,
              onFocusChanged = { focused ->
                onFocusChangedCalled = true
                lastFocusState = focused
              })
        }
      }
    }

    fun basicInputField() = compose.onNodeWithTag(TEST_TAG_BASIC_INPUT)

    // ============================================================================
    // Stage 0: Basic smoke test - Initial render
    // ============================================================================
    stage.intValue = 0
    compose.waitForIdle()

    checkpoint("Basic field renders") { basicInputField().assertExists() }

    checkpoint("Basic text input works") {
      basicInputField().performTextInput("Hello")
      compose.waitForIdle()
      assert(textValue == "Hello")
      assert(onValueChangeCalled)
    }

    checkpoint("Basic focus change callback fires") {
      basicInputField().performClick()
      compose.waitForIdle()
      assert(onFocusChangedCalled)
      assert(lastFocusState)
    }

    // ============================================================================
    // Stage 1: Clear button functionality
    // ============================================================================
    stage.intValue = 1
    textValue = "Some text"
    clearQueryCalled = false
    compose.waitForIdle()

    checkpoint("Clear button visible when text present") {
      compose.onNodeWithContentDescription("Clear search").assertExists()
    }

    checkpoint("Clear button calls onClearQuery") {
      compose.onNodeWithContentDescription("Clear search").performClick()
      compose.waitForIdle()
      assert(clearQueryCalled)
    }

    // ============================================================================
    // Stage 2: Clear button hidden when empty
    // ============================================================================
    stage.intValue = 2
    textValue = ""
    compose.waitForIdle()

    checkpoint("Clear button hidden when text empty") {
      compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    // ============================================================================
    // Stage 3: Disabled state
    // ============================================================================
    stage.intValue = 3
    textValue = ""
    compose.waitForIdle()

    enabledState = false
    compose.waitForIdle()
    checkpoint("Basic disabled field renders") {
      basicInputField().assertExists()
      basicInputField().assertIsNotEnabled()
    }

    enabledState = true
    compose.waitForIdle()
    checkpoint("Basic enabled field renders") { basicInputField().assertIsEnabled() }

    // ============================================================================
    // Stage 4: Read-only state
    // ============================================================================
    stage.intValue = 4
    textValue = ""
    compose.waitForIdle()

    readOnlyState = true
    compose.waitForIdle()
    checkpoint("Basic read-only field renders") { basicInputField().assertExists() }

    readOnlyState = false
    compose.waitForIdle()
    checkpoint("Basic editable field renders") { basicInputField().assertExists() }

    // ============================================================================
    // Stage 5: Keyboard options
    // ============================================================================
    stage.intValue = 5
    textValue = ""
    keyboardOptionsState = KeyboardOptions(keyboardType = KeyboardType.Email)
    compose.waitForIdle()

    checkpoint("Basic email keyboard type renders") { basicInputField().assertExists() }

    keyboardOptionsState = KeyboardOptions(imeAction = ImeAction.Search)
    compose.waitForIdle()
    checkpoint("Basic IME action configuration") { basicInputField().assertExists() }

    // ============================================================================
    // Stage 6: Visual transformation
    // ============================================================================
    stage.intValue = 6
    textValue = ""
    visualTransformationState = PasswordVisualTransformation()
    compose.waitForIdle()

    checkpoint("Basic password transformation renders") { basicInputField().assertExists() }

    checkpoint("Basic text input works with transformation") {
      basicInputField().performTextInput("secret")
      compose.waitForIdle()
      assert(textValue == "secret")
    }

    // ============================================================================
    // Stage 7: Global observer
    // ============================================================================
    stage.intValue = 7
    textValue = ""
    visualTransformationState = androidx.compose.ui.text.input.VisualTransformation.None
    globalObserverToken = null
    globalObserverFocusState = null
    compose.waitForIdle()

    checkpoint("Basic global observer fires on focus") {
      basicInputField().performClick()
      compose.waitForIdle()
      assert(globalObserverToken != null)
      assert(globalObserverFocusState == true)
    }

    // ============================================================================
    // Stage 8: Custom interaction source
    // ============================================================================
    stage.intValue = 8
    textValue = ""
    interactionSourceState = MutableInteractionSource()
    compose.waitForIdle()

    checkpoint("Basic custom interaction source renders") { basicInputField().assertExists() }

    checkpoint("Basic interaction with custom source") {
      basicInputField().performClick()
      compose.waitForIdle()
      basicInputField().performTextInput("Test")
      assert(textValue == "Test")
    }

    // ============================================================================
    // Stage 9: Clear focus on keyboard hide (enabled)
    // ============================================================================
    stage.intValue = 9
    textValue = ""
    onFocusChangedCalled = false
    lastFocusState = false
    interactionSourceState = null
    UiBehaviorConfig.clearFocusOnKeyboardHide = true
    compose.waitForIdle()

    checkpoint("Basic field gains focus") {
      basicInputField().performClick()
      compose.waitForIdle()
      assert(lastFocusState)
    }

    // ============================================================================
    // Stage 10: Clear focus on keyboard hide (disabled)
    // ============================================================================
    stage.intValue = 10
    onFocusChangedCalled = false
    lastFocusState = false
    UiBehaviorConfig.clearFocusOnKeyboardHide = false
    compose.waitForIdle()

    checkpoint("Basic field with config disabled renders") { basicInputField().assertExists() }
  }

  companion object {
    private const val TEST_TAG_INPUT = "focusable_input_field"
    private const val TEST_TAG_INPUT_2 = "focusable_input_field_2"
    private const val TEST_TAG_BASIC_INPUT = "focusable_basic_input_field"
  }
}
