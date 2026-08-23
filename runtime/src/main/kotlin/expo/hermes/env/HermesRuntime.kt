package expo.hermes.env

/**
 * A Hermes VM created on the calling thread, exposed to the JVM as the address of its
 * `facebook::jsi::Runtime`.
 *
 * This class knows nothing about what runs inside the runtime: a library under test takes
 * [pointer], attaches its own host objects to that runtime, and drives it. That is the same shape
 * React Native has on Android — the host creates the runtime, the library attaches to it — which is
 * what makes one library work against both.
 *
 * A `jsi::Runtime` is thread-affine, so the thread that constructs this object owns it; [evaluate]
 * and [close] refuse to run anywhere else. Call [close] (or use it as an [AutoCloseable]) to
 * destroy the VM.
 *
 * Constructing this class loads the native library on demand, so no explicit load call is needed.
 */
public class HermesRuntime : AutoCloseable {
  private val owner: Thread = Thread.currentThread()

  private var address: Long = nativeCreate()

  /**
   * The address of the underlying `facebook::jsi::Runtime`, for a native library that attaches to
   * it. Valid until [close].
   */
  public val pointer: Long
    get() {
      checkOpen()
      return address
    }

  /** Whether the underlying VM is still alive. */
  public val isOpen: Boolean
    get() = address != 0L

  /**
   * Evaluates [script] and returns its result coerced to a string.
   *
   * This is the whole JS surface here on purpose — enough to check that a build works. Anything
   * richer belongs in the library under test, which drives the runtime through [pointer].
   */
  public fun evaluate(script: String, sourceUrl: String = "hermes-tests-environment.js"): String {
    checkOpen()
    checkThread()
    return nativeEvaluate(address, script, sourceUrl)
  }

  override fun close() {
    if (address == 0L) return
    checkThread()
    nativeDestroy(address)
    address = 0L
  }

  private fun checkOpen() {
    check(address != 0L) { "this HermesRuntime is closed" }
  }

  private fun checkThread() {
    check(Thread.currentThread() === owner) {
      "a jsi::Runtime is thread-affine: this one is owned by ${owner.name}, " +
        "but the call came from ${Thread.currentThread().name}"
    }
  }

  private companion object {
    init {
      HermesEnv.load()
    }

    @JvmStatic
    private external fun nativeCreate(): Long

    @JvmStatic
    private external fun nativeDestroy(pointer: Long)

    @JvmStatic
    private external fun nativeEvaluate(pointer: Long, script: String, sourceUrl: String): String
  }
}
