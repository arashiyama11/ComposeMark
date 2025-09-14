pluginManagement {
    repositories {
        mavenLocal()
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

rootProject.name = "compose-mark"


include(":core")
include(":processor")
include(":plugin")
if (providers.environmentVariable("INCLUDE_SAMPLE").getOrElse("true").toBoolean()) {
    include(":sample")
}

