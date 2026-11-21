# sherpa-onnx: keep JNI bridge classes + native methods (§8.2)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }

# Do not warn about missing native tables in unused architectures
-dontwarn com.k2fsa.sherpa.onnx.**
