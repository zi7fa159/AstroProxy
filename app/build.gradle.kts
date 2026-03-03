plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.astroproxy"
    compileSdk = 34
    buildToolsVersion = "34.0.0" 
    defaultConfig {
        applicationId = "com.astroproxy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"
    }
    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = "astro123"
            keyAlias = "astro_alias"
            keyPassword = "astro123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
