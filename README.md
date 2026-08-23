# hermes-tests-environment

A Hermes host for testing JSI libraries without React Native. It creates a
`facebook::jsi::Runtime`, hands its address to the JVM, and ships the one process-wide copy of JSI
that the library under test links against. fbjni rides along, for consumers that benchmark their JNI
dispatch against it.

Published as `io.github.expo:hermes-test-environment`.

## Build

Requires macOS or Linux, CMake, Ninja, and a JDK. The first build compiles the Hermes VM, so it
takes a few minutes; after that only this repository's own sources rebuild.

```sh
git submodule update --init --recursive
./gradlew :runtime:test
```

## Publish

```sh
./gradlew :runtime:publishToMavenLocal
```

One command builds everything and produces a self-contained artifact — a consumer needs no Hermes
checkout, no C++ toolchain, and no build step beyond unpacking the zips:

| Artifact | Contents |
| --- | --- |
| jar | `io.github.expo.hermes.HermesRuntime`, `HermesEnv` |
| `jsi-cpp@zip` | JSI headers, plus `JSIDynamic.cpp` |
| `fbjni-cpp@zip` | fbjni headers |
| `native-libs@zip` | `libhermes-test-env.dylib` (VM + JSI) and `libfbjni.dylib` |

## Use

```kotlin
HermesRuntime().use { runtime ->
  runtime.evaluate("1 + 2")     // "3" — a smoke test
  attachMyLibrary(runtime.pointer)   // the real surface: a facebook::jsi::Runtime*
}
```

Link your own library against `libhermes-test-env.dylib` and compile against the headers from
`jsi-cpp@zip`. Never compile a second copy of JSI: two copies disagree about object layout and carry
distinct vtables and typeinfos, which breaks `dynamic_cast` and `catch` across library boundaries.
Where React Native is present, it is the one that provides JSI, and this artifact stays out of the
link.

Run the JVM with `-Djava.library.path=<dir holding the unpacked native-libs>`. A `jsi::Runtime` is
thread-affine: the thread that constructs `HermesRuntime` owns it, and `evaluate`/`close` refuse to
run anywhere else.
