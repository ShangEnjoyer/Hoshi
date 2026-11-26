import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.tvcatalog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tvcatalog"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.5"

        val tmdbKey = localProps.getProperty("TMDB_API_KEY", "")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose for TV
    implementation("androidx.tv:tv-material:1.0.0")

    // Imagens
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Código QR (ligação ao telemóvel para colar a chave do TMDB)
    implementation("com.google.zxing:core:3.5.3")

    // Rede
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
