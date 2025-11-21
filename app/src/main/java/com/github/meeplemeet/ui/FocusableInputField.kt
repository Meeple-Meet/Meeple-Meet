package com.github.meeplemeet.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.github.meeplemeet.utils.KeyboardUtils

/**
 * Finds the Activity from a Context by traversing the context hierarchy. Returns null if no
 * Activity is found.
 */
private fun Context.findActivity(): Activity? {
  var context = this
  while (context is ContextWrapper) {
    if (context is Activity) return context
    context = context.baseContext
  }
  return null
}

private val NoOpFocusableObserver: (Any, Boolean) -> Unit = { _, _ -> }

val LocalFocusableFieldObserver =
    staticCompositionLocalOf<(Any, Boolean) -> Unit> { NoOpFocusableObserver }

/**
 * Wraps [OutlinedTextField] and automatically clears focus when the soft keyboard is dismissed via
 * its system toggle, preventing fields from staying focused unintentionally.
 */
@Composable
fun FocusableInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    onFocusChanged: (Boolean) -> Unit = {}
) {
  var hasFocus by remember { mutableStateOf(false) }
  val latestHasFocus by rememberUpdatedState(hasFocus)
  val focusManager = LocalFocusManager.current
  val globalObserver = LocalFocusableFieldObserver.current
  val focusToken = remember { Any() }
  val activity = LocalContext.current.findActivity()

  DisposableEffect(focusManager, UiBehaviorConfig.clearFocusOnKeyboardHide) {
    if (!UiBehaviorConfig.clearFocusOnKeyboardHide) return@DisposableEffect onDispose {}
    val unregister =
        activity?.let { act ->
          KeyboardUtils.registerOnKeyboardHidden(act) {
            if (latestHasFocus) focusManager.clearFocus(force = true)
          }
        }
    onDispose { unregister?.invoke() }
  }

  DisposableEffect(focusToken) { onDispose { globalObserver(focusToken, false) } }

  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier =
          modifier.onFocusChanged {
            if (hasFocus == it.isFocused) return@onFocusChanged
            hasFocus = it.isFocused
            onFocusChanged(it.isFocused)
            globalObserver(focusToken, it.isFocused)
          },
      enabled = enabled,
      readOnly = readOnly,
      textStyle = textStyle,
      label = label,
      placeholder = placeholder,
      leadingIcon = leadingIcon,
      trailingIcon = trailingIcon,
      supportingText = supportingText,
      isError = isError,
      visualTransformation = visualTransformation,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      singleLine = singleLine,
      maxLines = maxLines,
      minLines = minLines,
      interactionSource = interactionSource,
      shape = shape,
      colors = colors)
}
