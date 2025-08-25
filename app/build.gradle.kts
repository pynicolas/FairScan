plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "org.mydomain.myscan"
    compileSdk = 35
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/assets"))

    defaultConfig {
        applicationId = "org.fairscan.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val hasSigning = listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD"
    ).all { project.hasProperty(it) }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(project.property("RELEASE_STORE_FILE") as String)
                storePassword = project.property("RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        release {
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

apply(from = "download-tflite.gradle.kts")

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.litert)
    implementation(libs.litert.support)
    implementation(libs.litert.metadata)
    implementation(libs.opencv)
    implementation(libs.pdfbox)
    implementation(libs.icons.extended)
    implementation(libs.zoomable)
    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

aboutLibraries {
    android.registerAndroidTasks = true
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.32.0"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

