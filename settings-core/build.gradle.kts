@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.dokka")
}

kotlin {
    android {
        namespace = "io.github.mlmgames.settings.core"
        compileSdk = 37
        minSdk = 21
        withJava()

        optimization {
            consumerKeepRules.apply {
                publish = true
                files(project.file("consumer-rules.pro"))
            }
        }
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.datastore.preferences.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.okio)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.datastore.preferences)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}