# sherpa-onnx: keep JNI bridge classes + native methods (§8.2)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }

# Do not warn about missing native tables in unused architectures
-dontwarn com.k2fsa.sherpa.onnx.**

# V-045 (run 45367329394): the releaseSmoke ANDROID-TEST APK's R8 pass references
# errorprone compile-only annotations (pulled in by androidx.test/compose-ui-test) whose
# javax.lang.model targets do not exist on Android. They are annotation retention only —
# no runtime behavior — so they are suppressed with -dontwarn (minify stays ON; this is
# the keep-rule fix, never disabling minification). Modifier is the class R8 reported;
# Element/ElementKind/Elements are the standard siblings in the same cluster.
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.util.Elements
