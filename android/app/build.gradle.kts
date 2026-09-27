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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
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
    // via Retrofit, and GeminiClient/XhsApiClient use it directly.
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
