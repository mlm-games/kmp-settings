plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.composeGradle)
    alias(libs.plugins.kotlinComposePlugin)
}

kotlin {
    androidLibrary {
        namespace = "io.github.mlmgames.settings.ui"
        compileSdk = 36
        minSdk = 21
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":settings-core"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}