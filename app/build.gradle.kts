plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.emergency.meshgps"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.emergency.meshgps"
        minSdk = 24
        targetSdk = 34
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // Layanan GPS Location
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Pustaka Nearby Connections (Solusi Error 'nearby')
    implementation("com.google.android.gms:play-services-nearby:19.1.0")

    // Pustaka Gson Serializer (Solusi Error 'gson')
    implementation("com.google.code.gson:gson:2.10.1")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
