package com.github.meeplemeet.utils

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Annotation to disable retry mechanism for a specific test. */
@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class noretry

const val CHECKPOINT_TIMEOUT_MS = 5_000L
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
  operator fun invoke(name: String, block: () -> Unit) {
    val isNoRetry = currentDescription?.getAnnotation(noretry::class.java) != null
    val maxAttempts = if (isNoRetry) 1 else 3

    var attempt = 0
    var lastResult: Result<Unit>? = null

    while (attempt < maxAttempts) {
      attempt++
      lastResult = runCatching { runBlocking { withTimeout(CHECKPOINT_TIMEOUT_MS) { block() } } }
      if (lastResult.isSuccess) break
    }

    report[name] = lastResult ?: Result.failure(RuntimeException("No execution attempted"))
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
    val ck = Checkpoint() // simple instance

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
