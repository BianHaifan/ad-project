plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlinx.kover")
}

val debugApiBaseUrl = providers.gradleProperty("AD_API_BASE_URL")
    .orElse("http://10.0.2.2:8081/api/v1/")
val releaseApiBaseUrl = providers.gradleProperty("AD_API_BASE_URL")
    .orElse("https://100.49.80.35/api/v1/")

fun apiHostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore(':')

android {
    namespace = "com.adproject.candidate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adproject.candidate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${debugApiBaseUrl.get()}\"")
            buildConfigField("String", "API_HOST", "\"${apiHostOf(debugApiBaseUrl.get())}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl.get()}\"")
            buildConfigField("String", "API_HOST", "\"${apiHostOf(releaseApiBaseUrl.get())}\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val keystorePath = providers.gradleProperty("RELEASE_KEYSTORE_PATH").orNull
                ?: System.getenv("RELEASE_KEYSTORE_PATH")
            val storePass = providers.gradleProperty("RELEASE_KEYSTORE_PASSWORD").orNull
                ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val keyPass = providers.gradleProperty("RELEASE_KEY_ALIAS_PASSWORD").orNull
                ?: System.getenv("RELEASE_KEY_ALIAS_PASSWORD")
            val alias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                ?: System.getenv("RELEASE_KEY_ALIAS")
            if (
                !keystorePath.isNullOrBlank() && file(keystorePath).exists() &&
                !storePass.isNullOrBlank() && !keyPass.isNullOrBlank() && !alias.isNullOrBlank()
            ) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystorePath)
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.adproject.candidate.AdCandidateApp",
                    "com.adproject.candidate.MainActivity",
                    "com.adproject.candidate.core.designsystem.*",
                )
            }
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.6")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
