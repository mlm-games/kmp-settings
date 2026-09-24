@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

        androidResources {
            enable = true
        }

        optimization {
            consumerKeepRules.apply {
                publish = true
                files(project.file("consumer-rules.pro"))
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvmToolchain(17)
    iosArm64()
    iosSimulatorArm64()
    linuxX64()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.datastore.preferences.core)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}