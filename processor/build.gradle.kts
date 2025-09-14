import com.vanniktech.maven.publish.SonatypeHost

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
    compileOnly(libs.kotlin.gradle.plugin)

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

if (providers.environmentVariable("PUBLIC_RELEASE").getOrElse("false").toBoolean()) {

    mavenPublishing {
        configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = true)

        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

        signAllPublications()

        coordinates(
            group.toString(),
            "composemark-gradle-plugin",
            project.version.toString()
        )

        pom {
            name.set("ComposeMark Gradle Plugin")
            description.set("ComposeMark: generate Compose UI from Markdown & wire KSP inputs")
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
}

publishing {
    publications {
        // Standard library JAR
        create<MavenPublication>("processor") {
            from(components["java"])
            artifactId = "composemark-processor"
        }
    }
    repositories {
        mavenLocal()
    }
}

afterEvaluate {
    val allSignTasks = tasks.withType(Sign::class.java)
    tasks.withType(PublishToMavenRepository::class.java).configureEach {
        dependsOn(allSignTasks)
    }
}