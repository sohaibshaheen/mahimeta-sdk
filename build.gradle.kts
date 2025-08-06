buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
    }
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("com.github.dcendents.android-maven") version "2.1"
}

// Configuration to hold embedded dependencies
val embedded = configurations.create("embedded")
android {
    namespace = "com.mahimeta.sdk"
    compileSdk = 34

    buildFeatures {
        buildConfig = true  // ← Add this
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Version information
        buildConfigField("String", "SDK_VERSION", "\"1.0.0\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Disable minification for now to debug
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // Configure source sets to include both Java and Kotlin sources
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/main/kotlin")
            res.srcDirs("src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }

    // Enable Java 8 features
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX dependencies
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")

    // GSON for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Add dependencies to embedded configuration
    embedded("com.google.code.gson:gson:2.10.1")
    embedded("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    embedded("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// Task to generate sources JAR
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.github.sohaibshaheen"
                artifactId = "mahimeta-sdk"
                version = "1.0.0"

                // Include the AAR
                artifact("$buildDir/outputs/aar/MahimetaSDK-release.aar") {
                    builtBy(tasks.getByName("bundleReleaseAar"))
                }

                // Include sources
                artifact(tasks["sourcesJar"])

                // Sources JAR is already included

                // Add dependencies to POM
                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")

                    // Add Gson
                    val gsonNode = dependenciesNode.appendNode("dependency")
                    gsonNode.appendNode("groupId", "com.google.code.gson")
                    gsonNode.appendNode("artifactId", "gson")
                    gsonNode.appendNode("version", "2.10.1")
                    gsonNode.appendNode("scope", "runtime")

                    // Add Coroutines
                    val coroutinesNode = dependenciesNode.appendNode("dependency")
                    coroutinesNode.appendNode("groupId", "org.jetbrains.kotlinx")
                    coroutinesNode.appendNode("artifactId", "kotlinx-coroutines-android")
                    coroutinesNode.appendNode("version", "1.7.3")
                    coroutinesNode.appendNode("scope", "runtime")
                }

                // Configure the POM
                pom {
                    name.set("Mahimeta Ad SDK")
                    description.set("A lightweight Android SDK for displaying ads with dynamic configuration from Mahimeta dashboard")
                    url.set("https://github.com/syedtehrimabbas/MahimetaSDK")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("syedtehrimabbas")
                            name.set("Syed Tehrim Abbas")
                            email.set("syedtehrimabbas@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/syedtehrimabbas/MahimetaSDK.git")
                        developerConnection.set("scm:git:ssh://github.com/syedtehrimabbas/MahimetaSDK.git")
                        url.set("https://github.com/syedtehrimabbas/MahimetaSDK/tree/main")
                    }
                }
            }
        }
    }
}
