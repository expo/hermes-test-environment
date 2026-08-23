package io.github.expo.hermes

/**
 * Loads the native library that carries the Hermes VM and the JSI copy this environment exports
 * (`libhermes-test-env`).
 *
 * The library is resolved through `java.library.path`, so the process must be started with
 * `-Djava.library.path=<runtime/build/native-libs>`.
 */
public object HermesEnv {
  @Volatile
  private var loaded = false

  public fun load() {
    if (loaded) return
    synchronized(this) {
      if (loaded) return
      System.loadLibrary("hermes-test-env")
      loaded = true
    }
  }
}
