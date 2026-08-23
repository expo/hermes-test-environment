cmake_minimum_required(VERSION 3.24)
project(hermes-test-env CXX)

# The path to the vendored Hermes tree is injected by Gradle (see runtime/build.gradle.kts). The
# fallback keeps an IDE CMake sync, which passes no -D vars, working. It can also be set manually:
#   cmake -S runtime/src/main/cpp -B build/native -G Ninja \
#     -DHERMES_DIR=$PWD/third-party/hermes
if (NOT DEFINED HERMES_DIR)
  set(HERMES_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third-party/hermes")
endif ()
if (NOT DEFINED FBJNI_DIR)
  set(FBJNI_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third-party/fbjni")
endif ()

# Gradle injects -DJAVA_HOME; the JNI headers come from it. IDE CMake syncs configure this file
# directly without any -D vars, so derive it from the environment or the macOS java_home helper.
if (NOT DEFINED JAVA_HOME)
  if (DEFINED ENV{JAVA_HOME})
    set(JAVA_HOME "$ENV{JAVA_HOME}")
  elseif (APPLE)
    execute_process(
      COMMAND /usr/libexec/java_home
      OUTPUT_VARIABLE JAVA_HOME
      OUTPUT_STRIP_TRAILING_WHITESPACE
      RESULT_VARIABLE JAVA_HOME_RESULT
    )
    if (NOT JAVA_HOME_RESULT EQUAL 0)
      message(FATAL_ERROR "JAVA_HOME is not set and /usr/libexec/java_home failed; pass -DJAVA_HOME=<jdk>.")
    endif ()
  endif ()
endif ()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- Hermes (the VM and the JSI it vendors) -----------------------------------------------------
# One add_subdirectory for the whole tree, so the `jsi` target this build exports and the VM that
# references it are configured identically — in particular JSI_UNSTABLE, which the tree turns on by
# default and which gates part of the jsi ABI (e.g. the jsi::Serialized destructor).
#
# The VM is linked statically: `hermesapi` provides facebook::hermes::makeHermesRuntime, and
# `hermesvm_a` the "_sh_*" Static Hermes entry points it calls.
set(HERMES_BUILD_SHARED_JSI OFF CACHE BOOL "" FORCE)
set(HERMES_ENABLE_DEBUGGER OFF CACHE BOOL "" FORCE)
set(HERMES_ENABLE_TEST_SUITE OFF CACHE BOOL "" FORCE)
add_subdirectory(${HERMES_DIR} hermes-build EXCLUDE_FROM_ALL)

# --- fbjni (vendored) -----------------------------------------------------------------------------
# Nothing here uses it: it is built and shipped for consumers that benchmark their own JNI dispatch
# against fbjni's, so they need neither the submodule nor a second toolchain hop. fbjni hard-requires
# JAVA_HOME for a non-Android build, which is resolved above. Skip its own gtest suite.
set(FBJNI_SKIP_TESTS ON CACHE BOOL "" FORCE)
add_subdirectory(${FBJNI_DIR} fbjni-build EXCLUDE_FROM_ALL)

# --- The host library ---------------------------------------------------------------------------
add_library(hermes-test-env SHARED
        HermesRuntimeJni.cpp
)

target_include_directories(hermes-test-env PRIVATE
        ${HERMES_DIR}/API
        ${HERMES_DIR}/API/jsi
        ${HERMES_DIR}/public
        ${JAVA_HOME}/include
)
if (APPLE)
  target_include_directories(hermes-test-env PRIVATE ${JAVA_HOME}/include/darwin)
else ()
  target_include_directories(hermes-test-env PRIVATE ${JAVA_HOME}/include/linux)
endif ()

# Our own source only — the vendored Hermes targets keep their own settings.
target_compile_options(hermes-test-env PRIVATE -Wall -Wextra -Wpedantic -Werror)

target_link_libraries(hermes-test-env PRIVATE
        hermesapi
        hermesvm_a
)

# Export ALL of JSI, not just the members this library happens to reference. A consumer (e.g. Expo
# Modules v2's bridge library) compiles against the JSI headers shipped alongside this library and
# resolves every jsi symbol here at link time, so exactly one copy — one set of vtables and
# typeinfos, so cross-library dynamic_cast and catch keep working — exists process-wide.
if (APPLE)
  target_link_options(hermes-test-env PRIVATE "-Wl,-force_load,$<TARGET_FILE:jsi>")
else ()
  target_link_options(hermes-test-env PRIVATE
          "-Wl,--whole-archive" "$<TARGET_FILE:jsi>" "-Wl,--no-whole-archive")
endif ()
add_dependencies(hermes-test-env jsi)
