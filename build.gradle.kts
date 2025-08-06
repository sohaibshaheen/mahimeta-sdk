plugins {
    id("com.android.library") version "8.3.2"  // Updated to latest stable version
    id("org.jetbrains.kotlin.android") version "1.9.23"  // Updated Kotlin version
    id("maven-publish")
    id("signing")  // Optional: for signing artifacts if publishing to Maven Central
}

group = "com.github.sohaibshaheen"
version = "1.0.2"  // Define version here for consistency

android {
    namespace = "com.mahimeta.sdk"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Version information
        buildConfigField("String", "SDK_VERSION", "\"$version\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("staging") {  // Added staging build type
            initWith(getByName("debug"))
            matchingFallbacks.add("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17  // Updated to Java 17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
        )
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Google Play Services
    implementation("com.google.android.gms:play-services-ads:23.0.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Sources and Javadoc tasks
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

val javadoc by tasks.registering(Javadoc::class) {
    source = android.sourceSets["main"].java.srcDirs
    classpath += project.files(android.bootClasspath.joinToString(File.pathSeparator))
    android.libraryVariants.forEach { variant ->
        classpath += variant.javaCompileProvider.get().classpath
    }
    excludes("**/R.html", "**/R.*.html", "**/index.html")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(javadoc.get().destinationDir)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                // Main artifacts
                groupId = "com.github.sohaibshaheen"
                artifactId = "mahimeta-sdk"
                version = version

                from(components["release"])
                artifact(sourcesJar.get())
                artifact(javadocJar.get())

                // POM configuration
                pom {
                    name.set("Mahimeta Ad SDK")
                    description.set("A lightweight Android SDK for displaying ads with dynamic configuration")
                    url.set("https://github.com/sohaibshaheen/mahimeta-sdk")

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
                        connection.set("scm:git:github.com/sohaibshaheen/mahimeta-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/sohaibshaheen/mahimeta-sdk.git")
                        url.set("https://github.com/sohaibshaheen/mahimeta-sdk")
                    }
                }
            }
        }

        // Optional: Configure repository for publishing
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/sohaibshaheen/mahimeta-sdk")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

// Task to ensure sourcesJar is built before metadata generation
tasks.withType<GenerateModuleMetadata> {
    dependsOn(tasks.named("sourcesJar"))
}