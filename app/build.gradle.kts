plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Google's official AdMob test IDs, used whenever no real ID is configured.
private val testAppId = "ca-app-pub-3940256099942544~3347511713"
private val testBannerId = "ca-app-pub-3940256099942544/6300978111"
private val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

private fun prop(name: String, fallback: String): String =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: fallback

android {
    namespace = "io.github.ishaaaak18.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ishaaaak18.iptv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = prop("admobAppId", testAppId)
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${prop("admobBannerId", testBannerId)}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${prop("admobInterstitialId", testInterstitialId)}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("io.github.ishaaaak18.iptv:core")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
}
