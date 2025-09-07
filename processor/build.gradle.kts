plugins {
    id("java-library")
    id("java-gradle-plugin")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.ksp)
}

group = rootProject.group
version = rootProject.version


gradlePlugin {
    plugins {
        create("composemark") {
            id = "io.github.arashiyama11.composemark"
            implementationClass =
                "io.github.arashiyama11.composemark.processor.gradle.ComposeMarkGradlePlugin"
            displayName = "ComposeMark Plugin"
            description = "Wires Markdown changes into KSP task inputs and sets defaults"
        }
    }
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(gradleApi())
    implementation(libs.symbol.processing.api)
    implementation(project(":core")) {
        isTransitive = false
    }


    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)


    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.compile.testing.ksp)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
}

publishing {
    publications {
        // Standard library JAR
        create<MavenPublication>("processor") {
            from(components["java"])
            artifactId = "composemark-processor"
        }
        // Plugin marker JAR is provided automatically by java-gradle-plugin (MarkersPublication)
    }
    repositories {
        mavenLocal()
    }
}
