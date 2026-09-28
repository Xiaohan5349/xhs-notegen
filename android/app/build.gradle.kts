import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xiaohan.xhsnotegen"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // Keystore lives in .wsl-tools/keystore/ (gitignored, shared by the
            // WSL and Windows checkouts of this repo); passwords come from the
            // gitignored local.properties — never hardcode them here.
            val secrets = Properties().apply {
                val lp = rootProject.file("local.properties")
                if (lp.exists()) lp.inputStream().use { load(it) }
            }
            storeFile = file("../../.wsl-tools/keystore/xhs-release.jks")
            storePassword = secrets.getProperty("RELEASE_STORE_PASSWORD")
            keyPassword = secrets.getProperty("RELEASE_KEY_PASSWORD")
            keyAlias = "xhs"
        }
    }

    defaultConfig {
        applicationId = "com.xiaohan.xhsnotegen"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
    }

    buildTypes {
        debug {
            // Installs next to the release app ("食记 Debug") instead of replacing it,
            // so testing never touches the real app's notes.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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
    }
}

ksp {
    // Exported schemas let Room verify migrations; commit app/schemas/.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // NOTE: okhttp must be declared explicitly — it used to arrive transitively
    // via Retrofit, and AiWriter/XhsApiClient use it directly.
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
base {
    archivesName.set("食记-${android.defaultConfig.versionName}")
}

// Release guard: a versionCode LOWER than the last release can't install as an
// update — Android makes you uninstall first, which deletes all notes, API keys
// and the XHS login. The last released code is recorded in released-version-code.txt
// (commit it). Rebuilding the same code is fine: that reinstalls in place.
val releasedVersionFile = file("released-version-code.txt")
val currentVersionCode = android.defaultConfig.versionCode ?: 0
// Checked first thing in the release build, before signing or packaging.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val last = releasedVersionFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0
        if (currentVersionCode < last) {
            throw GradleException(
                "versionCode $currentVersionCode is lower than the last release ($last). " +
                    "Installing it would force an uninstall and wipe the app's data. " +
                    "Set versionCode to ${last + 1} (or higher) in app/build.gradle.kts."
            )
        }
    }
}
// Recorded only after a release build succeeds.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doLast { releasedVersionFile.writeText("$currentVersionCode\n") }
}
