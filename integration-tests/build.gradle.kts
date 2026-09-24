plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinComposePlugin)
    alias(libs.plugins.ksp)
}

dependencies {
    val settingsVersion = providers.gradleProperty("VERSION_NAME").orElse("0.9.2-SNAPSHOT").get()
    implementation("io.github.mlm-games:kmp-settings-core:$settingsVersion")
    implementation("io.github.mlm-games:kmp-settings-ui-compose:$settingsVersion")
    implementation(libs.kotlinx.serialization.json)
    ksp("io.github.mlm-games:kmp-settings-ksp:$settingsVersion")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin {
    jvmToolchain(17)
}
