import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
}

// Release signing is driven entirely by environment variables so CI can inject
// GitHub Secrets without any secret ever living in the repo. When the vars are
// absent (typical local dev), the release build simply stays unsigned.
val keystorePath = providers.environmentVariable("SUBSTREAM_KEYSTORE_PATH").orNull
val keystorePassword = providers.environmentVariable("SUBSTREAM_KEYSTORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("SUBSTREAM_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("SUBSTREAM_KEY_PASSWORD").orNull

val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
    File(keystorePath).isFile &&
    !keystorePassword.isNullOrBlank() &&
    !signingKeyAlias.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.subhz"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.subhz"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // For BuildConfig.DEBUG, which gates the HTTP logging so a release build never writes
        // the Subsonic auth token to Logcat.
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 1. Networking (Retrofit 3 + OkHttp 5)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0")

    // 2. Serialization (Kotlin Core + Official Retrofit Converter)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")

    // 3. Media3 (Audio Player Engine - Stable release)
    val media3Version = "1.11.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    // 4. ViewModel & Navigation (State management and routing for Jetpack Compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Koin for Jetpack Compose (Dependency Injection)
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")

    // Coil for Jetpack Compose (Image Loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")
}