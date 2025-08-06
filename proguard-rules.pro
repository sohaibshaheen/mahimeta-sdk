# Keep all SDK classes and their public/protected members
-keep class com.mahimeta.sdk.** { *; }
-keepclassmembers class com.mahimeta.sdk.** {
    public protected *;
}

# Keep Kotlin metadata and coroutines
-keepclassmembers class **.kotlin.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep AdMob related classes
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-keep class com.google.android.gms.ads.AdView { *; }
-keep class com.google.android.gms.ads.AdListener { *; }
-keep class com.google.android.gms.ads.AdRequest { *; }
-keep class com.google.android.gms.ads.AdSize { *; }
-keep class com.google.android.gms.ads.MobileAds { *; }

# Keep custom view attributes
-keep class com.mahimeta.sdk.R$* { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep Gson model classes
-keep class com.mahimeta.sdk.model.** { *; }
-keep class com.mahimeta.sdk.analytics.model.** { *; }
-keep class com.mahimeta.sdk.api.model.** { *; }

# Keep annotations and reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations

# Keep Coroutine Continuations
-keepclassmembers class kotlin.coroutines.Continuation {
    public static final java.lang.Object getResult(java.lang.Object);
}

# Keep the entry point
-keep class com.mahimeta.sdk.MahimetaSDK { *; }
-keep class com.mahimeta.sdk.MahimetaAdView { *; }

# Keep all public API methods
-keepclassmembers class com.mahimeta.sdk.MahimetaSDK {
    public static *;
    public *;
}

# Keep all public API methods in views
-keepclassmembers class com.mahimeta.sdk.MahimetaAdView {
    public <init>(...);
    public void *(...);
}

# Keep network and analytics
-keep class com.mahimeta.sdk.network.** { *; }
-keep class com.mahimeta.sdk.analytics.** { *; }
-keep class com.mahimeta.sdk.api.** { *; }

# Keep all interfaces
-keep interface * {
    <methods>;
}

# Keep all enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep BuildConfig
-keep class com.mahimeta.sdk.BuildConfig { *; }

# Keep Java 8+ string concatenation
-keep class java.lang.invoke.StringConcatFactory { *; }
-dontwarn java.lang.invoke.StringConcatFactory
