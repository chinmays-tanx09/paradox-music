import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Optional release signing: values come from an untracked keystore.properties in the project root
// (storeFile, storePassword, keyAlias, keyPassword) or from the environment variables
// PM_STORE_FILE, PM_STORE_PASSWORD, PM_KEY_ALIAS, PM_KEY_PASSWORD. Nothing secret lives in this repo.
// When none is provided the release build is signed with the local debug key, so anyone can build it.
val signProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "bot.paradox.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "bot.paradox.music"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        // Only AndroidX's tiny graphics-path helper is native; ship it for phones only.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("paradox") {
            val sf = signProps.getProperty("storeFile") ?: System.getenv("PM_STORE_FILE")
            if (sf != null) {
                storeFile = file(sf)
                storePassword = signProps.getProperty("storePassword") ?: System.getenv("PM_STORE_PASSWORD")
                keyAlias = signProps.getProperty("keyAlias") ?: System.getenv("PM_KEY_ALIAS")
                keyPassword = signProps.getProperty("keyPassword") ?: System.getenv("PM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val sc = signingConfigs.getByName("paradox")
            signingConfig = if (sc.storeFile != null) sc else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/kotlin/**", "DebugProbesKt.bin") } }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf("MissingTranslation", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    val media3 = "1.5.1"
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("com.google.guava:guava:33.3.1-android")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.32.2")
}
