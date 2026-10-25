import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        FileInputStream(versionPropsFile).use { load(it) }
    }
}

val vMajor = versionProps.getProperty("VERSION_MAJOR", "1")
val vMinor = versionProps.getProperty("VERSION_MINOR", "0")
val vPatch = versionProps.getProperty("VERSION_PATCH", "0")
val vCode = versionProps.getProperty("VERSION_CODE", "1").toInt()

android {
    namespace = "com.example.gpsspeedometer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.gpsspeedometer"
        minSdk = 29
        targetSdk = 34
        versionCode = vCode
        versionName = "$vMajor.$vMinor.$vPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) {
            FileInputStream(keystorePropsFile).use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            val keyPath = System.getenv("KEYSTORE_PATH")
                ?: keystoreProps.getProperty("KEYSTORE_PATH")
                ?: "release.jks"
            val storePass = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProps.getProperty("KEYSTORE_PASSWORD")
            val keyAliasName = System.getenv("KEY_ALIAS")
                ?: keystoreProps.getProperty("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASSWORD")
                ?: keystoreProps.getProperty("KEY_PASSWORD")

            val storeFileObj = rootProject.file(keyPath)
            if (storeFileObj.exists() && storePass != null && keyAliasName != null && keyPass != null) {
                storeFile = storeFileObj
                storePassword = storePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
        debug {
            versionNameSuffix = ".dev"
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as? com.android.build.gradle.api.ApkVariantOutput)?.outputFileName =
                "GPS-Speedometer-v${variant.versionName}-${variant.name}.apk"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Location
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Extended Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Map (osmdroid OpenStreetMap)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Networking for Overpass API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("printVersion") {
    doLast {
        println("versionCode: $vCode")
        println("versionName: $vMajor.$vMinor.$vPatch")
    }
}

tasks.register("bumpPatch") {
    doLast {
        val props = Properties()
        FileInputStream(versionPropsFile).use { props.load(it) }
        val major = props.getProperty("VERSION_MAJOR", "1").toInt()
        val minor = props.getProperty("VERSION_MINOR", "0").toInt()
        val patch = props.getProperty("VERSION_PATCH", "0").toInt() + 1
        val code = props.getProperty("VERSION_CODE", "1").toInt() + 1

        props["VERSION_MAJOR"] = major.toString()
        props["VERSION_MINOR"] = minor.toString()
        props["VERSION_PATCH"] = patch.toString()
        props["VERSION_CODE"] = code.toString()

        FileOutputStream(versionPropsFile).use { props.store(it, null) }
        println("Bumped to v$major.$minor.$patch (code: $code)")
    }
}

tasks.register("bumpMinor") {
    doLast {
        val props = Properties()
        FileInputStream(versionPropsFile).use { props.load(it) }
        val major = props.getProperty("VERSION_MAJOR", "1").toInt()
        val minor = props.getProperty("VERSION_MINOR", "0").toInt() + 1
        val patch = 0
        val code = props.getProperty("VERSION_CODE", "1").toInt() + 1

        props["VERSION_MAJOR"] = major.toString()
        props["VERSION_MINOR"] = minor.toString()
        props["VERSION_PATCH"] = patch.toString()
        props["VERSION_CODE"] = code.toString()

        FileOutputStream(versionPropsFile).use { props.store(it, null) }
        println("Bumped to v$major.$minor.$patch (code: $code)")
    }
}

tasks.register("bumpMajor") {
    doLast {
        val props = Properties()
        FileInputStream(versionPropsFile).use { props.load(it) }
        val major = props.getProperty("VERSION_MAJOR", "1").toInt() + 1
        val minor = 0
        val patch = 0
        val code = props.getProperty("VERSION_CODE", "1").toInt() + 1

        props["VERSION_MAJOR"] = major.toString()
        props["VERSION_MINOR"] = minor.toString()
        props["VERSION_PATCH"] = patch.toString()
        props["VERSION_CODE"] = code.toString()

        FileOutputStream(versionPropsFile).use { props.store(it, null) }
        println("Bumped to v$major.$minor.$patch (code: $code)")
    }
}

kotlin {
    jvmToolchain(17)
}
