import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.osdetector)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.coil.compose)

            implementation(libs.gadulka)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.coil.network.okhttp)
            implementation(libs.androidx.activity.compose)
            // Provides Dispatchers.Main (AndroidDispatcherFactory) for the app's
            // CoroutineScopes. Relying on it being pulled in transitively by
            // Compose/AndroidX is what produces "Module with the Main dispatcher
            // had failed to initialize" crashes at startup.
            implementation(libs.kotlinx.coroutines.android)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.coil.network.okhttp)

                // Gadulka JVM backend uses JavaFX Media; must match host OS.
                val fxClassifier = when (osdetector.classifier) {
                    "linux-x86_64" -> "linux"
                    "linux-aarch_64" -> "linux-aarch64"
                    "windows-x86_64" -> "win"
                    "windows-aarch_64" -> "win-aarch64"
                    "osx-x86_64" -> "mac"
                    "osx-aarch_64" -> "mac-aarch64"
                    else -> error("Unsupported OS: ${osdetector.classifier}")
                }
                implementation("org.openjfx:javafx-base:${libs.versions.javafx.get()}:$fxClassifier")
                implementation("org.openjfx:javafx-graphics:${libs.versions.javafx.get()}:$fxClassifier")
                implementation("org.openjfx:javafx-swing:${libs.versions.javafx.get()}:$fxClassifier")
                implementation("org.openjfx:javafx-media:${libs.versions.javafx.get()}:$fxClassifier")
            }
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "app.apertus"

    // API 37 is the first Android release that ships minor SDK versions: the
    // published SDK package is `platforms;android-37.0`, `37.1`, `37.2`, and
    // there is NO plain `platforms;android-37`. When `compileSdkMinor` is left
    // unset, AGP looks for the plain `android-37` platform and aborts with
    // "Failed to find target with hash string 'android-37'".
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "app.apertus"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.MainKt"
    }
}
