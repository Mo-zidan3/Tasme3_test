# sherpa-onnx: keep JNI bridge classes + native methods (§8.2)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }

# Do not warn about missing native tables in unused architectures
-dontwarn com.k2fsa.sherpa.onnx.**

# V-045 (runs 45367329394/45368930517): errorprone compile-only annotations (via
# androidx.test/compose-ui-test) reference javax.lang.model targets absent from the
# Android bootclasspath. The ANDROID-TEST APK's R8 pass reads the build type's
# testProguardFiles — NOT this file — so the -dontwarn suppressions live in
# smoke-test-proguard.pro; they are mirrored here for the release/releaseSmoke APP APKs
# (harmless if unreferenced).
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.util.Elements
