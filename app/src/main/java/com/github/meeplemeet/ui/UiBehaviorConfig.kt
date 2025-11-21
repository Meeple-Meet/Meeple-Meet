package com.github.meeplemeet.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global UI configuration flags that allow tests or debug tooling to tweak behavior without adding
 * extra parameters to every composable.
 */
object UiBehaviorConfig {
  /**
   * When true, bottom action bars on form screens are hidden while an input field has focus (i.e.
   * an IME is likely visible). Tests can set this to false to keep the bars visible for assertions.
   */
  var hideBottomBarWhenInputFocused: Boolean by mutableStateOf(true)

  /**
   * When true, [FocusableInputField] automatically clears focus when the IME hides. Tests can
   * toggle this off to avoid unintended focus changes while asserting UI state.
   */
  var clearFocusOnKeyboardHide: Boolean by mutableStateOf(true)
}
