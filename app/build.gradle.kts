plugins {
    id("com.android.application") version "9.3.1"
}

android {
    namespace = "cn.xing.webtoapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.xing.webtoapp"
        minSdk = 24

        targetSdk = 28
        versionCode = 1
        versionName = "1.18"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "webtoapp2026"
            keyAlias = "webtoapp"
            keyPassword = "webtoapp2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
}
