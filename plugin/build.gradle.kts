@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = rootProject.group
version = rootProject.version
val isPublicRelease = providers.environmentVariable("PUBLIC_RELEASE").getOrElse("false").toBoolean()


kotlin {

    explicitApi()
    jvm()
    jvmToolchain(11)

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    wasmJs {
        browser {
            binaries.executable()
        }
    }

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "io.github.arashiyama11.composemark.plugin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "pluginKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                api(compose.foundation)
                api(compose.runtime)
                api(compose.ui)
                api(compose.material3)
                api(project(":core"))
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }

}


mavenPublishing {
    configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = true)

    publishToMavenCentral()

    if (isPublicRelease) {
        signAllPublications()
    }

    coordinates(
        group.toString(),
        "composemark-plugin",
        project.version.toString()
    )

    pom {
        name.set("ComposeMark Plugin")
        description.set("ComposeMark: generate Compose UI from Markdown")
        url.set("https://github.com/arashiyama11/ComposeMark")
        inceptionYear.set("2025")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("arashiyama11")
                name.set("arashiyama")
                url.set("https://github.com/arashiyama11")
            }
        }
        scm {
            url.set("https://github.com/arashiyama11/ComposeMark")
            connection.set("scm:git:git://github.com/arashiyama11/ComposeMark")
            developerConnection.set("scm:git:ssh://git@github.com/arashiyama11/ComposeMark")
        }
    }
}

if (isPublicRelease) {
    tasks.withType<PublishToMavenRepository>().configureEach {
        dependsOn(tasks.withType(Sign::class.java))
    }
}