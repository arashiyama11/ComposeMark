plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
}


// 監視したいMarkdownの場所を合わせてね
val markdownDirs = listOf(
    layout.projectDirectory.dir("mdcx"),
)


tasks.matching { it.name.startsWith("ksp") }.configureEach {
    // 例: kspCommonMainKotlinMetadata, kspKotlinJvm, kspWasmJs など全てに効く
    markdownDirs.forEach { d ->
        inputs.dir(d)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .withPropertyName("kspExtraInputs_${d.asFile.name}")
    }
}
