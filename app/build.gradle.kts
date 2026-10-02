import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Version from git: versionName = `git describe`, versionCode = number of commits.
fun git(vararg args: String): String? =
    try {
        val out = ByteArrayOutputStream()
        val p = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
        p.inputStream.copyTo(out)
        if (p.waitFor() == 0) out.toString().trim() else null
    } catch (_: Exception) {
        null
    }

android {
    namespace = "org.librehu.fm"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.librehu.fm"
        // The UJC201 runs Android 9.
        minSdk = 28
        targetSdk = 35
        versionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionName = git("describe", "--tags", "--always", "--dirty") ?: "dev"

        ndk {
            // Same ABI as Jancar's radio app, whose libfmjni drives /dev/fm through the 32-bit ioctl path.
            abiFilters += "armeabi-v7a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // LibreHU-service API (app/src/main/aidl, copied from LibreHU/LibreHU-service).
        aidl = true
    }

    lint {
        // CAPTURE_AUDIO_OUTPUT / ACCESS_BROADCAST_RADIO are granted to privileged installs only, as intended.
        disable += setOf("ProtectedPermissions", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
}
