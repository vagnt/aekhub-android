plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
// Firebase (ειδοποιήσεις): ενεργό μόλις υπάρχει το app/google-services.json
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "gr.aekhub.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "gr.aekhub.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 51
        versionName = "0.17.0"
        buildConfigField("String", "SITE", "\"https://aekhub.gr\"")
        // true μόνο στο APK του dev (build type «devtools»): εργαλεία προσομοίωσης αγώνα
        buildConfigField("boolean", "DEV", "false")
        manifestPlaceholders["appLabel"] = "@string/app_name"
        // AdMob App ID (δημόσιο αναγνωριστικό). Μέχρι να μπει το δικό μας, το δοκιμαστικό της Google.
        manifestPlaceholders["admobAppId"] = (project.findProperty("admobAppId") as String?) ?: "ca-app-pub-3940256099942544~3347511713"
    }

    // Υπογραφή: αν υπάρχουν τα secrets (GitHub Actions), το release υπογράφεται για το Play Store.
    val ks = System.getenv("AEK_KEYSTORE")
    signingConfigs {
        if (!ks.isNullOrBlank() && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("AEK_KEYSTORE_PASS")
                keyAlias = System.getenv("AEK_KEY_ALIAS")
                keyPassword = System.getenv("AEK_KEY_PASS")
            }
        }
        // Κλειδί δοκιμών: σταθερό, ώστε κάθε νέο APK να εγκαθίσταται πάνω στο προηγούμενο. ΟΧΙ για το Play Store.
        create("dev") {
            storeFile = file("dev.jks")
            storePassword = "aekhubdev"
            keyAlias = "dev"
            keyPassword = "aekhubdev"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("dev")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // APK για τον dev: εγκαθίσταται ΔΙΠΛΑ στο κανονικό app (άλλο όνομα πακέτου), με εργαλεία προσομοίωσης αγώνα.
        // Χωρίς Firebase (δεν έχει δικό του client), χωρίς minify, υπογεγραμμένο με το κλειδί δοκιμών.
        create("devtools") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("dev")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "DEV", "true")
            manifestPlaceholders["appLabel"] = "AEK Hub DEV"
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
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
    // Widget αρχικής οθόνης
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.android.gms:play-services-ads:23.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Το dev APK έχει άλλο όνομα πακέτου που δεν υπάρχει στο google-services.json: δεν επεξεργάζεται το Firebase (το app δουλεύει χωρίς push)
tasks.configureEach {
    if (name.contains("Devtools", ignoreCase = true) && name.contains("GoogleServices", ignoreCase = true)) enabled = false
}
