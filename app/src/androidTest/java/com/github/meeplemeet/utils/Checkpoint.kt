package com.github.meeplemeet.utils

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Tiny rule / helper that wraps every test statement and records success / failure together with
 * the stack-trace of the failure.
 *
 * Usage: @get:Rule val ck = Checkpoint.rule() // once per test class ... ck("description") { /* any
 * assertion */ }
 */
class Checkpoint() {

  private val report = linkedMapOf<String, Result<Unit>>()

  /** The wrapper you call from your test. */
  operator fun invoke(name: String, block: () -> Unit) {
    report[name] = runCatching { block() } // OK – block stays inline
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

    override fun starting(description: Description) {}

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
