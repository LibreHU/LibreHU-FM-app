pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LibreHU-FM"
include(":app")

// DAB+ engine from DABplay+ (MIT, https://github.com/jvspier/dabplayplus): see dab/README.md.
for (m in listOf("dab-core", "tuner-api", "driver-dtr100", "driver-si4684", "driver-siano", "logos", "data", "engine", "usb-android", "audio")) {
    include(":$m")
    project(":$m").projectDir = file("dab/$m")
}
