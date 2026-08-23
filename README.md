# hermes-tests-environment

A desktop (JVM) Hermes host: creates and manages a `facebook::jsi::Runtime` on the calling
thread and hands its address to a JVM caller. Engine-only — it knows nothing about Expo
modules.

Extracted from `expo-modules-android-v2`, where the same code lived in the `:hermes` module.
On Android, React Native plays this role; on desktop, this repo does.
