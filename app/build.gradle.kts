import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Single source of truth for the version: versionCode is derived as MAJOR*10000 + MINOR*100 + PATCH,
// so every release only has to bump this string. `-Ptapedeck.version=0.0.1` builds an "old" version,
// e.g. to see the update hint.
val appVersionName = (findProperty("tapedeck.version") as String?) ?: "0.4.1"
val appVersionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

// Release-key credentials live in the untracked keystore.properties at the repository root
// (storeFile, storePassword, keyAlias, keyPassword). Without it, release builds are signed with the
// debug key so they can still be installed for testing.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

// Python, FFmpeg and QuickJS ship as native binaries per ABI (~50 MB each). A full build makes one APK
// per ABI plus a universal APK with all four, which installs on any phone (about four times the size).
// `-Ptapedeck.abis=x86_64` limits a development build to the emulator's ABI and skips the universal one.
val abiOverride = (findProperty("tapedeck.abis") as String?)?.split(",")?.map(String::trim)
val targetAbis = abiOverride ?: listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "net.devemperor.tapedeck"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.devemperor.tapedeck"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*targetAbis.toTypedArray())
            isUniversalApk = abiOverride == null
        }
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // yt-dlp runs Python, FFmpeg and QuickJS as executables straight from the native library
            // directory, so the libraries have to be extracted at install time.
            useLegacyPackaging = true
        }
    }

    androidResources {
        localeFilters += listOf("en", "de")
        generateLocaleConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    testImplementation(libs.junit)
}
