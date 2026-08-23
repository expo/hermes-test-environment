import org.gradle.internal.os.OperatingSystem

plugins {
  alias(libs.plugins.kotlin.jvm)
  `maven-publish`
}

// The published artifact id. The Gradle project stays `:runtime` because the repository is the
// environment; this is the one thing inside it that gets published.
val publishedArtifactId = "hermes-test-environment"

base {
  archivesName = publishedArtifactId
}

val cppDir = layout.projectDirectory.dir("src/main/cpp")
val nativeBuildDir = layout.buildDirectory.dir("native")
val nativeLibsDir = layout.buildDirectory.dir("native-libs")
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("Release")

// The vendored Hermes tree (git submodule under //third-party). It carries both the VM and the JSI
// sources this build compiles and exports.
val hermesDir = rootProject.layout.projectDirectory.dir("third-party/hermes")

// Everything published here is built from that tree, so an uninitialized submodule has to fail
// with the fix rather than with a CMake error or a zip holding nothing.
fun checkHermesCheckout() {
  check(hermesDir.file("CMakeLists.txt").asFile.exists()) {
    "the Hermes submodule is missing — run `git submodule update --init --recursive`"
  }
}

sourceSets {
  main {
    java.setSrcDirs(listOf("src/main/kotlin"))
    resources.setSrcDirs(listOf("src/main/resources"))
  }
  test {
    java.setSrcDirs(listOf("src/test/kotlin"))
  }
}

dependencies {
  testImplementation(libs.kotlin.test.junit5)
}

kotlin {
  explicitApi()
}

// --- Native build (CMake + Ninja) ---------------------------------------------------------------

val configureNative by tasks.registering(Exec::class) {
  doFirst { checkHermesCheckout() }
  inputs.dir(cppDir)
  inputs.property("nativeBuildType", nativeBuildType)
  outputs.dir(nativeBuildDir)

  workingDir = rootDir
  commandLine(
    "cmake",
    "-S", cppDir.asFile.path,
    "-B", nativeBuildDir.get().asFile.path,
    "-G", "Ninja",
    "-DCMAKE_BUILD_TYPE=${nativeBuildType.get()}",
    "-DHERMES_DIR=${hermesDir.asFile.path}",
    "-DJAVA_HOME=${System.getProperty("java.home")}",
  )
}

val buildNative by tasks.registering(Exec::class) {
  dependsOn(configureNative)
  inputs.dir(cppDir)
  inputs.property("nativeBuildType", nativeBuildType)
  outputs.dir(nativeBuildDir)

  workingDir = rootDir
  commandLine("cmake", "--build", nativeBuildDir.get().asFile.path, "--target", "hermes-test-env")
}

val sharedLibraryName = if (OperatingSystem.current().isMacOsX) {
  "libhermes-test-env.dylib"
} else {
  "libhermes-test-env.so"
}

val copyNativeLibs by tasks.registering(Copy::class) {
  dependsOn(buildNative)
  from(nativeBuildDir.map { it.file(sharedLibraryName) })
  into(nativeLibsDir)
  doLast {
    // A published artifact without the library is useless to a consumer and fails much later, in
    // their link step, so stop here instead.
    val library = nativeLibsDir.get().file(sharedLibraryName).asFile
    check(library.exists()) { "the native build produced no $sharedLibraryName" }
  }
}

// Make the native library available whenever the module is assembled or tested.
tasks.named("classes") { dependsOn(copyNativeLibs) }

tasks.named<Test>("test") {
  useJUnitPlatform()
  dependsOn(copyNativeLibs)
  systemProperty("java.library.path", nativeLibsDir.get().asFile.path)
}

// --- Artifacts a consumer needs -----------------------------------------------------------------

// The JSI sources: headers to compile against, plus JSIDynamic.cpp, which ships inside the Hermes
// JSI tree without belonging to any Hermes target (a consumer that wants jsi::Value <-> folly
// conversion compiles it itself). Shipping these is what frees a consumer from vendoring Hermes.
val jsiSourcesZip by tasks.registering(Zip::class) {
  archiveClassifier = "jsi-cpp"
  doFirst { checkHermesCheckout() }
  from(hermesDir.dir("API/jsi")) {
    include("**/*.h")
    include("jsi/JSIDynamic.cpp")
    exclude("**/test/**")
  }
}

val nativeLibsZip by tasks.registering(Zip::class) {
  archiveClassifier = "native-libs"
  dependsOn(copyNativeLibs)
  from(nativeLibsDir)
}

// Publishing builds the native library and packages the JSI sources next to the jar, so the
// artifact is self-contained: a consumer links and runs against it without a Hermes checkout, a
// toolchain, or any task of their own beyond unpacking the zips.
publishing {
  publications {
    create<MavenPublication>("maven") {
      artifactId = publishedArtifactId
      from(components["java"])
      artifact(jsiSourcesZip)
      artifact(nativeLibsZip)
    }
  }
}

// Expose the directory holding the runtime library, and the task that produces it, so a build that
// composes this project (rather than resolving it from a repository) can put it on
// java.library.path.
extra["nativeLibsDir"] = nativeLibsDir.get().asFile
extra["nativeLibsTask"] = copyNativeLibs
