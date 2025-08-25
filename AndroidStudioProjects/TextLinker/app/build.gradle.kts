plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")  // Safe Args for Kotlin
    kotlin("kapt")
}

android {
    namespace = "com.xfire.textlinker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xfire.textlinker"
        minSdk = 21
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            // Exclude the specified files using the new Kotlin DSL syntax:
            excludes += "/META-INF/{AL2.0,LGPL2.1,*.version,**/libsqlite.so,**/libsqlite3.so}"
        }
    }
}

kapt {
    arguments {
        // Use Kotlin's syntax for setting arguments
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
        // Disable Room's table structure verification to avoid issues with SQLiteJDBCLoader
        arg("room.verifyTableStructure", "false")
        arg("room.verifier.failOnMissingDatabase", "false")
    }
}

dependencies {
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.10.1")


    // Room dependencies (using version 2.6.1)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // SLF4J simple binding for Room's LoggerFactory dependency (using version 1.7.30)
    implementation("org.slf4j:slf4j-simple:1.7.30")

    // Navigation components (using version 2.8.9)
    val nav_version = "2.8.9"
    implementation("androidx.navigation:navigation-fragment-ktx:$nav_version")
    implementation("androidx.navigation:navigation-ui-ktx:$nav_version")

    // CameraX dependencies
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // QR Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    
    // ZXing for QR code generation
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Swipe to refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Socket.IO Client - Removed due to conflict with Android's built-in org.json package
    // implementation("io.socket:socket.io-client:2.1.0")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
