import org.jetbrains.kotlin.gradle.idea.tcs.extras.projectArtifactsClasspathKey

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
        create("processorPlugin") {
            id = "io.github.arashiyama11.composemark.processor"
            implementationClass = "com.github.arashiyama11.processor.MarkdownComposeProcessor"
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
    }
}

dependencies{
    implementation(libs.symbol.processing.api)
    implementation(project(":core"))
    implementation(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)


    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.compile.testing.ksp)
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        // 通常のライブラリ jar
        create<MavenPublication>("processor") {
            from(components["java"])
            artifactId = "composemark-processor"
        }
        // プラグイン・マーカー jar は java-gradle-plugin が自動で MarkersPublication を用意します
    }
    repositories {
        mavenLocal()
    }
}