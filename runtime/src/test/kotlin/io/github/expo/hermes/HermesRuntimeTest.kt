package io.github.expo.hermes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HermesRuntimeTest {
  @Test
  fun `evaluates a script`() {
    HermesRuntime().use { runtime ->
      assertEquals("3", runtime.evaluate("1 + 2"))
      assertEquals("hello", runtime.evaluate("'hel' + 'lo'"))
    }
  }

  @Test
  fun `keeps state between evaluations`() {
    HermesRuntime().use { runtime ->
      runtime.evaluate("globalThis.counter = 0")
      repeat(3) { runtime.evaluate("globalThis.counter += 1") }
      assertEquals("3", runtime.evaluate("globalThis.counter"))
    }
  }

  @Test
  fun `hands out a runtime pointer`() {
    HermesRuntime().use { runtime ->
      assertTrue(runtime.pointer != 0L)
      assertTrue(runtime.isOpen)
    }
  }

  @Test
  fun `reports a JS error as an exception`() {
    HermesRuntime().use { runtime ->
      val error = assertFailsWith<RuntimeException> { runtime.evaluate("throw new Error('boom')") }
      assertTrue(error.message!!.contains("boom"), "unexpected message: ${error.message}")
      // The runtime survives a thrown error.
      assertEquals("1", runtime.evaluate("1"))
    }
  }

  @Test
  fun `closing twice is a no-op`() {
    val runtime = HermesRuntime()
    runtime.close()
    runtime.close()
    assertFalse(runtime.isOpen)
    assertFailsWith<IllegalStateException> { runtime.evaluate("1") }
  }

  @Test
  fun `refuses calls from another thread`() {
    HermesRuntime().use { runtime ->
      var failure: Throwable? = null
      val thread = Thread { failure = runCatching { runtime.evaluate("1") }.exceptionOrNull() }
      thread.start()
      thread.join()
      assertTrue(failure is IllegalStateException, "unexpected failure: $failure")
    }
  }
}
