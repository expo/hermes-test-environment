pluginManagement {
  repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenLocal()
    mavenCentral()
  }
}

rootProject.name = "hermes-tests-environment"

// The desktop (JVM) Hermes host: creates a `jsi::Runtime` and hands its address to the JVM. Its
// native library also carries the one process-wide copy of JSI, so a consumer's own library links
// against this one instead of compiling JSI itself.
include("runtime")
