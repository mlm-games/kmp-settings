pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-settings"

include(
  ":settings-core",
  ":settings-ui-compose",
  ":settings-ksp",
)
