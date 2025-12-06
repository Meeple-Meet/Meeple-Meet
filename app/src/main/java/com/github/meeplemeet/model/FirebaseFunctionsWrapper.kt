package com.github.meeplemeet.model

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions

/**
 * Interface wrapper around FirebaseFunctions to enable dependency injection and testing.
 *
 * This allows to mock Firebase Cloud Functions calls in tests without needing actual Firebase
 * infrastructure.
 */
interface FirebaseFunctionsWrapper {
  /**
   * Calls a Cloud Function with the given name and parameters.
   *
   * @param name The name of the Cloud Function to call.
   * @param data The parameters to pass to the function.
   * @return A [Task] that will complete with the function's result data.
   */
  fun <T> call(name: String, data: Any? = null): Task<T>

  /**
   * Configures this instance to use a local emulator.
   *
   * @param host The emulator host (e.g., "10.0.2.2").
   * @param port The emulator port (e.g., 5001).
   */
  fun useEmulator(host: String, port: Int)
}

/** Production implementation that delegates to actual FirebaseFunctions. */
class FirebaseFunctionsWrapperImpl(private val functions: FirebaseFunctions) :
    FirebaseFunctionsWrapper {

  override fun <T> call(name: String, data: Any?): Task<T> {
    return functions.getHttpsCallable(name).call(data).continueWith { task ->
      @Suppress("UNCHECKED_CAST")
      task.result.data as T
    }
  }

  override fun useEmulator(host: String, port: Int) {
    functions.useEmulator(host, port)
  }
}
