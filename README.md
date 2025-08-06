# Mahimeta Ad SDK

[![](https://jitpack.io/v/syedtehrimabbas/MahimetaSDK.svg)](https://jitpack.io/#syedtehrimabbas/MahimetaSDK)

A lightweight Android SDK for displaying ads with dynamic configuration from your Mahimeta dashboard.

## Features

- Dynamic ad unit ID configuration
- Multiple ad size support
- Lifecycle-aware ad management
- Easy integration with both XML and Jetpack Compose

## Installation

### Option 1: JitPack (Recommended)

#### Step 1: Add JitPack Repository

**Groovy (settings.gradle):**
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url 'https://jitpack.io' }
        google()
        mavenCentral()
    }
}
```

**Kotlin DSL (settings.gradle.kts):**
```kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
    }
}
```

#### Step 2: Add Dependency

```groovy
dependencies {
    implementation 'com.github.syedtehrimabbas:MahimetaSDK:1.0.0'
}
```

---

### Option 2: Using `.aar` File

#### Step 1: Download the `.aar` File

Download `MahimetaSDK-1.0.7.aar` and place it in your app’s `libs` directory.

#### Step 2: Add `.aar` to Your Project

Update your `build.gradle`:

```groovy
repositories {
    flatDir {
        dirs 'libs'
    }
}

dependencies {
    implementation(name: 'MahimetaSDK-1.0.7', ext: 'aar')
}
```

---

### Step 3: Add Required Permissions

In your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

---

### Step 4: Add Your Publisher ID

Add this inside the `<application>` tag:

```xml
<meta-data
    android:name="com.mahimeta.sdk.PUBLISHER_ID"
    android:value="YOUR_PUBLISHER_ID" />
```

---

## Usage

### 1. Initialize the SDK

In your custom `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MahimetaSDK.initialize(this)
    }
}
```

---

### 2. XML Layout Usage

```xml
<com.mahimeta.sdk.MahimetaAdView
    android:id="@+id/adView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adSize="BANNER" />
```

---

### 3. Jetpack Compose Usage

```kotlin
@Composable
fun AdBanner() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            MahimetaAdView(context).apply {
                setAdSize(MahimetaAdSize.BANNER)
            }
        }
    )
}
```

---

### 4. Available Ad Sizes

- `BANNER` – 320x50
- `LARGE_BANNER` – 320x100
- `MEDIUM_RECTANGLE` – 300x250
- `FULL_BANNER` – 468x60
- `LEADERBOARD` – 728x90
- `ADAPTIVE_BANNER` – screen width

```kotlin
val adView = findViewById<MahimetaAdView>(R.id.adView)
adView.setAdSize(MahimetaAdSize.ADAPTIVE_BANNER)
```

---

## Advanced Usage

### Manual Ad Refresh

```kotlin
adView.loadAd()           // Load ad manually
adView.reloadAd(30000)    // Reload after 30 seconds
```

---

### Lifecycle Handling

```kotlin
override fun onResume() {
    super.onResume()
    adView.onResume()
}

override fun onPause() {
    adView.onPause()
    super.onPause()
}

override fun onDestroy() {
    adView.onDestroy()
    super.onDestroy()
}
```

---

### Error Handling

```kotlin
adView.setAdListener(object : AdListener() {
    override fun onAdLoaded() {
        // Ad loaded successfully
    }

    override fun onAdFailedToLoad(error: LoadAdError) {
        // Handle failure
    }

    // Optional: onAdOpened(), onAdClicked(), onAdClosed(), onAdImpression()
})
```

---

## ProGuard / R8

Add these rules if you’re using R8 or ProGuard:

```
# Keep AdMob classes
-keep public class com.google.android.gms.ads.** { *; }
-keep public class com.google.ads.** { *; }

# Keep Mahimeta SDK classes
-keep class com.mahimeta.sdk.** { *; }
-keepclassmembers class com.mahimeta.sdk.** { *; }
```

---

## License

```
Copyright 2025 Mahimeta

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
