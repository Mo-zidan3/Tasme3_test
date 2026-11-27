# V-045: suppressions for the releaseSmoke ANDROID-TEST APK's R8 pass (the build type's
# testProguardFiles feed THIS apk's minification — proguardFiles only feed the app APK).
# Missing class reported by run 45368930517: javax.lang.model.element.Modifier, referenced
# from com.google.errorprone.annotations.IncompatibleModifiers.value() — annotation
# retention only, no runtime behavior. Minify stays ON; keep rules fixed, never disabled.
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.util.Elements
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
