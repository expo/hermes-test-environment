#include <jni.h>

#include <exception>
#include <memory>
#include <string>

#include <hermes/hermes.h>
#include <jsi/jsi.h>

namespace {
  // The internal (binary) class name of the JVM handle these natives are registered against.
  constexpr const char* kHandleClass = "io/github/expo/hermes/HermesRuntime";

  facebook::jsi::Runtime* asRuntime(jlong pointer) {
    return reinterpret_cast<facebook::jsi::Runtime*>(pointer);
  }

  std::string toStdString(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
  }

  void throwRuntimeException(JNIEnv* env, const std::string& message) {
    if (env->ExceptionCheck()) {
      return;
    }
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), message.c_str());
  }

  /**
   * Creates a Hermes VM and returns the address of its `jsi::Runtime`.
   *
   * The address is cast through `jsi::Runtime` so it round-trips unchanged for consumers that only
   * know the base class. Ownership moves to the JVM handle, which frees it in `nativeDestroy`.
   */
  jlong nativeCreate(JNIEnv* env, jclass) {
    try {
      std::unique_ptr<facebook::jsi::Runtime> runtime = facebook::hermes::makeHermesRuntime();
      return reinterpret_cast<jlong>(runtime.release());
    } catch (const std::exception& exception) {
      throwRuntimeException(env, std::string("failed to create a Hermes runtime: ") + exception.what());
      return 0;
    }
  }

  // `jsi::Runtime` has a virtual destructor, so deleting through the base destroys the Hermes VM.
  void nativeDestroy(JNIEnv*, jclass, jlong pointer) {
    delete asRuntime(pointer);
  }

  /**
   * Evaluates [script] and returns the result coerced to a string — enough to smoke-test a build,
   * and deliberately the only JS-facing call here. Everything richer belongs in the library under
   * test, which drives the runtime through this pointer.
   */
  jstring nativeEvaluate(JNIEnv* env, jclass, jlong pointer, jstring script, jstring sourceUrl) {
    facebook::jsi::Runtime& runtime = *asRuntime(pointer);
    try {
      facebook::jsi::Value result = runtime.evaluateJavaScript(
        std::make_shared<facebook::jsi::StringBuffer>(toStdString(env, script)),
        toStdString(env, sourceUrl)
      );
      return env->NewStringUTF(result.toString(runtime).utf8(runtime).c_str());
    } catch (const facebook::jsi::JSError& error) {
      throwRuntimeException(env, error.getMessage() + "\n" + error.getStack());
      return nullptr;
    } catch (const std::exception& exception) {
      throwRuntimeException(env, exception.what());
      return nullptr;
    }
  }

  const JNINativeMethod kMethods[] = {
    {const_cast<char*>("nativeCreate"), const_cast<char*>("()J"),
     reinterpret_cast<void*>(&nativeCreate)},
    {const_cast<char*>("nativeDestroy"), const_cast<char*>("(J)V"),
     reinterpret_cast<void*>(&nativeDestroy)},
    {const_cast<char*>("nativeEvaluate"),
     const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
     reinterpret_cast<void*>(&nativeEvaluate)},
  };
} // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }

  jclass handle = env->FindClass(kHandleClass);
  if (handle == nullptr) {
    return JNI_ERR;
  }
  if (env->RegisterNatives(handle, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
    return JNI_ERR;
  }
  return JNI_VERSION_1_6;
}
