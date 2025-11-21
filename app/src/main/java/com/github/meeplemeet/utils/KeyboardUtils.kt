package com.github.meeplemeet.utils

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

/**
 * Utility for monitoring soft keyboard visibility changes and dispatching callbacks.
 *
 * Based on https://stackoverflow.com/a/36922142, adapted for Compose usage.
 */
object KeyboardUtils {

  interface SoftKeyboardToggleListener {
    fun onToggleSoftKeyboard(isVisible: Boolean)
  }

  private val toggleListeners = mutableSetOf<SoftKeyboardToggleListener>()
  private val hideCallbacks = mutableSetOf<() -> Unit>()

  private var rootViewRef: WeakReference<View>? = null
  private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
  private var isKeyboardVisible = false

  /**
   * Adds a keyboard toggle listener. The underlying global layout listener is installed on-demand.
   *
   * @param activity host activity
   * @param listener callback invoked when keyboard visibility toggles
   */
  fun addKeyboardToggleListener(activity: Activity, listener: SoftKeyboardToggleListener) {
    ensureGlobalLayoutListener(activity)
    toggleListeners.add(listener)
  }

  /** Removes a previously registered toggle listener. */
  fun removeKeyboardToggleListener(listener: SoftKeyboardToggleListener) {
    toggleListeners.remove(listener)
  }

  /**
   * Registers a callback that fires whenever the keyboard is fully hidden.
   *
   * @return lambda to unregister the callback
   */
  fun registerOnKeyboardHidden(callback: () -> Unit): () -> Unit {
    hideCallbacks.add(callback)
    return { hideCallbacks.remove(callback) }
  }

  /** Detaches the global layout listener. Should be called from Activity.onDestroy(). */
  fun detach(activity: Activity) {
    val view = rootViewRef?.get() ?: activity.findViewById(android.R.id.content)
    globalLayoutListener?.let { listener ->
      view.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
    }
    globalLayoutListener = null
    rootViewRef = null
  }

  private fun ensureGlobalLayoutListener(activity: Activity) {
    if (globalLayoutListener != null) return
    val view = activity.findViewById<View>(android.R.id.content) ?: return
    rootViewRef = WeakReference(view)
    globalLayoutListener =
        ViewTreeObserver.OnGlobalLayoutListener {
          val rect = Rect()
          val content = rootViewRef?.get() ?: return@OnGlobalLayoutListener
          content.getWindowVisibleDisplayFrame(rect)
          val screenHeight = content.rootView.height
          val keyboardHeight = screenHeight - rect.height()
          val visible = keyboardHeight > screenHeight * 0.15
          if (visible != isKeyboardVisible) {
            isKeyboardVisible = visible
            notifyToggle(visible)
          }
        }
    view.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
  }

  private fun notifyToggle(visible: Boolean) {
    toggleListeners.toList().forEach { it.onToggleSoftKeyboard(visible) }
    if (!visible) {
      hideCallbacks.toList().forEach { it() }
    }
  }
}
