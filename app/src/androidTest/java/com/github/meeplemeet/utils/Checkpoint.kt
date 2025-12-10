package com.github.meeplemeet.utils

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertTrue
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Annotation to disable retry mechanism for a specific test. */
@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class noretry

const val DEFAULT_CHECKPOINT_TIMEOUT_MS = 20_000L

/**
 * Tiny rule / helper that wraps every test statement and records success / failure together with
 * the stack-trace of the failure.
 *
 * Usage: @get:Rule val ck = Checkpoint.rule() // once per test class ... ck("description") { /* any
 * assertion */ }
 */
class Checkpoint {

  private val report = linkedMapOf<String, Result<Unit>>()
  private var currentDescription: Description? = null

  /** The wrapper you call from your test. */
  operator fun invoke(
      name: String,
      block: () -> Unit,
      timeout: Long = DEFAULT_CHECKPOINT_TIMEOUT_MS
  ) {
    val isNoRetry = currentDescription?.getAnnotation(noretry::class.java) != null
    val maxAttempts = if (isNoRetry) 1 else 3

    var attempt = 0
    var lastResult: Result<Unit>? = null
    val executor = Executors.newSingleThreadExecutor()

    try {
      while (attempt < maxAttempts) {
        attempt++
        val future = executor.submit { block() }

        lastResult = runCatching {
          try {
            future.get(timeout, TimeUnit.MILLISECONDS)
            Unit
          } catch (e: TimeoutException) {
            future.cancel(true)
            throw e
          } catch (e: ExecutionException) {
            throw e.cause ?: e
          }
        }

        if (lastResult.isSuccess) break

        // Don't retry on timeout
        if (lastResult.exceptionOrNull() is TimeoutException) {
          break
        }
      }
    } finally {
      if (lastResult?.isSuccess == true) {
        executor.shutdown()
      } else {
        executor.shutdownNow()
      }
    }

    // Record the result
    report[name] = lastResult ?: Result.failure(RuntimeException("No execution attempted"))

    // Fail immediately on timeout or error
    val ex = lastResult?.exceptionOrNull()
    if (ex is TimeoutException) {
      throw RuntimeException("Checkpoint '$name' timed out after ${timeout}ms", ex)
    }
  }

  /** Call this once at the end of the test (automatic if you use the Rule). */
  fun assertAll() {
    val failures = report.filter { it.value.isFailure }
    println("Checkpoint summary: ${report.size - failures.size}/${report.size} OK")

    failures.forEach { (desc, result) ->
      val stack = result.exceptionOrNull()?.stackTraceToString() ?: ""
      Log.e("ERROR", "Checkpoint failed: $desc\n$stack")
    }

    assertTrue("${failures.size} checkpoint(s) failed → ${failures.keys}", failures.isEmpty())
  }

  /* ---------------------------------------------------------------------- */
  /* JUnit-rule glue (optional but handy)                                   */
  /* ---------------------------------------------------------------------- */
  class Rule : TestWatcher() {
    val ck = Checkpoint()

    override fun starting(description: Description) {
      ck.currentDescription = description
    }

    override fun finished(description: Description) = ck.assertAll()
  }

  companion object {
    /** If you like the Rule style: @get:Rule val ck = Checkpoint.rule() */
    @JvmStatic fun rule(): Rule = Rule()
  }
}

/* Extension to get a proper printable stack-trace */
private fun Throwable.stackTraceToString(): String =
    StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()
