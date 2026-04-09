plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.phantombeats"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phantombeats"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appLabel"] = "PhantomBeats"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "PhantomBeats Pruebas"
            buildConfigField("String", "REMOTE_BASE_URL", "\"https://clone-music-backend.onrender.com/\"")
            buildConfigField("String", "LOCAL_EMULATOR_BASE_URL", "\"http://10.0.2.2:3000/\"")
            buildConfigField("String", "LOCAL_DEVICE_BASE_URL", "\"http://127.0.0.1:3000/\"")
            buildConfigField("Boolean", "USE_REMOTE_BACKEND", "true")
        }

        getByName("release") {
            manifestPlaceholders["appLabel"] = "PhantomBeats"
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "REMOTE_BASE_URL", "\"https://clone-music-backend.onrender.com/\"")
            buildConfigField("String", "LOCAL_EMULATOR_BASE_URL", "\"http://10.0.2.2:3000/\"")
            buildConfigField("String", "LOCAL_DEVICE_BASE_URL", "\"http://127.0.0.1:3000/\"")
            buildConfigField("Boolean", "USE_REMOTE_BACKEND", "true")
        }
    }
    
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-android-compiler:2.51")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (Offline-first DB)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // Retrofit & GSON (Network API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Media3 - ExoPlayer
    val media3_version = "1.2.0"
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3_version") // Necesario para OkHttp caching

    // WorkManager (descargas en segundo plano)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // SAF DocumentFile (carpetas locales de musica)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // NewPipe Extractor (búsqueda y extracción de YouTube desde el cliente)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
}
