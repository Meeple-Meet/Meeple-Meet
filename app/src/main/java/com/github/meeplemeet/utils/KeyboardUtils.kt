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
  private val hideCallbacks = mutableSetOf<() -> Unit>()

  private var rootViewRef: WeakReference<View>? = null
  private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
  private var isKeyboardVisible = false

  /**
   * Registers a callback that fires whenever the keyboard is fully hidden.
   *
   * @param activity The activity to monitor for keyboard visibility changes
   * @return lambda to unregister the callback
   */
  fun registerOnKeyboardHidden(activity: Activity, callback: () -> Unit): () -> Unit {
    hideCallbacks.add(callback)
    ensureGlobalLayoutListener(activity)
    return { hideCallbacks.remove(callback) }
  }

  /**
   * Sets up the global layout listener to detect keyboard visibility changes. Only initializes once
   * per activity.
   */
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

  /**
   * Notifies all registered callbacks when keyboard visibility changes. When keyboard is hidden,
   * triggers all hide callbacks.
   */
  private fun notifyToggle(visible: Boolean) {
    if (!visible) {
      hideCallbacks.toList().forEach { it() }
    }
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
}
