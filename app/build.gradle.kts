plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.autoaim"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.autoaim"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    buildTypes {
        release {
            // Sinov uchun: debuggable emas (ART to'liq optimallashtiradi — Vision bir necha marta tez),
            // debug kalit bilan imzolanadi, telefonga to'g'ridan-to'g'ri o'rnatiladi.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(project(":engine"))

    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

}
