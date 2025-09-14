package io.github.arashiyama11.composemark.processor.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

open class ComposeMarkExtension internal constructor(project: Project) {
    /** Base directory for resolving Markdown and for composemark.root.path (defaults to projectDir). */
    var rootPath: String = project.projectDir.absolutePath

    /** Glob patterns relative to [rootPath] to watch for triggering KSP. */
    val watch: MutableList<String> = mutableListOf()

    /** Convenience to append patterns. */
    fun watch(vararg patterns: String) {
        watch += patterns
    }

    fun Project.ensureCommonKspBeforeKotlinCompile() {
        plugins.withId("com.google.devtools.ksp") {
            tasks.withType(KotlinCompilationTask::class.java)
                .configureEach {
                    it.dependsOn("kspCommonMainKotlinMetadata")
                }
        }
    }
}

class ComposeMarkGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext =
            project.extensions.create("composeMark", ComposeMarkExtension::class.java, project)

        project.extensions.extraProperties.set(
            "ksp.composemark.root.path",
            project.projectDir.absolutePath
        )

        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            project.extensions.findByName("ksp")?.let { kspExt ->
                try {
                    val arg =
                        kspExt::class.java.methods.firstOrNull { it.name == "arg" && it.parameterCount == 2 }
                    arg?.invoke(kspExt, "composemark.root.path", project.projectDir.absolutePath)
                } catch (_: Throwable) { /* ignore */
                }
            }

            project.afterEvaluate {
                val root = ext.rootPath

                project.extensions.extraProperties.set("ksp.composemark.root.path", root)
                project.extensions.findByName("ksp")?.let { kspExt ->
                    try {
                        val arg =
                            kspExt::class.java.methods.firstOrNull { it.name == "arg" && it.parameterCount == 2 }
                        arg?.invoke(kspExt, "composemark.root.path", root)
                    } catch (e: Throwable) {
                        project.logger.warn(
                            "Failed to set 'composemark.root.path' argument for KSP extension",
                            e
                        )
                    }
                }

                val files: ConfigurableFileCollection = project.files(
                    ext.watch.map { pattern -> project.fileTree(root) { it.include(pattern) } }
                )

                project.tasks
                    .matching { it.name.startsWith("ksp") }
                    .configureEach { t ->
                        t.inputs.files(files)
                            .withPropertyName("composemarkMd")
                            .withPathSensitivity(PathSensitivity.RELATIVE)
                    }
            }
        }
    }
}
