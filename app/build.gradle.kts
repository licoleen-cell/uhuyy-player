plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.uhuyy.player"
    // compileSdk 35 = tool build yang aman untuk AGP 8.7. App tetap jalan di Android 5.0 sampai 17.
    compileSdk = 35
    defaultConfig {
        applicationId = "com.uhuyy.player"
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }
    signingConfigs {
        // Kunci tetap (ikut di repo) supaya tiap build punya tanda tangan sama
        // dan bisa di-update di atas versi lama tanpa uninstall.
        // Cuma untuk sideload pribadi, bukan untuk Play Store.
        create("uhuyy") {
            storeFile = file("uhuyy.jks")
            storePassword = "uhuyy123"
            keyAlias = "uhuyy"
            keyPassword = "uhuyy123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("uhuyy")
        }
        debug {
            signingConfig = signingConfigs.getByName("uhuyy")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Coil dibuang: thumbnail sekarang pakai thumbnail bawaan sistem (jauh lebih ringan)
}
