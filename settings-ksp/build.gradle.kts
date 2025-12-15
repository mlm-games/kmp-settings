plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
  implementation("com.google.devtools.ksp:symbol-processing-api:2.3.0")
  implementation("com.squareup:kotlinpoet:2.2.0")
  implementation("com.squareup:kotlinpoet-ksp:2.2.0")
}

kotlin {
  jvmToolchain(17)
}