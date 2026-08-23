# hermes-tests-environment

A Hermes host for testing JSI libraries **without React Native**. It creates a
`facebook::jsi::Runtime`, hands its address to the JVM, and carries the one process-wide copy of
JSI that the library under test links against.

Extracted from `expo-modules-android-v2`, where this lived in its `:hermes` module. It knows nothing
about Expo: a library takes the runtime pointer and attaches its own host objects to it.

## Why it exists

On Android with React Native, RN creates the `jsi::Runtime` and ships `libjsi.so`; a JSI library
just attaches to what the host gives it. Off that path there is no host — so a desktop JVM test, or
an Android test in an app without React Native, has nothing to attach to. This repo is that host, so
one library can be exercised in all three environments through the same seam: *someone hands me a
`jsi::Runtime*`*.

## Who provides JSI

Exactly one copy of JSI may exist in a process — two copies disagree about object layout, and their
vtables and typeinfos are distinct, which breaks `dynamic_cast` and `catch` across library
boundaries. So the rule is:

| Environment | Runtime created by | JSI comes from |
| --- | --- | --- |
| Desktop JVM | this repo | this repo (`libhermes-test-env`) |
| Android, no React Native | this repo | this repo |
| Android with React Native | React Native | React Native (`ReactAndroid::jsi` prefab) |

**When React Native is installed it provides JSI, and this repo must stay out of the link.** The
consumer's CMake picks the provider — see `expo-modules-android-v2`'s
`api-android/src/main/cpp/CMakeLists.txt`, which aliases a target named `jsi` onto
`ReactAndroid::jsi`; the no-RN build aliases it onto this repo's instead.

## Layout

- `third-party/hermes` — the vendored Hermes tree (submodule, branch `static_h`). It carries both
  the VM and the JSI sources; there is no separate JSI repository.
- `runtime/` — the desktop (JVM) host: `expo.hermes.env.HermesRuntime` plus the native library.

## Use it (desktop)

```kotlin
HermesRuntime().use { runtime ->
  runtime.evaluate("1 + 2")   // "3" — a smoke test; the real surface is the pointer
  attachMyLibrary(runtime.pointer)
}
```

A `jsi::Runtime` is thread-affine: the thread that constructs `HermesRuntime` owns it, and
`evaluate`/`close` refuse to run anywhere else.

The native library is resolved through `java.library.path`, so run the JVM with
`-Djava.library.path=<runtime/build/native-libs>`.

## Build

Requires macOS or Linux, CMake, Ninja, and a JDK. The first build compiles the Hermes VM, so it
takes a few minutes; after that only this repo's own sources rebuild.

```sh
git submodule update --init --recursive
./gradlew :runtime:test
```

`./gradlew :runtime:publishToMavenLocal` publishes three things a consumer needs: the jar, a
`jsi-cpp` zip (the JSI headers, plus `JSIDynamic.cpp` — it ships inside the JSI tree without
belonging to any Hermes target), and a `native-libs` zip with the built library. That is what frees
a consumer from vendoring Hermes itself.

## Not here yet

- The Android (no React Native) artifact: the same VM built for Android ABIs, shipped as an AAR
  whose prefab exposes `jsi` and `hermes`.
- Linux and Windows desktop builds; only macOS and Linux are wired, and only macOS is tested.
