// import java.time.Duration
import com.android.build.api.dsl.DefaultConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileNotFoundException
import java.util.Properties

/**
 * Loads properties from the secure.properties file.
 * @return Properties object loaded from the file.
 * @throws GradleException if secure.properties file does not exist.
 */

fun loadSecureProps(): Properties {
    val props = Properties()
    val file = rootProject.file("secure.properties")
    if (!file.exists()) throw GradleException("Please populate secure.properties")
    props.load(file.inputStream())
    return props
}

// Load secure properties from the secure.properties file
val secureProps = loadSecureProps()

/**
 * Retrieves a property value from secure properties.
 * @param key The key of the property to retrieve.
 * @return The value of the property.
 * @throws GradleException if the key is not found in secure.properties.
 */
fun getProperty(key: String): String =
    secureProps.getProperty(key) ?: throw GradleException("$key not found in secure.properties")

/**
 * Sets a field in the BuildConfig file from secure properties.
 * @param defaultConfig The DefaultConfig scope from the Android Gradle DSL.
 * @param key The key of the property to be set in BuildConfig.
 */
fun setBuildConfigField(defaultConfig: DefaultConfig, key: String) {
    defaultConfig.buildConfigField("String", key, "\"${getProperty(key)}\"")
}

/**
 * Sets a placeholder in the AndroidManifest.xml file from secure properties.
 * @param defaultConfig The DefaultConfig scope from the Android Gradle DSL.
 * @param key The key of the property to be set as a manifest placeholder.
 */
fun setManifestPlaceholder(defaultConfig: DefaultConfig, key: String) {
    defaultConfig.manifestPlaceholders[key] = getProperty(key)
}

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
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig fields
        setBuildConfigField(this, "ADMOB_AD_UNIT_ID")
        setBuildConfigField(this, "TEST_DEVICE_IDS")

        // Manifest Placeholders
        setManifestPlaceholder(this, "ADMOB_APP_ID")
        setManifestPlaceholder(this, "SENTRY_DSN")
    }

    buildTypes {
        release {
            // Enables code shrinking, obfuscation, and optimization for only
            // your project's release build type. Make sure to use a build
            // variant with `isDebuggable=false`.
            isMinifyEnabled = true

            // Enables resource shrinking, which is performed by the
            // Android Gradle plugin.
            isShrinkResources = true

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
        buildConfig = true
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

    // "Add Dependencies: In your app's build.gradle file, include the necessary dependencies for
    // Google Mobile Ads SDK."
    implementation("com.google.android.gms:play-services-ads-lite:22.5.0")
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

tasks.register("renameApk") {
    doLast {
        println("Starting renameApk task")

        val versionName = project.android.defaultConfig.versionName
        val apkDirectory = File("${project.rootDir}/app/release/")
        val apkFiles = apkDirectory.listFiles { _, name -> name.endsWith(".apk") }

        if (apkFiles == null || apkFiles.isEmpty()) {
            throw FileNotFoundException("No APK files found in ${apkDirectory.absolutePath}")
        }
        if (apkFiles.size > 1) {
            throw Exception("Multiple APK files found in ${apkDirectory.absolutePath}. Expected only one.")
        }

        val apkFile = apkFiles.first()
        val newFileName = "MarshallsMetronome_$versionName.apk"
        val targetPath = "${project.rootDir}/app/build/outputs/apk/release/$newFileName"
        val newFile = File(targetPath)

        if (newFile.exists()) {
            println("Deleting existing file: ${newFile.absolutePath}")
            newFile.delete()
        }

        if (apkFile.exists()) {
            println("APK found at: ${apkFile.absolutePath}")
            apkFile.copyTo(newFile, overwrite = true)
            println("File copied to: ${newFile.absolutePath}")
        } else {
            println("APK not found at: ${apkFile.absolutePath}")
            throw FileNotFoundException("APK file not found")
        }

        println("Renaming complete. New file located at: ${newFile.absolutePath}")
    }
}
