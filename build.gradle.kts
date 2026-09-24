plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.composeGradle) apply false
    alias(libs.plugins.kotlinComposePlugin) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.dokka)
}

dependencies {
    dokka(project(":settings-core"))
    dokka(project(":settings-ui-compose"))
}

val integrationTest = tasks.register<Exec>("integrationTest") {
    dependsOn(
        ":settings-core:publishToMavenLocal",
        ":settings-ui-compose:publishToMavenLocal",
        ":settings-ksp:publishToMavenLocal",
    )
    workingDir(layout.projectDirectory.dir("integration-tests"))
    commandLine(
        rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
        "-PVERSION_NAME=${project.property("VERSION_NAME")}",
        "build",
        "--no-daemon",
    )
}

tasks.named("build") {
    dependsOn(integrationTest)
}
