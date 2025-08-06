# Keep all public classes and methods in your SDK package
-keep public class com.mahimeta.sdk.** { *; }

# Keep Kotlin metadata (important for Kotlin-based SDKs)
-keepclassmembers class **.kotlin.** { *; }

# Keep all classes that extend/implement these (adapt as needed)
-keep public class * implements com.mahimeta.sdk.MahimetaAdView
-keep public class * implements com.mahimeta.sdk.MahimetaSDK

# Keep all @Keep annotated classes (if you use them)
-keep @androidx.annotation.Keep class * { *; }