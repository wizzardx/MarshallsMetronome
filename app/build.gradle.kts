import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
// import java.time.Duration

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // Make sure that you have the Google services Gradle plugin
    id("com.google.gms.google-services")

    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")

    // Add the Sentry Android Gradle plugin
    id("io.sentry.android.gradle") version "3.14.0" apply false
}

android {
    namespace = "com.example.marshallsmetronome"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.marshallsmetronome"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
//         freeCompilerArgs = listOf("-Xdebug")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))

    // Compose UI Dependencies
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Testing Dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("org.mockito:mockito-android:5.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")

    // Lifecycle Dependencies
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Debugging Dependencies
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Kotlin Standard Library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")

    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:32.6.0"))

    // Add the dependencies for the Crashlytics and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    implementation("io.sentry:sentry-android:6.34.0")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
//         timeout.set(Duration.ofSeconds(30))
    }
}

tasks.withType<JavaCompile> {
//     options.compilerArgs.add("-Xdebug")
}
