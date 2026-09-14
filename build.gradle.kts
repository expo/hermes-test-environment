import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  base
}

allprojects {
  group = "io.github.expo"
  version = "0.1.0-SNAPSHOT"
}

subprojects {
  // Compile with a JDK 17 toolchain, but emit Java 11 bytecode so consumers can run on JVM 11.
  // The consumers (expo-modules-v2-android, kolibri) publish for JVM 11 themselves, and Gradle
  // rejects a Java 17 variant on a build that targets 11.
  // `release` (not `targetCompatibility`) so JDK-17-only APIs cannot leak into the bytecode.
  plugins.withId("org.jetbrains.kotlin.jvm") {
    extensions.configure<KotlinJvmProjectExtension> {
      jvmToolchain(17)
      compilerOptions {
        jvmTarget = JvmTarget.JVM_11
      }
    }
    tasks.withType<JavaCompile>().configureEach {
      options.release = 11
    }
  }
}
