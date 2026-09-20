import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signing = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val privateSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !signing.getProperty(it).isNullOrBlank() }

android {
    namespace = "ing.fuyaoskyrocket.photoinfo"
    compileSdk = 37
    defaultConfig {
        applicationId = "ing.fuyaoskyrocket.photoinfo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["appLabel"] = "Fuyao Photo Info"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (privateSigning) {
        signingConfigs.create("privateRelease") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Fuyao Photo Info Debug"
        }
        create("debugUnsigned") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug.unsigned"
            versionNameSuffix = "-debug-unsigned"
            signingConfig = null
            manifestPlaceholders["appLabel"] = "Fuyao Photo Info Debug Unsigned"
            matchingFallbacks += "debug"
        }
        release {
            // Never silently sign a production artifact with a debug key.
            signingConfig = if (privateSigning) signingConfigs.getByName("privateRelease") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("releaseUnsigned") {
            initWith(getByName("release"))
            applicationIdSuffix = ".unsigned"
            versionNameSuffix = "-unsigned"
            signingConfig = null
            manifestPlaceholders["appLabel"] = "Fuyao Photo Info Release Unsigned"
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true; buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
