import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.smarthome"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smarthome.lv"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "0.0.6"

        val envFile = rootProject.file(".env")
        if (envFile.exists()) {
            val properties = Properties()
            properties.load(envFile.inputStream())
            val apiUrl = properties.getProperty("API_BASE_URL") ?: "http://localhost:8000/api/"
            buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        } else {
            buildConfigField("String", "API_BASE_URL", "\"http://localhost:8000/api/\"")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keyPropsFile = rootProject.file("key.properties")
    val keyProps = Properties()
    var hasReleaseSigning = false
    if (keyPropsFile.exists()) {
        keyPropsFile.inputStream().use { keyProps.load(it) }
        hasReleaseSigning = keyProps.getProperty("storeFile") != null
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // Resolve relative to the project root (matches rootProject.file("key.properties") above),
                // not the app module directory, so relative storeFile paths in key.properties work as expected.
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only attach a signing config when key.properties actually provides one, so
            // `assembleRelease` still succeeds (unsigned) on machines/CI without secrets
            // instead of failing with an opaque "keystore not set" error.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    // Firebase's play-services-basement/base transitively pull in fragment:1.0.0, which is too
    // old for the ActivityResult APIs (registerForActivityResult) used in MainActivity. Pin a
    // modern version explicitly so Gradle's dependency resolution picks it over the old one.
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
