import com.android.build.api.dsl.androidLibrary

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

kotlin {
    androidLibrary {
        namespace = "io.github.mlmgames.settings.core"
        compileSdk = 36
        minSdk = 24
        withJava()
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.datastore)
                implementation(libs.datastore.preferences)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okio)
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