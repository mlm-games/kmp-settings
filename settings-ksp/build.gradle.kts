plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
  implementation(libs.symbol.processing.api)
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
}

kotlin {
  jvmToolchain(17)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}