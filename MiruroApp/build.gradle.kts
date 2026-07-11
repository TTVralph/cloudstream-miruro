import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
}

apply(plugin = "org.jetbrains.kotlin.plugin.compose")

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) {
        releaseSigningFile.inputStream().use { load(it) }
    }
}
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigningReady = releaseSigningKeys.all {
    !releaseSigningProperties.getProperty(it).isNullOrBlank()
}

android {
    namespace = "com.ttvralph.miruroapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ttvralph.miruroapp"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            // Keep shrinking disabled until the Jackson and playback reflection paths
            // have been validated with R8 on a physical TV.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(kotlin("test"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
