plugins {
    id("com.android.library") version "8.3.2"
    id("org.jetbrains.kotlin.android") version "1.9.23"
    id("maven-publish")
}

group = "com.github.sohaibshaheen"
version = "1.0.5"

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
        buildConfigField("String", "SDK_VERSION", "\"$version\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xjvm-default=all",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.android.gms:play-services-ads:23.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Sources jar task
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.github.sohaibshaheen"
                artifactId = "mahimeta-sdk"
                version = version

                // Main AAR artifact
                artifact("$buildDir/outputs/aar/${project.name}-release.aar") {
                    builtBy(tasks.named("assembleRelease"))
                }

                // Sources JAR
                artifact(sourcesJar.get())

                // Add dependencies to POM
                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")

                    // List all your implementation dependencies here
                    configurations.implementation.get().allDependencies.forEach {
                        if (it.group != null && it.name != "unspecified" && it.version != null) {
                            val dependencyNode = dependenciesNode.appendNode("dependency")
                            dependencyNode.appendNode("groupId", it.group)
                            dependencyNode.appendNode("artifactId", it.name)
                            dependencyNode.appendNode("version", it.version)
                            dependencyNode.appendNode("scope", "runtime")
                        }
                    }
                }

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
                            id.set("sohaibshaheen")
                            name.set("Sohaib Shaheen")
                            email.set("me@sohaibshaheen.com")
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
    }
}