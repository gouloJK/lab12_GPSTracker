// Path: app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.lab12_gpstracker"
    compileSdk = 36  // Updated from 35 to 36

    defaultConfig {
        applicationId = "com.example.lab12_gpstracker"
        minSdk = 24
        targetSdk = 36  // Updated from 35 to 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity.ktx)

    // Volley for HTTP requests
    implementation("com.android.volley:volley:1.2.1")

    // OSMDroid for OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}