plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.apkscout.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apkscout.android"
        minSdk = 36
        targetSdk = 36
        versionCode = 29
        versionName = "0.5.1"
    }

    signingConfigs {
        create("release") {
            val path = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
            val password = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            val alias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            val keyPasswordValue = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull

            if (!path.isNullOrBlank()) {
                storeFile = rootProject.file(path)
                storePassword = password
                keyAlias = alias
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (!providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
}
