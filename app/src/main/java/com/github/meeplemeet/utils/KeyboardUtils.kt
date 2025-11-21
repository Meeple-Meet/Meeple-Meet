package com.github.meeplemeet.utils

import android.app.Activity
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
}
